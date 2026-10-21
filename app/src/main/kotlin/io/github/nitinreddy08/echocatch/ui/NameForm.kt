// SPDX-License-Identifier: GPL-3.0-only
package io.github.nitinreddy08.echocatch.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import io.github.nitinreddy08.echocatch.R
import io.github.nitinreddy08.echocatch.model.NameProblem
import io.github.nitinreddy08.echocatch.model.NameSettings

/**
 * The fields for FR-1: the name, up to two nicknames and an optional "how it sounds" spelling.
 * Problems are only shown after the first attempt to save, so nobody is scolded for a half-typed name.
 */
@Composable
internal fun NameForm(
    initial: NameSettings,
    saveLabel: String,
    showSavedNotice: Boolean,
    onSave: (NameSettings) -> Unit,
    modifier: Modifier = Modifier,
) {
    var name by rememberSaveable { mutableStateOf(initial.name) }
    var nickname by rememberSaveable { mutableStateOf(initial.nicknames.getOrElse(0) { "" }) }
    var nicknameTwo by rememberSaveable { mutableStateOf(initial.nicknames.getOrElse(1) { "" }) }
    var soundsLike by rememberSaveable { mutableStateOf(initial.soundsLike) }
    var attempted by rememberSaveable { mutableStateOf(false) }
    var saved by rememberSaveable { mutableStateOf(false) }

    val value = NameSettings(name, listOf(nickname, nicknameTwo), soundsLike)
    val problems = if (attempted) value.validate() else emptySet()
    val nameError = problems.firstNotNullOfOrNull { problem -> nameProblemTextRes(problem) }

    fun edited(update: () -> Unit) {
        update()
        saved = false
    }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = name,
            onValueChange = { edited { name = it } },
            label = { Text(stringResource(R.string.name_label)) },
            singleLine = true,
            isError = nameError != null,
            supportingText = if (nameError != null) {
                { Text(stringResource(nameError)) }
            } else {
                null
            },
            keyboardOptions = fieldOptions(ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = nickname,
            onValueChange = { edited { nickname = it } },
            label = { Text(stringResource(R.string.nickname_label)) },
            singleLine = true,
            keyboardOptions = fieldOptions(ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = nicknameTwo,
            onValueChange = { edited { nicknameTwo = it } },
            label = { Text(stringResource(R.string.nickname_two_label)) },
            singleLine = true,
            keyboardOptions = fieldOptions(ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = soundsLike,
            onValueChange = { edited { soundsLike = it } },
            label = { Text(stringResource(R.string.sounds_like_label)) },
            supportingText = { Text(stringResource(R.string.sounds_like_hint)) },
            singleLine = true,
            keyboardOptions = fieldOptions(ImeAction.Done),
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = {
                attempted = true
                if (value.isUsable) {
                    onSave(value.normalized())
                    saved = true
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(saveLabel)
        }
        if (showSavedNotice && saved) {
            Text(
                text = stringResource(R.string.name_saved),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/** Names are not words to correct: no auto-correct, capitalise each word. */
private fun fieldOptions(ime: ImeAction) = KeyboardOptions(
    capitalization = KeyboardCapitalization.Words,
    autoCorrectEnabled = false,
    imeAction = ime,
)

private fun nameProblemTextRes(problem: NameProblem): Int = when (problem) {
    NameProblem.NAME_EMPTY -> R.string.name_error_empty
    NameProblem.INVALID_CHARACTERS -> R.string.name_error_characters
    NameProblem.NAME_TOO_LONG,
    NameProblem.NICKNAME_TOO_LONG,
    NameProblem.SOUNDS_LIKE_TOO_LONG,
    -> R.string.name_error_length
    NameProblem.TOO_MANY_NICKNAMES -> R.string.name_error_nicknames
}
