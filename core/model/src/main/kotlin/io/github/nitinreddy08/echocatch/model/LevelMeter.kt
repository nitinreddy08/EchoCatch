// SPDX-License-Identifier: GPL-3.0-only
package io.github.nitinreddy08.echocatch.model

import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Peak and RMS level of 16-bit PCM in dBFS, plus how long the input has been exactly zero.
 *
 * [update] runs on the audio thread, so it allocates nothing and takes no lock. The three readings are
 * `@Volatile` so the control plane can sample them from another thread; they are a debug display, not a
 * consistent snapshot. The audio itself is never stored (project rule).
 *
 * Exactly-zero input is how Android delivers a silenced capture (another app has priority, or a Bluetooth
 * route that never opened, as seen in spike S1), so [silentMs] lets the status screen say so honestly.
 */
class LevelMeter {
    @Volatile
    var peakDb: Float = SILENCE_DB
        private set

    @Volatile
    var rmsDb: Float = SILENCE_DB
        private set

    @Volatile
    var silentMs: Long = 0L
        private set

    /** Reads the first [count] samples of [samples]; [count] larger than the array is cut to its size. */
    fun update(samples: ShortArray, count: Int, sampleRateHz: Int) {
        val n = if (count > samples.size) samples.size else count
        if (n <= 0 || sampleRateHz <= 0) return
        var peak = 0
        var sumSquares = 0.0
        for (i in 0 until n) {
            val s = samples[i].toInt()
            val magnitude = if (s < 0) -s else s
            if (magnitude > peak) peak = magnitude
            sumSquares += s.toDouble() * s
        }
        peakDb = toDb(peak / FULL_SCALE)
        rmsDb = toDb(sqrt(sumSquares / n) / FULL_SCALE)
        silentMs = if (peak == 0) silentMs + n * 1000L / sampleRateHz else 0L
    }

    fun reset() {
        peakDb = SILENCE_DB
        rmsDb = SILENCE_DB
        silentMs = 0L
    }

    private fun toDb(ratio: Double): Float = if (ratio <= 0.0) SILENCE_DB else max(SILENCE_DB, (20.0 * log10(ratio)).toFloat())

    companion object {
        /** Reading for silence, and the floor of the scale. */
        const val SILENCE_DB = -90f
        private const val FULL_SCALE = 32768.0
    }
}
