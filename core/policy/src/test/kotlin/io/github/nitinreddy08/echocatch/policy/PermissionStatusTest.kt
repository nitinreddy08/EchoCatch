// SPDX-License-Identifier: GPL-3.0-only
package io.github.nitinreddy08.echocatch.policy

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PermissionStatusTest {
    @Test
    fun grantedWinsOverEverythingElse() {
        for (asked in listOf(false, true)) {
            for (rationale in listOf(false, true)) {
                assertEquals(PermissionStatus.GRANTED, PermissionStatus.resolve(true, asked, rationale))
            }
        }
    }

    @Test
    fun neverAskedAndNoRationaleIsNotAsked() {
        assertEquals(PermissionStatus.NOT_ASKED, PermissionStatus.resolve(granted = false, askedBefore = false, shouldShowRationale = false))
    }

    @Test
    fun refusedOnceWithRationaleCanBeAskedAgain() {
        assertEquals(PermissionStatus.DENIED, PermissionStatus.resolve(granted = false, askedBefore = true, shouldShowRationale = true))
    }

    @Test
    fun askedBeforeWithoutRationaleIsBlocked() {
        assertEquals(PermissionStatus.BLOCKED, PermissionStatus.resolve(granted = false, askedBefore = true, shouldShowRationale = false))
    }

    @Test
    fun onlyGrantedCountsAsGranted() {
        assertTrue(PermissionStatus.GRANTED.isGranted)
        assertFalse(PermissionStatus.NOT_ASKED.isGranted)
        assertFalse(PermissionStatus.DENIED.isGranted)
        assertFalse(PermissionStatus.BLOCKED.isGranted)
    }
}
