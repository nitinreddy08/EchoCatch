// SPDX-License-Identifier: GPL-3.0-only
package io.github.nitinreddy08.echocatch.lab

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import kotlin.math.abs

/**
 * Records one fixed-length clip from the phone microphone (the same `MIC` source and 16 kHz format the real app
 * uses) for the Lab app's consented test clips (ADR-0012). The clip is held in memory until the caller decides to
 * save it; callbacks arrive on the main thread.
 */
class ClipRecorder(private val context: Context) {
    interface Listener {
        fun onProgress(elapsedMs: Long, peakDbfs: Double)

        fun onFinished(result: Result)
    }

    sealed class Result {
        class Recorded(val samples: ShortArray, val count: Int, val stats: JSONObject) : Result()

        class Failed(val message: String) : Result()

        object Cancelled : Result()
    }

    @Volatile
    private var cancelled = false
    private val main = Handler(Looper.getMainLooper())

    fun start(seconds: Int, listener: Listener) {
        cancelled = false
        Thread({ record(seconds, listener) }, "lab-clip-recorder").start()
    }

    fun cancel() {
        cancelled = true
    }

    @SuppressLint("MissingPermission") // checked first thing in record()
    private fun record(seconds: Int, listener: Listener) {
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            finish(listener, Result.Failed("The microphone permission is missing."))
            return
        }
        val minBytes = AudioRecord.getMinBufferSize(SAMPLE_RATE_HZ, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val total = SAMPLE_RATE_HZ * seconds
        val chunk = SAMPLE_RATE_HZ / 10
        val record = try {
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE_HZ,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                maxOf(minBytes, chunk * 2 * 4),
            )
        } catch (e: IllegalArgumentException) {
            finish(listener, Result.Failed("The microphone could not be opened: ${e.message}"))
            return
        }
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            finish(listener, Result.Failed("The microphone could not be opened. Stop any running lab service and try again."))
            return
        }
        val samples = ShortArray(total)
        var filled = 0
        var chunkPeak = 0
        try {
            record.startRecording()
            while (filled < total && !cancelled) {
                val read = record.read(samples, filled, minOf(chunk, total - filled))
                if (read <= 0) {
                    finish(listener, Result.Failed("The microphone stopped delivering sound."))
                    return
                }
                chunkPeak = 0
                for (i in filled until filled + read) chunkPeak = maxOf(chunkPeak, abs(samples[i].toInt()))
                filled += read
                val elapsedMs = filled * 1000L / SAMPLE_RATE_HZ
                val peakDbfs = SignalStats.dbfsOf(chunkPeak.toDouble())
                main.post { listener.onProgress(elapsedMs, peakDbfs) }
            }
        } catch (e: IllegalStateException) {
            finish(listener, Result.Failed("The microphone failed: ${e.message}"))
            return
        } finally {
            try {
                record.stop()
            } catch (e: IllegalStateException) {
                // never started
            }
            record.release()
        }
        if (cancelled) {
            finish(listener, Result.Cancelled)
        } else {
            finish(listener, Result.Recorded(samples, filled, SignalStats.summarize(samples, filled, SAMPLE_RATE_HZ)))
        }
    }

    private fun finish(listener: Listener, result: Result) {
        main.post { listener.onFinished(result) }
    }

    companion object {
        const val SAMPLE_RATE_HZ = 16_000
    }
}
