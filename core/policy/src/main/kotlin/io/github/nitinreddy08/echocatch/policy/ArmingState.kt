// SPDX-License-Identifier: GPL-3.0-only
package io.github.nitinreddy08.echocatch.policy

/**
 * States of the arming policy (docs/plan/03-architecture.md section 6, ADR-0004).
 *
 * The transitions and events are added in milestone M3 (task M3-T3). The one invariant fixed now is that the
 * microphone stays closed until the policy decides it is useful.
 */
enum class ArmingState(val micOpen: Boolean) {
    /** No service, no notification. */
    DISARMED(micOpen = false),

    /** Foreground service alive, microphone closed; only cheap device and playback listeners run. */
    ARMED_IDLE(micOpen = false),

    /** Microphone route open and the detection pipeline running. */
    LISTENING(micOpen = true),

    /** Audio focus held while the alert cue plays; capture continues. */
    ALERTING(micOpen = true),

    /** Capture continues but triggers are ignored until the cooldown ends. */
    COOLDOWN(micOpen = true),
}
