// SPDX-License-Identifier: GPL-3.0-only
package io.github.nitinreddy08.echocatch.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SourceIdTest {
    @Test
    fun phoneMicIsTheBaselineAndNeedsNoFallback() {
        assertFalse(SourceId.PHONE_MIC.needsFallback)
    }

    @Test
    fun everyHeadsetSourceNeedsFallback() {
        val headsetSources = SourceId.entries - SourceId.PHONE_MIC
        assertEquals(3, headsetSources.size)
        assertTrue(headsetSources.all { it.needsFallback })
    }
}
