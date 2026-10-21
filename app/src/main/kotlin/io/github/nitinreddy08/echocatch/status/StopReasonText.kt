// SPDX-License-Identifier: GPL-3.0-only
package io.github.nitinreddy08.echocatch.status

import androidx.annotation.StringRes
import io.github.nitinreddy08.echocatch.R
import io.github.nitinreddy08.echocatch.policy.StopReason

/** The plain-language sentence for why listening ended, shown on the status screen and in the "stopped" notification. */
@StringRes
fun StopReason.messageRes(): Int = when (this) {
    StopReason.USER -> R.string.stop_user
    StopReason.PERMISSION_REVOKED -> R.string.stop_permission
    StopReason.MICROPHONE_UNAVAILABLE -> R.string.stop_unavailable
    StopReason.CAPTURE_ERROR -> R.string.stop_capture_error
    StopReason.START_REFUSED -> R.string.stop_start_refused
    StopReason.SYSTEM_STOPPED -> R.string.stop_system
}
