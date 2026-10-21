// SPDX-License-Identifier: GPL-3.0-only
package io.github.nitinreddy08.echocatch.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.nitinreddy08.echocatch.BuildConfig
import io.github.nitinreddy08.echocatch.R
import io.github.nitinreddy08.echocatch.model.NameSettings

@Composable
internal fun SettingsScreen(
    name: NameSettings,
    microphoneAllowed: Boolean,
    notificationsAllowed: Boolean,
    notificationPermissionApplies: Boolean,
    onSaveName: (NameSettings) -> Unit,
    onOpenAppSettings: () -> Unit,
    onOpenBatterySettings: () -> Unit,
    onBack: () -> Unit,
) {
    Page {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineMedium)
            TextButton(onClick = onBack) { Text(stringResource(R.string.settings_back)) }
        }

        Text(stringResource(R.string.settings_name_title), style = MaterialTheme.typography.titleMedium)
        NameForm(
            initial = name,
            saveLabel = stringResource(R.string.name_save_settings),
            showSavedNotice = true,
            onSave = onSaveName,
        )

        HorizontalDivider()
        Text(stringResource(R.string.settings_permissions_title), style = MaterialTheme.typography.titleMedium)
        PermissionRow(stringResource(R.string.settings_microphone), microphoneAllowed)
        if (notificationPermissionApplies) {
            PermissionRow(stringResource(R.string.settings_notifications), notificationsAllowed)
        }
        OutlinedButton(onClick = onOpenAppSettings, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.settings_open_app_settings))
        }

        HorizontalDivider()
        Text(stringResource(R.string.settings_keep_title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.settings_keep_body), style = MaterialTheme.typography.bodyMedium)
        OutlinedButton(onClick = onOpenBatterySettings, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.settings_open_battery_settings))
        }

        HorizontalDivider()
        Text(stringResource(R.string.settings_privacy_title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.settings_privacy_body), style = MaterialTheme.typography.bodyMedium)

        HorizontalDivider()
        Text(stringResource(R.string.settings_about_title), style = MaterialTheme.typography.titleMedium)
        Text(
            text = stringResource(R.string.settings_about_body, BuildConfig.VERSION_NAME),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun PermissionRow(label: String, allowed: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Text(
            text = stringResource(if (allowed) R.string.permission_allowed else R.string.permission_not_allowed),
            style = MaterialTheme.typography.bodyLarge,
            color = if (allowed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
        )
    }
}
