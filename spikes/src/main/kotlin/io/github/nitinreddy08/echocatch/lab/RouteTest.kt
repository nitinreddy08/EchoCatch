// SPDX-License-Identifier: GPL-3.0-only
@file:Suppress("DEPRECATION")

package io.github.nitinreddy08.echocatch.lab

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.os.Build
import android.os.SystemClock
import org.json.JSONArray
import org.json.JSONObject

/**
 * Spike S1: opens one microphone route and records what happened (docs/plan/04-spikes.md).
 *
 * Questions it helps answer: which device really carries the audio, at what sample rate and bandwidth, how long the
 * route takes to open and to restore, and whether music keeps playing while the mic is open.
 */
class RouteTest(private val context: Context) {
    enum class Method { PREFERRED_DEVICE, COMMUNICATION_DEVICE }

    class Request(
        val device: AudioDeviceInfo,
        val method: Method,
        val source: Int,
        val sourceName: String,
        val seconds: Int,
        val requestedSampleRate: Int,
    )

    /** Blocking. Call from a background thread. */
    @SuppressLint("MissingPermission")
    fun run(request: Request, log: (String) -> Unit): JSONObject {
        val audioManager = context.getSystemService(AudioManager::class.java)
        val result = JSONObject()
            .put("startedAtMillis", System.currentTimeMillis())
            .put("deviceType", DeviceInfo.typeName(request.device.type))
            .put("deviceProduct", request.device.productName.toString())
            .put("method", request.method.name)
            .put("preset", request.sourceName)
            .put("seconds", request.seconds)
            .put("requestedSampleRate", request.requestedSampleRate)
            .put("before", snapshot(audioManager))

        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            return result.put("error", "RECORD_AUDIO is not granted")
        }

        val setupStart = SystemClock.elapsedRealtime()
        result.put("routeSetup", setUpRoute(audioManager, request))
        result.put("routeSetupMs", SystemClock.elapsedRealtime() - setupStart)

