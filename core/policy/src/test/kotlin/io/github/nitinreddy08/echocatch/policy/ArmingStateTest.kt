// SPDX-License-Identifier: GPL-3.0-only
package io.github.nitinreddy08.echocatch.policy

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ArmingStateTest {
    @Test
    fun microphoneIsClosedUntilListening() {
        assertFalse(ArmingState.DISARMED.micOpen)
        assertFalse(ArmingState.ARMED_IDLE.micOpen)
    }

    @Test
    fun microphoneIsOpenWhileListeningAlertingAndCoolingDown() {
        assertTrue(ArmingState.LISTENING.micOpen)
        assertTrue(ArmingState.ALERTING.micOpen)
        assertTrue(ArmingState.COOLDOWN.micOpen)
    }

    @Test
    fun statesMatchTheArchitectureDocument() {
        assertEquals(
            listOf("DISARMED", "ARMED_IDLE", "LISTENING", "ALERTING", "COOLDOWN"),
            ArmingState.entries.map { it.name },
        )
    }
}
