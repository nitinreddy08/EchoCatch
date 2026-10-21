// SPDX-License-Identifier: GPL-3.0-only
package io.github.nitinreddy08.echocatch.audio

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Process
import androidx.core.content.ContextCompat
import io.github.nitinreddy08.echocatch.model.SourceId

/**
 * The phone's own microphone: the universal Tier 0 baseline that works on every supported Android version and with
 * every pair of earphones (ADR-0002, ADR-0009). 16 kHz mono 16-bit, read in 100 ms hops. Using the earbuds' own
 * microphone is a later upgrade that falls back to this.
 *
 * The audio source is `MIC`, the one spike S1 showed working with music playing; whether `VOICE_RECOGNITION`
 * behaves better is a measurement for M2 (ADR-0001), not an assumption here.
 */
class PhoneMicSource(private val context: Context) : AudioSource {
    override val id: SourceId = SourceId.PHONE_MIC

    private var record: AudioRecord? = null
    private var frame = ShortArray(0)
    private var thread: Thread? = null

    @Volatile
    private var capturing = false

    @SuppressLint("MissingPermission") // checked on the first line of the function
    override fun open(): OpenResult {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            return OpenResult.Failed(OpenFailure.PERMISSION_MISSING)
        }
        close()
        val minBytes = AudioRecord.getMinBufferSize(SAMPLE_RATE_HZ, CHANNEL_CONFIG, ENCODING)
        if (minBytes <= 0) return OpenResult.Failed(OpenFailure.UNAVAILABLE)
        val hopSamples = SAMPLE_RATE_HZ * HOP_MS / 1000
        val bufferBytes = maxOf(minBytes, hopSamples * BYTES_PER_SAMPLE * BUFFER_HOPS)
        val created = try {
            AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE_HZ, CHANNEL_CONFIG, ENCODING, bufferBytes)
        } catch (e: IllegalArgumentException) {
            return OpenResult.Failed(OpenFailure.UNAVAILABLE)
        } catch (e: SecurityException) {
            return OpenResult.Failed(OpenFailure.PERMISSION_MISSING)
        }
        if (created.state != AudioRecord.STATE_INITIALIZED) {
            created.release()
            return OpenResult.Failed(OpenFailure.UNAVAILABLE)
        }
        record = created
        frame = ShortArray(hopSamples)
        return OpenResult.Opened(SAMPLE_RATE_HZ)
    }

    override fun startCapture(sink: PcmSink) {
        val active = record ?: return
        capturing = true
        thread = Thread({ captureLoop(active, sink) }, "echocatch-capture").also { it.start() }
    }

    override fun stopCapture() {
        capturing = false
        val running = thread ?: return
        thread = null
        try {
            running.join(JOIN_TIMEOUT_MS)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    override fun close() {
        stopCapture()
        record?.release()
        record = null
    }

    /** The audio thread: one preallocated buffer, a blocking read, no allocation and no locks in the loop. */
    @SuppressLint("MissingPermission") // the record exists only because open() checked the permission
    private fun captureLoop(active: AudioRecord, sink: PcmSink) {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
        try {
            active.startRecording()
        } catch (e: IllegalStateException) {
            sink.onSourceError(SourceError.START_FAILED)
            return
        }
        if (active.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
            sink.onSourceError(SourceError.START_FAILED)
            return
        }
        val buffer = frame
        while (capturing) {
            val read = active.read(buffer, 0, buffer.size)
            if (read > 0) {
                sink.onFrame(buffer, read)
            } else if (read < 0) {
                if (capturing) sink.onSourceError(SourceError.READ_FAILED)
                break
            }
        }
        try {
            active.stop()
        } catch (e: IllegalStateException) {
            // already stopped by the system; nothing left to release here
        }
    }

    private companion object {
        const val SAMPLE_RATE_HZ = 16_000
        const val HOP_MS = 100
        const val BUFFER_HOPS = 4
        const val BYTES_PER_SAMPLE = 2
        const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
        const val JOIN_TIMEOUT_MS = 1_000L
    }
}
