// SPDX-License-Identifier: GPL-3.0-only
package io.github.nitinreddy08.echocatch.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nitinreddy08.echocatch.R
import io.github.nitinreddy08.echocatch.model.NameSettings
import io.github.nitinreddy08.echocatch.policy.ListeningStatus
import io.github.nitinreddy08.echocatch.status.ListeningStatusStore
import io.github.nitinreddy08.echocatch.status.messageRes
import kotlin.math.roundToInt

/** The status screen (FR-9): the true state in words, the start/stop control and a debug level meter (M1-T5). */
@Composable
internal fun HomeScreen(
    name: NameSettings,
    store: ListeningStatusStore,
    status: ListeningStatus,
    notificationMayBeHidden: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Page {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
            TextButton(onClick = onOpenSettings) { Text(stringResource(R.string.home_settings)) }
        }
        Text(
            text = stringResource(R.string.home_name_line, name.name),
            style = MaterialTheme.typography.bodyLarge,
        )
        StatusCard(status)
        if (status.isRunning) {
            LevelCard(store)
            OutlinedButton(onClick = onStop, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.home_stop))
            }
        } else {
            Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.home_start))
            }
        }
        if (notificationMayBeHidden) {
            Text(
                text = stringResource(R.string.home_notification_hidden),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
        NoticeCard()
    }
}

@Composable
private fun StatusCard(status: ListeningStatus) {
    val lastStop = status.lastStop
    val warning = status.isRunning && status.micSilent
    val detail = when {
        warning -> stringResource(R.string.home_silent_warning)
        status.isRunning -> stringResource(R.string.home_hint_on)
        lastStop != null -> stringResource(lastStop.messageRes())
        else -> stringResource(R.string.home_hint_off)
    }
    val container = when {
        warning -> MaterialTheme.colorScheme.errorContainer
        status.isRunning -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.secondaryContainer
    }
    val content = when {
        warning -> MaterialTheme.colorScheme.onErrorContainer
        status.isRunning -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSecondaryContainer
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = container, contentColor = content),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(if (status.isRunning) R.string.home_status_on else R.string.home_status_off),
                style = MaterialTheme.typography.titleLarge,
            )
            Text(text = detail, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** Collects the level here, not in the screen root, so only this card redraws ten times a second. */
@Composable
private fun LevelCard(store: ListeningStatusStore) {
    val level by store.level.collectAsStateWithLifecycle()
    val fraction = ((level.rmsDb + METER_FLOOR_DB) / METER_FLOOR_DB).coerceIn(0f, 1f)
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.home_level_title), style = MaterialTheme.typography.titleMedium)
            LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
            Text(
                text = stringResource(R.string.home_level_format, level.rmsDb.roundToInt(), level.peakDb.roundToInt()),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun NoticeCard() {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.home_notice_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.home_notice_body), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** The meter shows the range from this level up to full scale (0 dB). */
private const val METER_FLOOR_DB = 60f
