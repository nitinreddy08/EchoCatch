// SPDX-License-Identifier: GPL-3.0-only
package io.github.nitinreddy08.echocatch.model

/**
 * Where microphone audio comes from (ADR-0002, ADR-0009).
 *
 * [PHONE_MIC] is the universal Tier 0 baseline and the fallback for every other source.
 */
enum class SourceId {
    PHONE_MIC,
    BLUETOOTH_CLASSIC,
    BLE_AUDIO,
    WIRED,
    ;

    /** True for sources that depend on the headset and therefore must fall back to [PHONE_MIC] on failure. */
    val needsFallback: Boolean get() = this != PHONE_MIC
}
