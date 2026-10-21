// SPDX-License-Identifier: GPL-3.0-only
package io.github.nitinreddy08.echocatch.policy

import io.github.nitinreddy08.echocatch.model.NameSettings

/** What the setup flow asks for next. */
enum class SetupStep {
    /** The plain-language explanation of what the microphone is used for (Play-compatible prominent disclosure). */
    DISCLOSURE,

    /** The system microphone dialog can be shown. */
    MICROPHONE,

    /** The microphone dialog will not appear; send the user to the app settings. */
    MICROPHONE_BLOCKED,

    /** The notification dialog can be shown (Android 13 and newer). Optional. */
    NOTIFICATIONS,

    /** The name to listen for (FR-1). */
    NAME,

    /** Nothing left to ask. */
    READY,
}

/** The facts the planner needs; the UI gathers them from the system and from stored settings. */
data class SetupInputs(
    val disclosureAccepted: Boolean,
    val microphone: PermissionStatus,
    /** False before Android 13, where notifications need no permission. */
    val notificationPermissionApplies: Boolean,
    val notifications: PermissionStatus,
    /** The user chose "Not now" for notifications. */
    val notificationsDeclined: Boolean,
    val name: NameSettings,
)

/**
 * Decides the next setup step from the current facts, so a revoked permission sends the user back to the right
 * screen and nothing is asked twice. Pure and table-tested.
 *
 * Order: disclosure, microphone, notifications, name. The disclosure always comes before the first system
 * dialog so the user knows why the microphone is wanted.
 */
object SetupPlanner {
    fun nextStep(inputs: SetupInputs): SetupStep = when {
        !inputs.disclosureAccepted -> SetupStep.DISCLOSURE
        inputs.microphone == PermissionStatus.BLOCKED -> SetupStep.MICROPHONE_BLOCKED
        !inputs.microphone.isGranted -> SetupStep.MICROPHONE
        wantsNotificationPrompt(inputs) -> SetupStep.NOTIFICATIONS
        !inputs.name.isUsable -> SetupStep.NAME
        else -> SetupStep.READY
    }

    /** Listening needs the disclosure and the microphone; everything else is optional for starting it. */
    fun canStartListening(inputs: SetupInputs): Boolean = inputs.disclosureAccepted && inputs.microphone.isGranted

    /**
     * True when the foreground-service notification may be hidden by the system: the permission applies and is
     * not granted. The service still runs and still appears in the system's task manager.
     */
    fun notificationMayBeHidden(inputs: SetupInputs): Boolean =
        inputs.notificationPermissionApplies && !inputs.notifications.isGranted

    private fun wantsNotificationPrompt(inputs: SetupInputs): Boolean =
        inputs.notificationPermissionApplies &&
            !inputs.notificationsDeclined &&
            (inputs.notifications == PermissionStatus.NOT_ASKED || inputs.notifications == PermissionStatus.DENIED)
}
