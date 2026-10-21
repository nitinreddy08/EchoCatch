// SPDX-License-Identifier: GPL-3.0-only
package io.github.nitinreddy08.echocatch.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.nitinreddy08.echocatch.model.NameSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

/** Everything the app remembers. It is stored on the device only and never leaves it (ADR-0007). */
data class AppSettings(
    val disclosureAccepted: Boolean = false,
    /** Android cannot say "asked before", so the app remembers it to tell "never asked" from "blocked". */
    val microphoneAsked: Boolean = false,
    val notificationsAsked: Boolean = false,
    val notificationsDeclined: Boolean = false,
    val name: NameSettings = NameSettings(),
    /** True from the moment listening starts until it stops on purpose; still true on a fresh start means Android ended it. */
    val listeningWanted: Boolean = false,
)

class SettingsRepository(private val store: DataStore<Preferences>) {
    val settings: Flow<AppSettings> = store.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map { it.toSettings() }

    suspend fun current(): AppSettings = settings.first()

    suspend fun acceptDisclosure() = store.edit { it[DISCLOSURE_ACCEPTED] = true }

    suspend fun markMicrophoneAsked() = store.edit { it[MICROPHONE_ASKED] = true }

    suspend fun markNotificationsAsked() = store.edit { it[NOTIFICATIONS_ASKED] = true }

    suspend fun declineNotifications() = store.edit { it[NOTIFICATIONS_DECLINED] = true }

    suspend fun setListeningWanted(wanted: Boolean) = store.edit { it[LISTENING_WANTED] = wanted }

    /** Saves the cleaned-up name; settings that do not validate are not stored. */
    suspend fun saveName(value: NameSettings) {
        val clean = value.normalized()
        if (!clean.isUsable) return
        store.edit {
            it[NAME] = clean.name
            it[NICKNAME_1] = clean.nicknames.getOrElse(0) { "" }
            it[NICKNAME_2] = clean.nicknames.getOrElse(1) { "" }
            it[SOUNDS_LIKE] = clean.soundsLike
        }
    }

    private fun Preferences.toSettings() = AppSettings(
        disclosureAccepted = this[DISCLOSURE_ACCEPTED] ?: false,
        microphoneAsked = this[MICROPHONE_ASKED] ?: false,
        notificationsAsked = this[NOTIFICATIONS_ASKED] ?: false,
        notificationsDeclined = this[NOTIFICATIONS_DECLINED] ?: false,
        name = NameSettings(
            name = this[NAME] ?: "",
            nicknames = listOf(this[NICKNAME_1] ?: "", this[NICKNAME_2] ?: "").filter { it.isNotBlank() },
            soundsLike = this[SOUNDS_LIKE] ?: "",
        ),
        listeningWanted = this[LISTENING_WANTED] ?: false,
    )

    private companion object {
        val DISCLOSURE_ACCEPTED = booleanPreferencesKey("disclosure_accepted")
        val MICROPHONE_ASKED = booleanPreferencesKey("microphone_asked")
        val NOTIFICATIONS_ASKED = booleanPreferencesKey("notifications_asked")
        val NOTIFICATIONS_DECLINED = booleanPreferencesKey("notifications_declined")
        val LISTENING_WANTED = booleanPreferencesKey("listening_wanted")
        val NAME = stringPreferencesKey("name")
        val NICKNAME_1 = stringPreferencesKey("nickname_1")
        val NICKNAME_2 = stringPreferencesKey("nickname_2")
        val SOUNDS_LIKE = stringPreferencesKey("sounds_like")
    }
}
