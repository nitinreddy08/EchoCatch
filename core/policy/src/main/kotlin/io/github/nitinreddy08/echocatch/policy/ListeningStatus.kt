// SPDX-License-Identifier: GPL-3.0-only
package io.github.nitinreddy08.echocatch.policy

/** Why listening ended, so the status screen can say what happened (FR-9). */
enum class StopReason {
    /** The user pressed Stop in the app or in the notification. */
    USER,

    /** The microphone permission was withdrawn while listening. */
    PERMISSION_REVOKED,

    /** The microphone could not be opened, for example during a call or while another app holds it. */
    MICROPHONE_UNAVAILABLE,

    /** Reading the microphone failed after it had started. */
    CAPTURE_ERROR,

    /** Android refused to start the microphone service, for example because no screen of the app was visible (ADR-0004). */
    START_REFUSED,

    /** The app was running when the process ended without a stop request: Android ended it or the device restarted. */
    SYSTEM_STOPPED,
}

/**
 * The true state shown in the app and the notification (FR-9, FR-2).
 *
 * [micSilent] means the microphone is open but delivering only zeros, which Android does when another app has
 * priority for capture. It only has meaning while running.
 */
data class ListeningStatus(
    val state: ArmingState = ArmingState.DISARMED,
    val micSilent: Boolean = false,
    val lastStop: StopReason? = null,
) {
    val isRunning: Boolean get() = state != ArmingState.DISARMED

    /** Listening has started: clears the previous stop reason. */
    fun started(): ListeningStatus = ListeningStatus(state = ArmingState.LISTENING)

    /** Listening ended for [reason]. */
    fun stopped(reason: StopReason): ListeningStatus = ListeningStatus(state = ArmingState.DISARMED, lastStop = reason)

    /** Records whether the microphone is delivering only silence; ignored while not running. */
    fun withMicSilent(silent: Boolean): ListeningStatus = if (isRunning) copy(micSilent = silent) else this
}
