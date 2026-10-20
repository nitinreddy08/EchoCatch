// SPDX-License-Identifier: GPL-3.0-only
package io.github.nitinreddy08.echocatch.lab

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.media.AudioRecord
import android.media.AudioRecordingConfiguration
import android.media.MediaRecorder
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * Spikes S2, S5 and S6: a microphone foreground service that logs how it was started, whether it can open and close
 * the microphone later from the background, whether capture gets silenced, which device and playback events arrive,
 * and what the battery does (docs/plan/04-spikes.md). Everything is written to [EventLog].
 */
class LabService : Service() {
    @Volatile private var running = false
    @Volatile private var cycleNow = false
    private var worker: Thread? = null
    private var mode = MODE_IDLE
    private var hopMs = 100
    private var cycleSeconds = 60
    private var startedAt = 0L
    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var audioManager: AudioManager

    private val recordingCallback = object : AudioManager.AudioRecordingCallback() {
        override fun onRecordingConfigChanged(configs: MutableList<AudioRecordingConfiguration>) {
            val items = JSONArray()
            for (config in configs) {
                val item = JSONObject().put("clientAudioSource", config.clientAudioSource)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) item.put("clientSilenced", config.isClientSilenced)
                items.put(item)
            }
            EventLog.append(this@LabService, "recording_config_changed", JSONObject().put("count", configs.size).put("configs", items))
        }
    }

    private val playbackCallback = object : AudioManager.AudioPlaybackCallback() {
        override fun onPlaybackConfigChanged(configs: MutableList<AudioPlaybackConfiguration>) {
            val items = JSONArray()
            for (config in configs) {
                val attributes = config.audioAttributes
                items.put(JSONObject().put("usage", attributes.usage).put("contentType", attributes.contentType))
            }
            EventLog.append(
                this@LabService,
                "playback_config_changed",
                JSONObject().put("count", configs.size).put("players", items).put("musicActive", audioManager.isMusicActive),
            )
        }
    }

    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) = logDevices("device_added", addedDevices)
        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) = logDevices("device_removed", removedDevices)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(AudioManager::class.java)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopLab("stop_action")
            ACTION_CYCLE_NOW -> cycleNow = true
            else -> startLab(intent)
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        if (running) stopLab("destroyed")
        super.onDestroy()
    }

    private fun startLab(intent: Intent?) {
        if (running) return
        mode = intent?.getStringExtra(EXTRA_MODE) ?: MODE_IDLE
        hopMs = intent?.getIntExtra(EXTRA_HOP_MS, 100) ?: 100
        cycleSeconds = intent?.getIntExtra(EXTRA_CYCLE_SECONDS, 60) ?: 60
        createChannel()
        val notification = buildRunningNotification()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            EventLog.append(this, "fgs_start_failed", JSONObject().put("exception", e.javaClass.simpleName).put("message", e.message ?: ""))
            stopSelf()
            return
        }
        running = true
        isRunning = true
        startedAt = SystemClock.elapsedRealtime()
        EventLog.append(
            this,
            "fgs_started",
            JSONObject().put("mode", mode).put("hopMs", hopMs).put("cycleSeconds", cycleSeconds).put("power", powerState()),
        )
        registerCallbacks()
        worker = Thread({ loop() }, "lab-loop").also { it.start() }
    }

    private fun stopLab(reason: String) {
        if (!running) {
            stopSelf()
            return
        }
        running = false
        isRunning = false
        worker?.interrupt()
        unregisterCallbacks()
        EventLog.append(this, "fgs_stopped", JSONObject().put("reason", reason).put("runtimeS", (SystemClock.elapsedRealtime() - startedAt) / 1000))
        stopForeground(STOP_FOREGROUND_REMOVE)
        postRestartNotification()
        stopSelf()
    }

    private fun registerCallbacks() {
        audioManager.registerAudioRecordingCallback(recordingCallback, mainHandler)
        audioManager.registerAudioPlaybackCallback(playbackCallback, mainHandler)
        audioManager.registerAudioDeviceCallback(deviceCallback, mainHandler)
    }

    private fun unregisterCallbacks() {
        audioManager.unregisterAudioRecordingCallback(recordingCallback)
        audioManager.unregisterAudioPlaybackCallback(playbackCallback)
        audioManager.unregisterAudioDeviceCallback(deviceCallback)
    }

    private fun logDevices(type: String, devices: Array<out AudioDeviceInfo>) {
        val items = JSONArray()
        for (device in devices) items.put(DeviceInfo.describe(device))
        EventLog.append(this, type, JSONObject().put("devices", items))
    }

    private fun loop() {
        try {
            var nextBattery = SystemClock.elapsedRealtime()
            when (mode) {
                MODE_MIC_OPEN -> micOpenLoop { nextBattery = sampleBatteryIfDue(nextBattery) }
                MODE_CYCLE -> cycleLoop { nextBattery = sampleBatteryIfDue(nextBattery) }
                else -> idleLoop { nextBattery = sampleBatteryIfDue(nextBattery) }
            }
        } catch (e: InterruptedException) {
            // normal stop
        }
    }

    private fun sampleBatteryIfDue(nextDue: Long): Long {
        val now = SystemClock.elapsedRealtime()
        if (now < nextDue) return nextDue
        EventLog.append(this, "battery", batteryState().put("power", powerState()))
        return now + BATTERY_INTERVAL_MS
    }

    private inline fun idleLoop(tick: () -> Unit) {
        while (running) {
            tick()
            Thread.sleep(1000)
        }
    }

    @SuppressLint("MissingPermission")
    private inline fun micOpenLoop(tick: () -> Unit) {
        val record = openRecord() ?: return
        try {
            record.startRecording()
            EventLog.append(this, "mic_open_started", JSONObject().put("routed", routedName(record)))
            val buffer = ShortArray(SAMPLE_RATE * hopMs / 1000)
            var lastSilenced = false
            var nextSilenceCheck = 0L
            while (running) {
                val read = record.read(buffer, 0, buffer.size)
                if (read < 0) {
                    EventLog.append(this, "mic_read_error", JSONObject().put("code", read))
                    return
                }
                val now = SystemClock.elapsedRealtime()
                if (now >= nextSilenceCheck) {
                    nextSilenceCheck = now + 5000
                    val silenced = isSilenced(record)
                    if (silenced != lastSilenced) {
                        lastSilenced = silenced
                        EventLog.append(this, "capture_silenced_changed", JSONObject().put("silenced", silenced))
                    }
                }
                tick()
            }
        } finally {
            record.release()
            EventLog.append(this, "mic_open_ended")
        }
    }

    private inline fun cycleLoop(tick: () -> Unit) {
        var nextCycle = SystemClock.elapsedRealtime()
        while (running) {
            tick()
            val now = SystemClock.elapsedRealtime()
            if (cycleNow || now >= nextCycle) {
                cycleNow = false
                runOneCycle()
                nextCycle = SystemClock.elapsedRealtime() + cycleSeconds * 1000L
            }
            Thread.sleep(500)
        }
    }

    @SuppressLint("MissingPermission")
    private fun runOneCycle() {
        val event = JSONObject().put("sinceStartS", (SystemClock.elapsedRealtime() - startedAt) / 1000).put("power", powerState())
        val record = openRecord()
        if (record == null) {
            EventLog.append(this, "mic_cycle", event.put("ok", false))
            return
        }
        try {
            val openStart = SystemClock.elapsedRealtime()
            record.startRecording()
            val buffer = ShortArray(SAMPLE_RATE / 2)
            val samples = ShortArray(SAMPLE_RATE * 2)
            var filled = 0
            while (filled < samples.size) {
                val read = record.read(buffer, 0, minOf(buffer.size, samples.size - filled))
                if (read <= 0) break
                System.arraycopy(buffer, 0, samples, filled, read)
                filled += read
            }
            var sumSquares = 0.0
            for (i in 0 until filled) sumSquares += samples[i].toDouble() * samples[i]
            val rms = if (filled > 0) sqrt(sumSquares / filled) else 0.0
            event.put("ok", filled > 0)
                .put("openToDataMs", SystemClock.elapsedRealtime() - openStart)
                .put("rmsDbfs", if (rms <= 0.0) -120.0 else 20.0 * log10(rms / 32768.0))
                .put("silenced", isSilenced(record))
                .put("routed", routedName(record))
            record.stop()
        } catch (e: IllegalStateException) {
            event.put("ok", false).put("exception", e.javaClass.simpleName).put("message", e.message ?: "")
        } finally {
            record.release()
        }
        EventLog.append(this, "mic_cycle", event)
    }

    @SuppressLint("MissingPermission")
    private fun openRecord(): AudioRecord? {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            EventLog.append(this, "mic_open_failed", JSONObject().put("reason", "RECORD_AUDIO not granted"))
            return null
        }
        return try {
            val minBuffer = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            val format = AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(SAMPLE_RATE)
                .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                .build()
            val record = AudioRecord.Builder()
                .setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
                .setAudioFormat(format)
                .setBufferSizeInBytes(maxOf(minBuffer * 2, SAMPLE_RATE))
                .build()
            if (record.state == AudioRecord.STATE_INITIALIZED) {
                record
            } else {
                record.release()
                EventLog.append(this, "mic_open_failed", JSONObject().put("reason", "not initialised"))
                null
            }
        } catch (e: Exception) {
            EventLog.append(this, "mic_open_failed", JSONObject().put("exception", e.javaClass.simpleName).put("message", e.message ?: ""))
            null
        }
    }

    private fun isSilenced(record: AudioRecord): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && record.activeRecordingConfiguration?.isClientSilenced == true

    private fun routedName(record: AudioRecord): String =
        record.routedDevice?.let { DeviceInfo.typeName(it.type) } ?: "unknown"

    private fun powerState(): JSONObject {
        val pm = getSystemService(PowerManager::class.java)
        return JSONObject()
            .put("screenOn", pm.isInteractive)
            .put("deviceIdle", pm.isDeviceIdleMode)
            .put("powerSave", pm.isPowerSaveMode)
            .put("ignoringBatteryOptimizations", pm.isIgnoringBatteryOptimizations(packageName))
    }

    private fun batteryState(): JSONObject {
        val manager = getSystemService(BatteryManager::class.java)
        val sticky = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        return JSONObject()
            .put("levelPercent", manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY))
            .put("chargeCounterUah", manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER))
            .put("currentNowUa", manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW))
            .put("temperatureTenthsC", sticky?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) ?: -1)
            .put("pluggedCode", sticky?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) ?: -1)
    }

    private fun createChannel() {
        val channel = NotificationChannel(CHANNEL_ID, "EchoCatch Lab", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildRunningNotification(): Notification {
        val stop = PendingIntent.getService(
            this,
            1,
            Intent(this, LabService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val cycle = PendingIntent.getService(
            this,
            2,
            Intent(this, LabService::class.java).setAction(ACTION_CYCLE_NOW),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_lab)
            .setContentTitle("EchoCatch Lab running")
            .setContentText("Mode: $mode")
            .setOngoing(true)
            .addAction(Notification.Action.Builder(Icon.createWithResource(this, R.drawable.ic_lab), "Stop", stop).build())
            .addAction(Notification.Action.Builder(Icon.createWithResource(this, R.drawable.ic_lab), "Mic cycle now", cycle).build())
            .build()
    }

    /** A plain notification (not a foreground service) whose action starts the service: tests the notification start path. */
    @SuppressLint("NotificationPermission")
    private fun postRestartNotification() {
        val start = PendingIntent.getForegroundService(
            this,
            3,
            Intent(this, LabService::class.java).setAction(ACTION_START).putExtra(EXTRA_MODE, MODE_CYCLE),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_lab)
            .setContentTitle("EchoCatch Lab stopped")
            .setContentText("Tap Start to test starting the service from a notification")
            .addAction(Notification.Action.Builder(Icon.createWithResource(this, R.drawable.ic_lab), "Start", start).build())
            .setAutoCancel(true)
            .build()
        getSystemService(NotificationManager::class.java).notify(RESTART_NOTIFICATION_ID, notification)
    }

    companion object {
        const val ACTION_START = "io.github.nitinreddy08.echocatch.lab.START"
        const val ACTION_STOP = "io.github.nitinreddy08.echocatch.lab.STOP"
        const val ACTION_CYCLE_NOW = "io.github.nitinreddy08.echocatch.lab.CYCLE_NOW"
        const val EXTRA_MODE = "mode"
        const val EXTRA_HOP_MS = "hopMs"
        const val EXTRA_CYCLE_SECONDS = "cycleSeconds"
        const val MODE_IDLE = "idle"
        const val MODE_MIC_OPEN = "mic_open"
        const val MODE_CYCLE = "cycle"

        private const val CHANNEL_ID = "lab"
        private const val NOTIFICATION_ID = 1
        private const val RESTART_NOTIFICATION_ID = 2
        private const val SAMPLE_RATE = 16000
        private const val BATTERY_INTERVAL_MS = 60_000L

        @Volatile
        var isRunning = false

        fun startIntent(context: Context, mode: String, hopMs: Int = 100, cycleSeconds: Int = 60): Intent =
            Intent(context, LabService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_MODE, mode)
                .putExtra(EXTRA_HOP_MS, hopMs)
                .putExtra(EXTRA_CYCLE_SECONDS, cycleSeconds)
    }
}
