// SPDX-License-Identifier: GPL-3.0-only
package io.github.nitinreddy08.echocatch.lab

import org.json.JSONObject
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.sqrt

/** Simple, dependency-free measurements of a recorded buffer. Only numbers leave this class, never audio. */
object SignalStats {
    private val bandFrequenciesHz = intArrayOf(250, 500, 1000, 2000, 3000, 3500, 4000, 5000, 6000, 7000, 10000, 14000)

    fun summarize(samples: ShortArray, count: Int, sampleRate: Int): JSONObject {
        if (count <= 0) return JSONObject().put("samples", 0)
        var sumSquares = 0.0
        var peak = 0
        var zeros = 0
        for (i in 0 until count) {
            val s = samples[i].toInt()
            sumSquares += s.toDouble() * s
            peak = maxOf(peak, abs(s))
            if (s == 0) zeros++
        }
        val rms = sqrt(sumSquares / count)
        val bands = JSONObject()
        val powers = DoubleArray(bandFrequenciesHz.size)
        var total = 0.0
        for ((index, frequency) in bandFrequenciesHz.withIndex()) {
            if (frequency < sampleRate / 2 * 0.95) {
                powers[index] = goertzelPower(samples, count, sampleRate, frequency)
                total += powers[index]
            }
        }
        for ((index, frequency) in bandFrequenciesHz.withIndex()) {
            if (frequency < sampleRate / 2 * 0.95 && total > 0.0) {
                bands.put("${frequency}Hz", powers[index] / total)
            }
        }
        // Loudness per 100 ms window: shows whether speech was present (steady near-silence means nobody spoke).
        val windowSize = sampleRate / 10
        val windowLevels = ArrayList<Double>()
        var start = 0
        while (start + windowSize <= count) {
            var windowSquares = 0.0
            for (i in start until start + windowSize) windowSquares += samples[i].toDouble() * samples[i]
            windowLevels.add(dbfs(sqrt(windowSquares / windowSize)))
            start += windowSize
        }
        windowLevels.sort()
        val p95 = if (windowLevels.isEmpty()) -120.0 else windowLevels[((windowLevels.size - 1) * 0.95).toInt()]
        val loudWindows = windowLevels.count { it > -45.0 }.toDouble() / maxOf(1, windowLevels.size)
        return JSONObject()
            .put("samples", count)
            .put("windowRmsP95Dbfs", p95)
            .put("fractionOfWindowsAboveMinus45Dbfs", loudWindows)
            .put("rmsDbfs", dbfs(rms))
            .put("peakDbfs", dbfs(peak.toDouble()))
            .put("zeroSampleFraction", zeros.toDouble() / count)
            .put("relativeBandPower", bands)
    }

    /** Level of a sample magnitude (0 to 32768) in dB relative to full scale; -120 for silence. */
    fun dbfsOf(value: Double): Double = dbfs(value)

    private fun dbfs(value: Double): Double = if (value <= 0.0) -120.0 else 20.0 * log10(value / 32768.0)

    private fun goertzelPower(samples: ShortArray, count: Int, sampleRate: Int, frequency: Int): Double {
        val coefficient = 2.0 * cos(2.0 * PI * frequency / sampleRate)
        var s1 = 0.0
        var s2 = 0.0
        for (i in 0 until count) {
            val s = samples[i] + coefficient * s1 - s2
            s2 = s1
            s1 = s
        }
        return s1 * s1 + s2 * s2 - coefficient * s1 * s2
    }
}
