// SPDX-License-Identifier: GPL-3.0-only
package io.github.nitinreddy08.echocatch.status

import io.github.nitinreddy08.echocatch.model.LevelMeter
import io.github.nitinreddy08.echocatch.policy.ListeningStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** A snapshot of the debug level meter (M1-T5). Numbers only; the audio itself is never kept. */
data class LevelReading(
    val peakDb: Float = LevelMeter.SILENCE_DB,
    val rmsDb: Float = LevelMeter.SILENCE_DB,
    val silentMs: Long = 0L,
)

/**
 * The true state of listening, shared by the service (which writes it) and the screens (which only read it).
 * One instance lives as long as the process; a fresh process starts Disarmed (ADR-0004).
 */
class ListeningStatusStore {
    private val statusFlow = MutableStateFlow(ListeningStatus())
    private val levelFlow = MutableStateFlow(LevelReading())

    val status: StateFlow<ListeningStatus> = statusFlow.asStateFlow()
    val level: StateFlow<LevelReading> = levelFlow.asStateFlow()

    fun update(transform: (ListeningStatus) -> ListeningStatus) = statusFlow.update(transform)

    fun publishLevel(reading: LevelReading) {
        levelFlow.value = reading
    }
}
