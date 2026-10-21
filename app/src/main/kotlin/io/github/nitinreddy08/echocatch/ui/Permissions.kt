// SPDX-License-Identifier: GPL-3.0-only
package io.github.nitinreddy08.echocatch.ui

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import io.github.nitinreddy08.echocatch.platform.Platform

/** What Android says about the two permissions right now; combined with what the app remembers by `PermissionStatus.resolve`. */
internal data class PermissionFacts(
    val microphoneGranted: Boolean,
    val microphoneRationale: Boolean,
    val notificationsGranted: Boolean,
    val notificationsRationale: Boolean,
)

internal fun readPermissionFacts(activity: Activity): PermissionFacts {
    fun granted(permission: String) =
        ContextCompat.checkSelfPermission(activity, permission) == PackageManager.PERMISSION_GRANTED

    fun rationale(permission: String) = ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)

    val notificationsApply = Platform.notificationPermissionApplies()
    return PermissionFacts(
        microphoneGranted = granted(Manifest.permission.RECORD_AUDIO),
        microphoneRationale = rationale(Manifest.permission.RECORD_AUDIO),
        notificationsGranted = !notificationsApply || granted(Manifest.permission.POST_NOTIFICATIONS),
        notificationsRationale = notificationsApply && rationale(Manifest.permission.POST_NOTIFICATIONS),
    )
}

internal fun Context.findActivity(): Activity {
    var current = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    error("The screen is not hosted by an activity")
}

/** Opens this app's page in Android settings, where permissions can be switched on after "Don't allow". */
internal fun openAppSettings(activity: Activity) {
    activity.startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", activity.packageName, null)),
    )
}

/**
 * Opens Android's battery optimisation list, where an app can be allowed to keep running in the background. This
 * only opens a settings screen; the app never asks Android to ignore battery optimisation itself (ADR-0011). Falls
 * back to the app's own settings page on phones that do not have that screen.
 */
internal fun openBatterySettings(activity: Activity) {
    try {
        activity.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
    } catch (e: ActivityNotFoundException) {
        openAppSettings(activity)
    }
}
