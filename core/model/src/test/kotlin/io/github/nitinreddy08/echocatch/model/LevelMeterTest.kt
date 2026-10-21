// SPDX-License-Identifier: GPL-3.0-only
package io.github.nitinreddy08.echocatch.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LevelMeterTest {
    private val rate = 16_000

    @Test
    fun startsAtTheFloor() {
        val meter = LevelMeter()
        assertEquals(LevelMeter.SILENCE_DB, meter.peakDb)
        assertEquals(LevelMeter.SILENCE_DB, meter.rmsDb)
        assertEquals(0L, meter.silentMs)
    }

    @Test
    fun zeroInputReadsAsSilenceAndAccumulatesTime() {
        val meter = LevelMeter()
        meter.update(ShortArray(1_600), 1_600, rate)
        meter.update(ShortArray(1_600), 1_600, rate)
        assertEquals(LevelMeter.SILENCE_DB, meter.peakDb)
        assertEquals(LevelMeter.SILENCE_DB, meter.rmsDb)
        assertEquals(200L, meter.silentMs)
    }

    @Test
    fun soundResetsTheSilentTime() {
        val meter = LevelMeter()
        meter.update(ShortArray(1_600), 1_600, rate)
        meter.update(ShortArray(1_600) { 1 }, 1_600, rate)
        assertEquals(0L, meter.silentMs)
    }

    @Test
    fun halfScaleIsAboutMinusSixDecibels() {
        val meter = LevelMeter()
        meter.update(ShortArray(160) { 16_384 }, 160, rate)
        assertEquals(-6.02f, meter.peakDb, 0.01f)
        assertEquals(-6.02f, meter.rmsDb, 0.01f)
    }

    @Test
    fun fullScaleSquareWaveIsAboutZeroDecibels() {
        val meter = LevelMeter()
        val wave = ShortArray(160) { if (it % 2 == 0) Short.MAX_VALUE else Short.MIN_VALUE }
        meter.update(wave, wave.size, rate)
        assertEquals(0f, meter.peakDb, 0.01f)
        assertEquals(0f, meter.rmsDb, 0.01f)
    }

    @Test
    fun peakIsAboveRmsForASingleSpike() {
        val meter = LevelMeter()
        val samples = ShortArray(100)
        samples[10] = 20_000
        meter.update(samples, samples.size, rate)
        assertTrue(meter.peakDb > meter.rmsDb)
    }

    @Test
    fun onlyTheFirstCountSamplesAreRead() {
        val meter = LevelMeter()
        val samples = ShortArray(100) { if (it < 50) 0 else 30_000 }
        meter.update(samples, 50, rate)
        assertEquals(LevelMeter.SILENCE_DB, meter.peakDb)
    }

    @Test
    fun anOversizedOrEmptyCountIsHandledWithoutFailing() {
        val meter = LevelMeter()
        meter.update(ShortArray(10) { 16_384 }, 1_000, rate)
        assertEquals(-6.02f, meter.peakDb, 0.01f)
        meter.update(ShortArray(10), 0, rate)
        meter.update(ShortArray(10), -5, rate)
        assertEquals(-6.02f, meter.peakDb, 0.01f)
    }

    @Test
    fun aBadSampleRateIsIgnored() {
        val meter = LevelMeter()
        meter.update(ShortArray(10) { 16_384 }, 10, 0)
        assertEquals(LevelMeter.SILENCE_DB, meter.peakDb)
    }

    @Test
    fun resetReturnsToTheFloor() {
        val meter = LevelMeter()
        meter.update(ShortArray(100) { 10_000 }, 100, rate)
        meter.reset()
        assertEquals(LevelMeter.SILENCE_DB, meter.peakDb)
        assertEquals(LevelMeter.SILENCE_DB, meter.rmsDb)
        assertEquals(0L, meter.silentMs)
    }

    @Test
    fun theMostNegativeSampleDoesNotOverflow() {
        val meter = LevelMeter()
        meter.update(ShortArray(10) { Short.MIN_VALUE }, 10, rate)
        assertEquals(0f, meter.peakDb, 0.001f)
    }
}
