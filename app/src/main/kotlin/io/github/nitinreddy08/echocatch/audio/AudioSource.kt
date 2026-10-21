// SPDX-License-Identifier: GPL-3.0-only
package io.github.nitinreddy08.echocatch.audio

import io.github.nitinreddy08.echocatch.model.SourceId

/** Why a source could not be opened. */
enum class OpenFailure {
    PERMISSION_MISSING,
    UNAVAILABLE,
}

sealed interface OpenResult {
    data class Opened(val sampleRateHz: Int) : OpenResult

    data class Failed(val reason: OpenFailure) : OpenResult
}

/** Why a running capture ended on its own. */
enum class SourceError {
    START_FAILED,
    READ_FAILED,
}

/**
 * Receives microphone audio on the capture thread.
 *
 * [onFrame] must not allocate, lock or log: the same buffer is reused for every frame and is only valid until the
 * call returns. Raw audio is never stored or logged (project rule).
 */
interface PcmSink {
    fun onFrame(samples: ShortArray, count: Int)

    fun onSourceError(error: SourceError)
}

/**
 * Where microphone audio comes from (ADR-0002, docs/plan/03-architecture.md section 8). M1 has the phone
 * microphone only; headset routes arrive in milestone M4 behind this same interface.
 *
 * [open] and [close] block briefly and belong on the control thread. Capture runs on its own thread between
 * [startCapture] and [stopCapture]. [close] must release the route promptly.
 */
interface AudioSource {
    val id: SourceId

    fun open(): OpenResult

    fun startCapture(sink: PcmSink)

    fun stopCapture()

    fun close()
}
