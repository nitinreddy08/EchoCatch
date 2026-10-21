// SPDX-License-Identifier: GPL-3.0-only
package io.github.nitinreddy08.echocatch.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NameSettingsTest {
    @Test
    fun emptySettingsAreNotUsable() {
        assertEquals(setOf(NameProblem.NAME_EMPTY), NameSettings().validate())
        assertFalse(NameSettings(name = "   ").isUsable)
    }

    @Test
    fun aPlainNameIsUsable() {
        assertTrue(NameSettings(name = "Maya").isUsable)
    }

    @Test
    fun normalizingTrimsCollapsesSpacesAndDropsBlankAndRepeatedNicknames() {
        val result = NameSettings(
            name = "  Mary   Ann ",
            nicknames = listOf(" Annie ", "", "annie", "mary ann", "Mae"),
            soundsLike = "  Mary  Ann ",
        ).normalized()
        assertEquals("Mary Ann", result.name)
        assertEquals(listOf("Annie", "Mae"), result.nicknames)
        assertEquals("Mary Ann", result.soundsLike)
    }

    @Test
    fun namesInOtherScriptsAndWithPunctuationAreAccepted() {
        for (name in listOf("José", "Nguyễn", "D'Souza", "O’Brien", "Mary-Ann", "St. John", "प्रिया", "Siobhán")) {
            assertEquals(emptySet(), NameSettings(name = name).validate(), name)
        }
    }

    @Test
    fun digitsAndSymbolsAreRejected() {
        for (name in listOf("R2D2", "Max!", "a/b", "Bob#Smith")) {
            assertEquals(setOf(NameProblem.INVALID_CHARACTERS), NameSettings(name = name).validate(), name)
        }
    }

    @Test
    fun lengthLimitsApplyToEveryField() {
        val long = "a".repeat(NameSettings.MAX_LENGTH + 1)
        assertEquals(setOf(NameProblem.NAME_TOO_LONG), NameSettings(name = long).validate())
        assertEquals(setOf(NameProblem.NICKNAME_TOO_LONG), NameSettings(name = "Maya", nicknames = listOf(long)).validate())
        assertEquals(setOf(NameProblem.SOUNDS_LIKE_TOO_LONG), NameSettings(name = "Maya", soundsLike = long).validate())
        assertTrue(NameSettings(name = "a".repeat(NameSettings.MAX_LENGTH)).isUsable)
    }

    @Test
    fun atMostTwoNicknames() {
        assertTrue(NameSettings(name = "Maya", nicknames = listOf("May", "Mimi")).isUsable)
        assertEquals(
            setOf(NameProblem.TOO_MANY_NICKNAMES),
            NameSettings(name = "Maya", nicknames = listOf("May", "Mimi", "Yaya")).validate(),
        )
    }

    @Test
    fun blankNicknamesDoNotCountTowardTheLimit() {
        assertTrue(NameSettings(name = "Maya", nicknames = listOf("May", "", " ", "Mimi")).isUsable)
    }

    @Test
    fun severalProblemsAreAllReported() {
        val problems = NameSettings(name = "", nicknames = listOf("x1"), soundsLike = "y2").validate()
        assertEquals(setOf(NameProblem.NAME_EMPTY, NameProblem.INVALID_CHARACTERS), problems)
    }
}
