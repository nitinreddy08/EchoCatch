// SPDX-License-Identifier: GPL-3.0-only
package io.github.nitinreddy08.echocatch.lab

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.PI
import kotlin.math.sin

/**
 * Spike S3: asks for audio focus with a chosen type and attributes, plays a short synthesized chime, and records what
 * the system reports. What the music actually did (ducked, paused, ignored) is noted by the tester afterwards.
 */
class DuckingTest(private val context: Context) {
    class Request(
        val gain: Int,
        val gainName: String,
        val usage: Int,
        val usageName: String,
        val contentType: Int,
        val contentTypeName: String,
        val player: String,
    )

    /** Blocking. Call from a background thread. */
    fun run(request: Request): JSONObject {
        val audioManager = context.getSystemService(AudioManager::class.java)
        val attributes = AudioAttributes.Builder().setUsage(request.usage).setContentType(request.contentType).build()
        val focusEvents = JSONArray()
        val t0 = SystemClock.elapsedRealtime()
        val listener = AudioManager.OnAudioFocusChangeListener { change ->
            focusEvents.put(JSONObject().put("change", focusChangeName(change)).put("atMs", SystemClock.elapsedRealtime() - t0))
        }
        val focusRequest = AudioFocusRequest.Builder(request.gain)
            .setAudioAttributes(attributes)
            .setAcceptsDelayedFocusGain(false)
            .setWillPauseWhenDucked(false)
            .setOnAudioFocusChangeListener(listener, Handler(Looper.getMainLooper()))
            .build()

        val result = JSONObject()
            .put("startedAtMillis", System.currentTimeMillis())
            .put("player", request.player)
            .put("gain", request.gainName)
            .put("usage", request.usageName)
            .put("contentType", request.contentTypeName)
            .put("before", snapshot(audioManager))

        val focusStart = SystemClock.elapsedRealtime()
        val code = audioManager.requestAudioFocus(focusRequest)
        result.put("focusResult", focusResultName(code)).put("focusRequestMs", SystemClock.elapsedRealtime() - focusStart)

        // The cue is played even if focus was refused: the real app will have a safety bias towards alerting.
        val pcm = chime()
        val track = AudioTrack.Builder()
            .setAudioAttributes(attributes)
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setBufferSizeInBytes(pcm.size * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
        try {
            track.write(pcm, 0, pcm.size)
            val playStart = SystemClock.elapsedRealtime()
            track.play()
            var startLatencyMs = -1L
            while (SystemClock.elapsedRealtime() - playStart < CUE_MS) {
                if (startLatencyMs < 0 && track.playbackHeadPosition > 0) startLatencyMs = SystemClock.elapsedRealtime() - playStart
                if (SystemClock.elapsedRealtime() - playStart > CUE_MS / 2 && !result.has("during")) {
                    result.put("during", snapshot(audioManager))
                }
                SystemClock.sleep(20)
            }
            result.put("outputStartLatencyMs", startLatencyMs)
            track.stop()
        } finally {
            track.release()
        }

        audioManager.abandonAudioFocusRequest(focusRequest)
        SystemClock.sleep(RESUME_WAIT_MS)
        result.put("afterResumeWait", snapshot(audioManager)).put("focusEvents", focusEvents)
        return result
    }

    private fun snapshot(audioManager: AudioManager): JSONObject {
        val players = JSONArray()
        for (config in audioManager.activePlaybackConfigurations) {
            val attributes = config.audioAttributes
            players.put(JSONObject().put("usage", attributes.usage).put("contentType", attributes.contentType))
        }
        return JSONObject()
            .put("musicActive", audioManager.isMusicActive)
            .put("musicVolumeIndex", audioManager.getStreamVolume(AudioManager.STREAM_MUSIC))
            .put("players", players)
    }

    /** Three short tones with fades, as a stand-in for the real alert cue. */
    private fun chime(): ShortArray {
        val tones = intArrayOf(880, 1320, 880)
        val perToneSamples = SAMPLE_RATE * CUE_MS / 1000 / tones.size
        val out = ShortArray(perToneSamples * tones.size)
        for ((index, frequency) in tones.withIndex()) {
            for (i in 0 until perToneSamples) {
                val fade = minOf(1.0, minOf(i, perToneSamples - i) / (SAMPLE_RATE * 0.02))
                out[index * perToneSamples + i] = (sin(2.0 * PI * frequency * i / SAMPLE_RATE) * fade * 0.6 * Short.MAX_VALUE).toInt().toShort()
            }
        }
        return out
    }

    companion object {
        private const val SAMPLE_RATE = 44100
        private const val CUE_MS = 1200
        private const val RESUME_WAIT_MS = 2500L

        fun focusResultName(code: Int): String = when (code) {
            AudioManager.AUDIOFOCUS_REQUEST_GRANTED -> "GRANTED"
            AudioManager.AUDIOFOCUS_REQUEST_FAILED -> "FAILED"
            AudioManager.AUDIOFOCUS_REQUEST_DELAYED -> "DELAYED"
            else -> "CODE_$code"
        }

        fun focusChangeName(code: Int): String = when (code) {
            AudioManager.AUDIOFOCUS_GAIN -> "GAIN"
            AudioManager.AUDIOFOCUS_LOSS -> "LOSS"
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> "LOSS_TRANSIENT"
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> "LOSS_TRANSIENT_CAN_DUCK"
            else -> "CODE_$code"
        }
    }
}
