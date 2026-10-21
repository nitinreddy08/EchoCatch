// SPDX-License-Identifier: GPL-3.0-only
package io.github.nitinreddy08.echocatch.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.nitinreddy08.echocatch.R
import io.github.nitinreddy08.echocatch.model.NameSettings
import io.github.nitinreddy08.echocatch.policy.SetupStep

/** One setup question at a time. [SetupStep] is decided by `SetupPlanner`; this only draws it. */
@Composable
internal fun OnboardingScreen(
    step: SetupStep,
    name: NameSettings,
    onAcceptDisclosure: () -> Unit,
    onRequestMicrophone: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onRequestNotifications: () -> Unit,
    onDeclineNotifications: () -> Unit,
    onSaveName: (NameSettings) -> Unit,
) {
    when (step) {
        SetupStep.DISCLOSURE -> DisclosurePage(onAcceptDisclosure)
        SetupStep.MICROPHONE -> MessagePage(
            title = stringResource(R.string.microphone_title),
            body = stringResource(R.string.microphone_body),
        ) {
            PrimaryButton(stringResource(R.string.microphone_button), onRequestMicrophone)
        }
        SetupStep.MICROPHONE_BLOCKED -> MessagePage(
            title = stringResource(R.string.microphone_blocked_title),
            body = stringResource(R.string.microphone_blocked_body),
        ) {
            PrimaryButton(stringResource(R.string.microphone_blocked_open_settings), onOpenAppSettings)
            OutlinedButton(onClick = onRequestMicrophone, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.microphone_blocked_try_again))
            }
        }
        SetupStep.NOTIFICATIONS -> MessagePage(
            title = stringResource(R.string.notifications_title),
            body = stringResource(R.string.notifications_body),
        ) {
            PrimaryButton(stringResource(R.string.notifications_allow), onRequestNotifications)
            TextButton(onClick = onDeclineNotifications, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.notifications_not_now))
            }
        }
        SetupStep.NAME, SetupStep.READY -> MessagePage(
            title = stringResource(R.string.name_title),
            body = stringResource(R.string.name_body),
        ) {
            NameForm(
                initial = name,
                saveLabel = stringResource(R.string.name_save),
                showSavedNotice = false,
                onSave = onSaveName,
            )
        }
    }
}

@Composable
private fun DisclosurePage(onAccept: () -> Unit) {
    Page {
        Text(stringResource(R.string.disclosure_title), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.disclosure_intro), style = MaterialTheme.typography.bodyLarge)
        Fact(R.string.disclosure_mic_heading, R.string.disclosure_mic_body)
        Fact(R.string.disclosure_private_heading, R.string.disclosure_private_body)
        Fact(R.string.disclosure_visible_heading, R.string.disclosure_visible_body)
        Fact(R.string.disclosure_early_heading, R.string.disclosure_early_body)
        PrimaryButton(stringResource(R.string.disclosure_button), onAccept)
    }
}

@Composable
private fun ColumnScope.Fact(@StringRes heading: Int, @StringRes body: Int) {
    Text(stringResource(heading), style = MaterialTheme.typography.titleMedium)
    Text(stringResource(body), style = MaterialTheme.typography.bodyLarge)
}

@Composable
private fun MessagePage(title: String, body: String, actions: @Composable ColumnScope.() -> Unit) {
    Page {
        Text(title, style = MaterialTheme.typography.headlineMedium)
        Text(body, style = MaterialTheme.typography.bodyLarge)
        actions()
    }
}

@Composable
internal fun PrimaryButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(onClick = onClick, modifier = modifier.fillMaxWidth()) {
        Text(label)
    }
}