        var record: AudioRecord? = null
        try {
            val minBuffer = AudioRecord.getMinBufferSize(
                request.requestedSampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
            )
            if (minBuffer <= 0) {
                return result.put("error", "unsupported sample rate or format (min buffer $minBuffer)")
            }
            val format = AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(request.requestedSampleRate)
                .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                .build()
            record = AudioRecord.Builder()
                .setAudioSource(request.source)
                .setAudioFormat(format)
                .setBufferSizeInBytes(maxOf(minBuffer * 2, request.requestedSampleRate))
                .build()
            if (record.state != AudioRecord.STATE_INITIALIZED) {
                return result.put("error", "AudioRecord failed to initialise")
            }
            if (request.method == Method.PREFERRED_DEVICE) {
                result.put("setPreferredDeviceAccepted", record.setPreferredDevice(request.device))
            }

            val total = request.requestedSampleRate * request.seconds
            val samples = ShortArray(total)
            val chunk = ShortArray(request.requestedSampleRate / 10)
            var filled = 0
            var firstReadMs = -1L
            var midSnapshot: JSONObject? = null
            var silencedSeen = false
            val captureStart = SystemClock.elapsedRealtime()
            record.startRecording()
            result.put("recordingState", record.recordingState)

            while (filled < total) {
                val read = record.read(chunk, 0, minOf(chunk.size, total - filled))
                if (read < 0) {
                    result.put("readError", read)
                    break
                }
                if (read > 0 && firstReadMs < 0) firstReadMs = SystemClock.elapsedRealtime() - captureStart
                System.arraycopy(chunk, 0, samples, filled, read)
                filled += read
                if (midSnapshot == null && SystemClock.elapsedRealtime() - captureStart > request.seconds * 500L) {
                    midSnapshot = snapshot(audioManager)
                    silencedSeen = isSilenced(record)
                }
            }

            val routed = record.routedDevice
            result.put("routedDeviceType", routed?.let { DeviceInfo.typeName(it.type) } ?: "unknown")
            result.put("routedDeviceProduct", routed?.productName?.toString() ?: "unknown")
            result.put("actualSampleRate", record.sampleRate)
            result.put("firstReadMs", firstReadMs)
            result.put("clientSilencedAtMidpoint", silencedSeen)
            result.put("during", midSnapshot ?: snapshot(audioManager))
            result.put("signal", SignalStats.summarize(samples, filled, request.requestedSampleRate))
            record.stop()
        } catch (e: IllegalArgumentException) {
            result.put("error", "IllegalArgumentException: ${e.message}")
        } catch (e: IllegalStateException) {
            result.put("error", "IllegalStateException: ${e.message}")
        } catch (e: SecurityException) {
            result.put("error", "SecurityException: ${e.message}")
        } finally {
            record?.release()
            val restoreStart = SystemClock.elapsedRealtime()
            result.put("routeTeardown", tearDownRoute(audioManager, request))
            result.put("routeTeardownMs", SystemClock.elapsedRealtime() - restoreStart)
        }
        result.put("after", snapshot(audioManager))
        log("S1 ${result.optString("deviceType")} via ${request.method}: routed=${result.optString("routedDeviceType")}, " +
            "rate=${result.optInt("actualSampleRate")}, music before/during/after=" +
            "${result.optJSONObject("before")?.optBoolean("musicActive")}/" +
            "${result.optJSONObject("during")?.optBoolean("musicActive")}/" +
            "${result.optJSONObject("after")?.optBoolean("musicActive")}")
        return result
    }

    private fun isSilenced(record: AudioRecord): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && record.activeRecordingConfiguration?.isClientSilenced == true

    private fun setUpRoute(audioManager: AudioManager, request: Request): JSONObject {
        val info = JSONObject()
        val isScoInput = request.device.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
        if (request.method == Method.COMMUNICATION_DEVICE) {
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            info.put("modeSetTo", "IN_COMMUNICATION")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val target = audioManager.availableCommunicationDevices.firstOrNull { it.type == request.device.type }
                info.put("communicationDeviceFound", target != null)
                if (target != null) {
                    info.put("setCommunicationDeviceAccepted", audioManager.setCommunicationDevice(target))
                    val waitStart = SystemClock.elapsedRealtime()
                    val active = waitFor(4000) { audioManager.communicationDevice?.type == target.type }
                    info.put("communicationDeviceActive", active)
                    info.put("communicationDeviceActiveAfterMs", SystemClock.elapsedRealtime() - waitStart)
                } else {
                    audioManager.clearCommunicationDevice()
                }
            } else if (isScoInput) {
                audioManager.startBluetoothSco()
                info.put("legacyScoConnected", waitFor { audioManager.isBluetoothScoOn })
            }
        } else if (isScoInput && Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            audioManager.startBluetoothSco()
            info.put("legacyScoConnected", waitFor { audioManager.isBluetoothScoOn })
        }
        return info
    }

    private fun tearDownRoute(audioManager: AudioManager, request: Request): JSONObject {
        val info = JSONObject()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            audioManager.clearCommunicationDevice()
        } else {
            audioManager.stopBluetoothSco()
            info.put("legacyScoStopped", waitFor { !audioManager.isBluetoothScoOn })
        }
        audioManager.mode = AudioManager.MODE_NORMAL
        info.put("method", request.method.name)
        return info
    }

    private fun waitFor(timeoutMs: Long = 3000, condition: () -> Boolean): Boolean {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (SystemClock.elapsedRealtime() < deadline) {
            if (condition()) return true
            SystemClock.sleep(100)
        }
        return condition()
    }

    private fun snapshot(audioManager: AudioManager): JSONObject {
        val configs = JSONArray()
        for (config in audioManager.activePlaybackConfigurations) {
            val attributes = config.audioAttributes
            configs.put(JSONObject().put("usage", attributes.usage).put("contentType", attributes.contentType))
        }
        val snapshot = JSONObject()
            .put("musicActive", audioManager.isMusicActive)
            .put("audioMode", audioManager.mode)
            .put("playbackConfigs", configs)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            snapshot.put("communicationDevice", audioManager.communicationDevice?.let { DeviceInfo.typeName(it.type) } ?: "none")
        }
        return snapshot
    }
}
