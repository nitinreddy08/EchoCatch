// SPDX-License-Identifier: GPL-3.0-only
package io.github.nitinreddy08.echocatch.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import io.github.nitinreddy08.echocatch.AppGraph
import io.github.nitinreddy08.echocatch.EchoCatchApp
import io.github.nitinreddy08.echocatch.MainActivity
import io.github.nitinreddy08.echocatch.R
import io.github.nitinreddy08.echocatch.audio.AudioSource
import io.github.nitinreddy08.echocatch.audio.OpenFailure
import io.github.nitinreddy08.echocatch.audio.OpenResult
import io.github.nitinreddy08.echocatch.audio.PcmSink
import io.github.nitinreddy08.echocatch.audio.PhoneMicSource
import io.github.nitinreddy08.echocatch.audio.SourceError
import io.github.nitinreddy08.echocatch.model.LevelMeter
import io.github.nitinreddy08.echocatch.policy.StopReason
import io.github.nitinreddy08.echocatch.status.LevelReading
import io.github.nitinreddy08.echocatch.status.messageRes
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The microphone foreground service (ADR-0004, M1-T3).
 *
 * It is only ever started from a visible screen or a notification action, shows a persistent notification with a
 * Stop button for as long as the microphone is open, and releases everything when stopped. It is not sticky: if
 * Android ends it, the app says so on the next launch instead of silently restarting a microphone.
 *
 * In this milestone the microphone stays open for the whole time the service runs and the audio only feeds the
 * level meter. Opening it only when it is useful (headset connected, media playing) arrives with the arming policy
 * in milestone M3.
 *
 * Threads: `onStartCommand` runs on the main thread; everything else runs on one control thread so that start,
 * stop and error handling can never interleave. The capture thread belongs to the audio source.
 */
class ListeningService : Service() {
    private val graph: AppGraph get() = (application as EchoCatchApp).graph

    private lateinit var controlThread: HandlerThread
    private lateinit var control: Handler
    private val started = AtomicBoolean(false)
    private val meter = LevelMeter()

    // Control thread only from here on.
    private var source: AudioSource? = null
    private var sampleRateHz = 0
    private var generation = 0

    private val levelTick = object : Runnable {
        override fun run() {
            graph.status.publishLevel(LevelReading(meter.peakDb, meter.rmsDb, meter.silentMs))
            val silent = meter.silentMs >= SILENT_AFTER_MS
            graph.status.update { it.withMicSilent(silent) }
            control.postDelayed(this, LEVEL_INTERVAL_MS)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        controlThread = HandlerThread("echocatch-control").also { it.start() }
        control = Handler(controlThread.looper)
        createChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            control.post { shutdown(StopReason.USER) }
        } else {
            begin()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        control.removeCallbacks(levelTick)
        control.post { releaseAfterDestroy() }
        controlThread.quitSafely()
        super.onDestroy()
    }

    /** Main thread. Must promote the service to the foreground at once; the microphone is opened afterwards. */
    private fun begin() {
        if (!started.compareAndSet(false, true)) return
        getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_STOPPED_ID)
        try {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_LISTENING_ID,
                buildListeningNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
            )
        } catch (e: SecurityException) {
            failBeforeStart(StopReason.PERMISSION_REVOKED)
            return
        } catch (e: RuntimeException) {
            failBeforeStart(StopReason.START_REFUSED)
            return
        }
        val session = ++generationCounter
        control.post { open(session) }
    }

    private fun failBeforeStart(reason: StopReason) {
        started.set(false)
        graph.status.update { it.stopped(reason) }
        postStoppedNotification(reason)
        stopSelf()
    }

    private fun open(session: Int) {
        generation = session
        val mic = PhoneMicSource(applicationContext)
        when (val result = mic.open()) {
            is OpenResult.Opened -> {
                source = mic
                sampleRateHz = result.sampleRateHz
                meter.reset()
                mic.startCapture(sinkFor(session))
                graph.status.update { it.started() }
                graph.launch { graph.settings.setListeningWanted(true) }
                control.postDelayed(levelTick, LEVEL_INTERVAL_MS)
            }
            is OpenResult.Failed -> shutdown(
                if (result.reason == OpenFailure.PERMISSION_MISSING) StopReason.PERMISSION_REVOKED else StopReason.MICROPHONE_UNAVAILABLE,
            )
        }
    }

    /** Runs on the audio thread: no allocation, no locks. Errors are handed to the control thread. */
    private fun sinkFor(session: Int) = object : PcmSink {
        override fun onFrame(samples: ShortArray, count: Int) {
            meter.update(samples, count, sampleRateHz)
        }

        override fun onSourceError(error: SourceError) {
            val reason = if (error == SourceError.START_FAILED) StopReason.MICROPHONE_UNAVAILABLE else StopReason.CAPTURE_ERROR
            control.post { if (session == generation) shutdown(reason) }
        }
    }

    private fun shutdown(reason: StopReason) {
        if (!started.get()) {
            stopSelf()
            return
        }
        control.removeCallbacks(levelTick)
        source?.close()
        source = null
        meter.reset()
        graph.status.publishLevel(LevelReading())
        graph.status.update { it.stopped(reason) }
        graph.launch { graph.settings.setListeningWanted(false) }
        if (reason != StopReason.USER) postStoppedNotification(reason)
        started.set(false)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun releaseAfterDestroy() {
        source?.close()
        source = null
        if (started.getAndSet(false)) {
            graph.status.update { it.stopped(StopReason.SYSTEM_STOPPED) }
            graph.launch { graph.settings.setListeningWanted(false) }
        }
    }

    private fun createChannels() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_LISTENING, getString(R.string.channel_listening), NotificationManager.IMPORTANCE_LOW).apply {
                description = getString(R.string.channel_listening_description)
                setShowBadge(false)
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_STOPPED, getString(R.string.channel_stopped), NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = getString(R.string.channel_stopped_description)
            },
        )
    }

    private fun buildListeningNotification(): Notification {
        val stop = PendingIntent.getService(this, 1, stopIntent(this), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL_LISTENING)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.notification_listening_title))
            .setContentText(getString(R.string.notification_listening_text))
            .setStyle(NotificationCompat.BigTextStyle().bigText(getString(R.string.notification_listening_text)))
            .setContentIntent(openAppIntent())
            .addAction(0, getString(R.string.notification_stop), stop)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun postStoppedNotification(reason: StopReason) {
        val text = getString(reason.messageRes())
        val notification = NotificationCompat.Builder(this, CHANNEL_STOPPED)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.notification_stopped_title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openAppIntent())
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .build()
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_STOPPED_ID, notification)
    }

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        this,
        0,
        Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    companion object {
        const val ACTION_START = "io.github.nitinreddy08.echocatch.action.START_LISTENING"
        const val ACTION_STOP = "io.github.nitinreddy08.echocatch.action.STOP_LISTENING"

        private const val CHANNEL_LISTENING = "listening"
        private const val CHANNEL_STOPPED = "stopped"
        private const val NOTIFICATION_LISTENING_ID = 1
        private const val NOTIFICATION_STOPPED_ID = 2
        private const val LEVEL_INTERVAL_MS = 100L
        private const val SILENT_AFTER_MS = 3_000L

        /** Counts starts so that a late error from an earlier session cannot stop a newer one. */
        private var generationCounter = 0

        fun startIntent(context: Context): Intent = Intent(context, ListeningService::class.java).setAction(ACTION_START)

        fun stopIntent(context: Context): Intent = Intent(context, ListeningService::class.java).setAction(ACTION_STOP)
    }
}
