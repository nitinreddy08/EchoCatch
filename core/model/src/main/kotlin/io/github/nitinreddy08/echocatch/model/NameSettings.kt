// SPDX-License-Identifier: GPL-3.0-only
package io.github.nitinreddy08.echocatch.model

/** Why a [NameSettings] cannot be used yet. */
enum class NameProblem {
    NAME_EMPTY,
    NAME_TOO_LONG,
    TOO_MANY_NICKNAMES,
    NICKNAME_TOO_LONG,
    SOUNDS_LIKE_TOO_LONG,
    INVALID_CHARACTERS,
}

/**
 * What to listen for (FR-1): the user's name, up to [MAX_NICKNAMES] nicknames, and an optional "how it sounds"
 * spelling such as "Shivawn" for Siobhan (the S4 screening showed that a typed spelling alone often fails).
 *
 * The text is stored on the device only. Names in any script are allowed; only letters, combining marks, spaces,
 * apostrophes, hyphens and full stops are accepted.
 */
data class NameSettings(
    val name: String = "",
    val nicknames: List<String> = emptyList(),
    val soundsLike: String = "",
) {
    /** Trimmed and single-spaced, with blank entries and case-insensitive repeats of the name removed. */
    fun normalized(): NameSettings {
        val cleanName = clean(name)
        val seen = mutableSetOf(cleanName.lowercase())
        val cleanNicknames = nicknames.map(::clean).filter { it.isNotEmpty() && seen.add(it.lowercase()) }
        return NameSettings(cleanName, cleanNicknames, clean(soundsLike))
    }

    /** Everything that stops these settings from being used; empty when they are fine. */
    fun validate(): Set<NameProblem> {
        val value = normalized()
        val problems = linkedSetOf<NameProblem>()
        if (value.name.isEmpty()) problems += NameProblem.NAME_EMPTY
        if (value.name.length > MAX_LENGTH) problems += NameProblem.NAME_TOO_LONG
        if (value.nicknames.size > MAX_NICKNAMES) problems += NameProblem.TOO_MANY_NICKNAMES
        if (value.nicknames.any { it.length > MAX_LENGTH }) problems += NameProblem.NICKNAME_TOO_LONG
        if (value.soundsLike.length > MAX_LENGTH) problems += NameProblem.SOUNDS_LIKE_TOO_LONG
        val everything = listOf(value.name, value.soundsLike) + value.nicknames
        if (everything.any { text -> text.any { !isAllowed(it) } }) problems += NameProblem.INVALID_CHARACTERS
        return problems
    }

    /** True when a name is present and nothing in [validate] objects. */
    val isUsable: Boolean get() = validate().isEmpty()

    companion object {
        const val MAX_NICKNAMES = 2
        const val MAX_LENGTH = 40

        private fun clean(text: String): String = text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.joinToString(" ")

        private fun isAllowed(c: Char): Boolean =
            c.isLetter() ||
                c.category == CharCategory.NON_SPACING_MARK ||
                c.category == CharCategory.COMBINING_SPACING_MARK ||
                c == ' ' || c == '\'' || c == '’' || c == '-' || c == '.'
    }
}
