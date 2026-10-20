// SPDX-License-Identifier: GPL-3.0-only
package io.github.nitinreddy08.echocatch.lab

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.json.JSONObject

/** Spike S2: after a reboot, record whether a microphone service can be started from BOOT_COMPLETED (expected: no). */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        EventLog.append(context, "boot_completed")
        try {
            context.startForegroundService(LabService.startIntent(context, LabService.MODE_CYCLE))
            EventLog.append(context, "boot_start_requested")
        } catch (e: Exception) {
            EventLog.append(context, "boot_start_failed", JSONObject().put("exception", e.javaClass.simpleName).put("message", e.message ?: ""))
        }
    }
}
