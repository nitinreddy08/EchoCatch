// SPDX-License-Identifier: GPL-3.0-only
package io.github.nitinreddy08.echocatch.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nitinreddy08.echocatch.AppGraph
import io.github.nitinreddy08.echocatch.platform.Platform
import io.github.nitinreddy08.echocatch.policy.PermissionStatus
import io.github.nitinreddy08.echocatch.policy.SetupInputs
import io.github.nitinreddy08.echocatch.policy.SetupPlanner
import io.github.nitinreddy08.echocatch.policy.SetupStep
import io.github.nitinreddy08.echocatch.service.ListeningService
import io.github.nitinreddy08.echocatch.settings.AppSettings

private enum class Destination { HOME, SETTINGS }

/**
 * Decides what is on screen: the setup questions until nothing is left to ask (`SetupPlanner`), then Home and
 * Settings. Because the step is recomputed from live facts, a permission switched off later in Android's settings
 * brings the right question back by itself.
 */
@Composable
fun AppRoot(graph: AppGraph) {
    val settings by graph.settings.settings.collectAsStateWithLifecycle<AppSettings?>(initialValue = null)
    val loaded = settings
    if (loaded == null) {
        Surface(modifier = Modifier.fillMaxSize()) { Box(Modifier.fillMaxSize()) }
    } else {
        AppContent(graph, loaded)
    }
}

@Composable
private fun AppContent(graph: AppGraph, settings: AppSettings) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }

    // Android only says what is granted now; re-read on every return to the app (for example from app settings).
    var facts by remember { mutableStateOf(readPermissionFacts(activity)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { facts = readPermissionFacts(activity) }

    val microphoneLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        graph.launch { graph.settings.markMicrophoneAsked() }
        facts = readPermissionFacts(activity)
    }
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        graph.launch { graph.settings.markNotificationsAsked() }
        facts = readPermissionFacts(activity)
    }

    val inputs = SetupInputs(
        disclosureAccepted = settings.disclosureAccepted,
        microphone = PermissionStatus.resolve(facts.microphoneGranted, settings.microphoneAsked, facts.microphoneRationale),
        notificationPermissionApplies = Platform.notificationPermissionApplies(),
        notifications = PermissionStatus.resolve(facts.notificationsGranted, settings.notificationsAsked, facts.notificationsRationale),
        notificationsDeclined = settings.notificationsDeclined,
        name = settings.name,
    )
    val step = SetupPlanner.nextStep(inputs)

    var destination by rememberSaveable { mutableStateOf(Destination.HOME) }
    BackHandler(enabled = step == SetupStep.READY && destination == Destination.SETTINGS) {
        destination = Destination.HOME
    }

    if (step != SetupStep.READY) {
        OnboardingScreen(
            step = step,
            name = settings.name,
            onAcceptDisclosure = { graph.launch { graph.settings.acceptDisclosure() } },
            onRequestMicrophone = { microphoneLauncher.launch(android.Manifest.permission.RECORD_AUDIO) },
            onOpenAppSettings = { openAppSettings(activity) },
            onRequestNotifications = { notificationLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS) },
            onDeclineNotifications = { graph.launch { graph.settings.declineNotifications() } },
            onSaveName = { graph.launch { graph.settings.saveName(it) } },
        )
    } else if (destination == Destination.SETTINGS) {
        SettingsScreen(
            name = settings.name,
            microphoneAllowed = facts.microphoneGranted,
            notificationsAllowed = facts.notificationsGranted,
            notificationPermissionApplies = inputs.notificationPermissionApplies,
            onSaveName = { graph.launch { graph.settings.saveName(it) } },
            onOpenAppSettings = { openAppSettings(activity) },
            onBack = { destination = Destination.HOME },
        )
    } else {
        val status by graph.status.status.collectAsStateWithLifecycle()
        val startListening: () -> Unit = {
            if (SetupPlanner.canStartListening(inputs)) {
                ContextCompat.startForegroundService(context, ListeningService.startIntent(context))
            }
        }
        val stopListening: () -> Unit = { context.startService(ListeningService.stopIntent(context)) }
        HomeScreen(
            name = settings.name,
            store = graph.status,
            status = status,
            notificationMayBeHidden = SetupPlanner.notificationMayBeHidden(inputs),
            onStart = startListening,
            onStop = stopListening,
            onOpenSettings = { destination = Destination.SETTINGS },
        )
    }
}
