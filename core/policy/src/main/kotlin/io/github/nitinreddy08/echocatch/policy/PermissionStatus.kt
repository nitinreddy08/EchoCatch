// SPDX-License-Identifier: GPL-3.0-only
package io.github.nitinreddy08.echocatch.policy

/**
 * Where a runtime permission stands, derived from the three facts Android exposes.
 *
 * Android never says "blocked" outright: a permission that is not granted, was asked for before, and gives no
 * rationale means the system will not show its dialog (the user chose "Don't allow" twice, or "Ask every
 * time" on Android 11 and newer). The UI then offers app settings, and also a retry because "Ask every time"
 * does show the dialog again.
 */
enum class PermissionStatus {
    GRANTED,

    /** Never asked in this install. */
    NOT_ASKED,

    /** Refused once; the system will still show its dialog, so explain first and ask again. */
    DENIED,

    /** Refused and the dialog is not expected to appear; only the app settings can change it. */
    BLOCKED,
    ;

    val isGranted: Boolean get() = this == GRANTED

    companion object {
        fun resolve(granted: Boolean, askedBefore: Boolean, shouldShowRationale: Boolean): PermissionStatus = when {
            granted -> GRANTED
            shouldShowRationale -> DENIED
            askedBefore -> BLOCKED
            else -> NOT_ASKED
        }
    }
}
