// SPDX-License-Identifier: GPL-3.0-only
package io.github.nitinreddy08.echocatch

import android.app.Application
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import io.github.nitinreddy08.echocatch.policy.StopReason
import io.github.nitinreddy08.echocatch.settings.SettingsRepository
import io.github.nitinreddy08.echocatch.status.ListeningStatusStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** Manual constructor injection: the few long-lived objects, created once per process (docs/plan/03-architecture.md). */
class AppGraph(application: Application) {
    val settings = SettingsRepository(application.settingsDataStore)
    val status = ListeningStatusStore()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Runs [block] for as long as the process lives, so a write started by the service survives the service. */
    fun launch(block: suspend CoroutineScope.() -> Unit) {
        scope.launch(block = block)
    }

    /**
     * Called once at process start. If the last run ended without a stop request, say so instead of showing a calm
     * "off": Android ended the service, or the phone restarted (ADR-0004 says a process death needs a manual re-enable).
     */
    fun recoverFromUnexpectedStop() {
        scope.launch {
            if (settings.current().listeningWanted && !status.status.value.isRunning) {
                status.update { it.stopped(StopReason.SYSTEM_STOPPED) }
                settings.setListeningWanted(false)
            }
        }
    }
}
