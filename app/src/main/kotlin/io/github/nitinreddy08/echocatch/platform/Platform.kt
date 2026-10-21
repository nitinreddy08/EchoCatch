// SPDX-License-Identifier: GPL-3.0-only
package io.github.nitinreddy08.echocatch.platform

import android.os.Build

/**
 * The only place in the app that reads `Build.VERSION.SDK_INT` (ADR-0009, M1-T8). Everything that differs by
 * Android version asks a question here, so supporting API 26 up to the newest release stays one reviewable file.
 * `tools/ci/check_sdk_checks.py` fails the build if an SDK check appears anywhere else.
 */
object Platform {
    /** Notifications need a runtime permission from Android 13 (API 33). */
    fun notificationPermissionApplies(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
}
