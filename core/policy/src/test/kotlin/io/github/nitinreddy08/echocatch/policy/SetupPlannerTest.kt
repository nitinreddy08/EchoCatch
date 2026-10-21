// SPDX-License-Identifier: GPL-3.0-only
package io.github.nitinreddy08.echocatch.policy

import io.github.nitinreddy08.echocatch.model.NameSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SetupPlannerTest {
    private val ready = SetupInputs(
        disclosureAccepted = true,
        microphone = PermissionStatus.GRANTED,
        notificationPermissionApplies = true,
        notifications = PermissionStatus.GRANTED,
        notificationsDeclined = false,
        name = NameSettings(name = "Maya"),
    )

    @Test
    fun everythingInPlaceIsReady() {
        assertEquals(SetupStep.READY, SetupPlanner.nextStep(ready))
    }

    @Test
    fun theDisclosureComesBeforeAnySystemDialog() {
        val fresh = ready.copy(
            disclosureAccepted = false,
            microphone = PermissionStatus.NOT_ASKED,
            notifications = PermissionStatus.NOT_ASKED,
            name = NameSettings(),
        )
        assertEquals(SetupStep.DISCLOSURE, SetupPlanner.nextStep(fresh))
    }

    @Test
    fun theMicrophoneIsAskedAfterTheDisclosure() {
        for (status in listOf(PermissionStatus.NOT_ASKED, PermissionStatus.DENIED)) {
            assertEquals(SetupStep.MICROPHONE, SetupPlanner.nextStep(ready.copy(microphone = status)))
        }
    }

    @Test
    fun aBlockedMicrophoneSendsTheUserToSettings() {
        assertEquals(SetupStep.MICROPHONE_BLOCKED, SetupPlanner.nextStep(ready.copy(microphone = PermissionStatus.BLOCKED)))
    }

    @Test
    fun revokingTheMicrophoneLaterReturnsToThatStep() {
        assertEquals(SetupStep.READY, SetupPlanner.nextStep(ready))
        assertEquals(SetupStep.MICROPHONE_BLOCKED, SetupPlanner.nextStep(ready.copy(microphone = PermissionStatus.BLOCKED)))
    }

    @Test
    fun notificationsAreAskedWhenTheyApplyAndAreNotDecided() {
        for (status in listOf(PermissionStatus.NOT_ASKED, PermissionStatus.DENIED)) {
            assertEquals(SetupStep.NOTIFICATIONS, SetupPlanner.nextStep(ready.copy(notifications = status)))
        }
    }

    @Test
    fun notificationsAreSkippedBeforeAndroid13() {
        val old = ready.copy(notificationPermissionApplies = false, notifications = PermissionStatus.NOT_ASKED)
        assertEquals(SetupStep.READY, SetupPlanner.nextStep(old))
    }

    @Test
    fun notificationsAreNotAskedAgainAfterNotNowOrWhenBlocked() {
        assertEquals(
            SetupStep.READY,
            SetupPlanner.nextStep(ready.copy(notifications = PermissionStatus.DENIED, notificationsDeclined = true)),
        )
        assertEquals(SetupStep.READY, SetupPlanner.nextStep(ready.copy(notifications = PermissionStatus.BLOCKED)))
    }

    @Test
    fun theNameIsAskedLast() {
        assertEquals(SetupStep.NAME, SetupPlanner.nextStep(ready.copy(name = NameSettings())))
        assertEquals(SetupStep.NAME, SetupPlanner.nextStep(ready.copy(name = NameSettings(name = "R2D2"))))
        assertEquals(
            SetupStep.NOTIFICATIONS,
            SetupPlanner.nextStep(ready.copy(name = NameSettings(), notifications = PermissionStatus.NOT_ASKED)),
        )
    }

    @Test
    fun listeningNeedsOnlyTheDisclosureAndTheMicrophone() {
        assertTrue(SetupPlanner.canStartListening(ready))
        assertTrue(
            SetupPlanner.canStartListening(
                ready.copy(notifications = PermissionStatus.BLOCKED, name = NameSettings()),
            ),
        )
        assertFalse(SetupPlanner.canStartListening(ready.copy(disclosureAccepted = false)))
        for (status in listOf(PermissionStatus.NOT_ASKED, PermissionStatus.DENIED, PermissionStatus.BLOCKED)) {
            assertFalse(SetupPlanner.canStartListening(ready.copy(microphone = status)))
        }
    }

    @Test
    fun theNotificationMayBeHiddenOnlyWhereThePermissionAppliesAndIsMissing() {
        assertFalse(SetupPlanner.notificationMayBeHidden(ready))
        assertTrue(SetupPlanner.notificationMayBeHidden(ready.copy(notifications = PermissionStatus.BLOCKED)))
        assertTrue(SetupPlanner.notificationMayBeHidden(ready.copy(notifications = PermissionStatus.NOT_ASKED)))
        assertFalse(
            SetupPlanner.notificationMayBeHidden(
                ready.copy(notificationPermissionApplies = false, notifications = PermissionStatus.NOT_ASKED),
            ),
        )
    }
}
