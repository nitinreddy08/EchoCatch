// SPDX-License-Identifier: GPL-3.0-only
package io.github.nitinreddy08.echocatch.policy

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ListeningStatusTest {
    @Test
    fun startsDisarmedWithNoHistory() {
        val status = ListeningStatus()
        assertEquals(ArmingState.DISARMED, status.state)
        assertFalse(status.isRunning)
        assertFalse(status.micSilent)
        assertNull(status.lastStop)
    }

    @Test
    fun startingClearsThePreviousStopReason() {
        val running = ListeningStatus().stopped(StopReason.CAPTURE_ERROR).started()
        assertEquals(ArmingState.LISTENING, running.state)
        assertTrue(running.isRunning)
        assertNull(running.lastStop)
    }

    @Test
    fun stoppingRecordsTheReasonAndClearsSilence() {
        val stopped = ListeningStatus().started().withMicSilent(true).stopped(StopReason.USER)
        assertEquals(ArmingState.DISARMED, stopped.state)
        assertFalse(stopped.micSilent)
        assertEquals(StopReason.USER, stopped.lastStop)
    }

    @Test
    fun silenceIsTrackedOnlyWhileRunning() {
        val idle = ListeningStatus()
        assertEquals(idle, idle.withMicSilent(true))
        val running = ListeningStatus().started()
        assertTrue(running.withMicSilent(true).micSilent)
        assertFalse(running.withMicSilent(true).withMicSilent(false).micSilent)
    }

    @Test
    fun anUnexpectedStopCanBeReportedOnAFreshProcess() {
        val status = ListeningStatus().stopped(StopReason.SYSTEM_STOPPED)
        assertFalse(status.isRunning)
        assertEquals(StopReason.SYSTEM_STOPPED, status.lastStop)
    }
}
