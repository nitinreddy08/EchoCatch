# ADR-0009: Support most Android versions and any non-Apple TWS through a universal baseline, capability tiers, and per-headset profiles

- **Status:** Accepted (minSdk confirmed at M1; tier details refined by S1, S2, S7)
- **Date:** 2026-10-20
- **Deciders:** project owner, with Claude as architect
- **Evidence:** platform facts P31–P36 in `docs/plan/02-platform-facts.md`; S1, S2, S5, S7 pending

## Context

The project owner wants EchoCatch to work on all Android phones, across most Android versions, with any TWS earbuds except Apple's.

We cannot test every phone and every earbud model. The variation sits in four places:
1. **OS behaviour by API level:** audio focus, foreground-service rules, Bluetooth APIs, concurrent-capture rules.
2. **OEM background limits:** vendors differ widely in how aggressively they kill services (P35).
3. **Earbud behaviour:** whether the mic opens, narrowband versus wideband HFP, mono mic selection, LE Audio support, firmware quirks (P16, P32).
4. **Device performance:** low-end phones have slower CPUs and less RAM.

Two facts make broad support achievable. The alert only needs ordinary audio output, which every Bluetooth audio device provides. And the phone's own mic works regardless of the earbuds. Only the headset-mic path varies.

## Options considered

1. **Support a curated list** of phones and earbuds. Easy to test, but contradicts the goal.
2. **Branch on device model** (known-good and known-bad lists in code). Brittle, and impossible to keep complete.
3. **Guarantee a universal baseline, detect capabilities at runtime, learn per headset, and publish an honest compatibility matrix.**

## Decision

Option 3, made of these parts:

1. **minSdk 26 (Android 8.0).** It provides `AudioFocusRequest` and automatic ducking (P7, verified for API 26+), notification channels, and playback callbacks. Going lower would need legacy focus code without automatic ducking. Changing the floor needs a superseding ADR. targetSdk is the current stable level, bumped yearly.
2. **Tier 0, the universal baseline:** phone mic capture, with the alert played through whatever output is active (any Bluetooth audio device, wired, or speaker). It must work on every supported API level, with any earbuds, independent of mic support.
3. **Capability tiers for the headset mic**, always falling back to Tier 0:
   - Tier 1: classic Bluetooth (HFP/SCO): legacy `startBluetoothSco` path below API 31, `setCommunicationDevice` from API 31 (P17).
   - Tier 2: LE Audio input via `setPreferredDevice` where phone, OS, and headset support it (P14, P15).
4. **Feature detection, never model sniffing.** No code branches on phone or earbud model names. Differences are expressed as capabilities and as data.
5. **Per-headset profiles:** a "Test my headset" wizard (FR-11) opens the mic route, records the resulting device type, sample rate and codec, checks that music keeps playing, and stores the preferred source locally, keyed by what Android reports about the device. This is how unknown earbuds are handled without a hard-coded list.
6. **API-level differences isolated** behind adapters in `core/audio` and `core/alert`. Each adapter is unit-tested with Robolectric at its SDK range and smoke-tested on emulator images across the supported levels.
7. **ABIs:** `arm64-v8a` and `armeabi-v7a` for devices, plus `x86_64` for emulators, so older and entry-level phones are covered.
8. **No Google Play Services dependency**, so the app also runs on devices without Google services. This follows from ADR-0007.
9. **Apple earbuds are out of scope** for testing and support. There is no Apple-specific code. They are ordinary Bluetooth devices and may well work.
10. **A published compatibility matrix** (`docs/compat/`, from M6) built from user device reports. "Tested" and "expected to work by capability" are kept separate from "known issues".

## Consequences

- The promise is honest and bounded: Tier 0 works everywhere we support; the headset-mic path works where the hardware allows and falls back cleanly where it does not. We do **not** promise headset-mic quality on every earbud model.
- The test matrix grows (API levels × tiers), so the emulator and Robolectric layers matter, and so does the community device-report process (S7).
- A low-end reference device is needed for the CPU budget (NFR-16), and the detector must meet it. This may favour smaller models.
- OEM-specific onboarding (per-vendor battery-manager steps) becomes a maintained document (M5-T2).
- We will know this was wrong if bug reports cluster around a small set of earbud chipsets or vendors that need special handling beyond a profile (that would justify a vendor-quirks data file, not code branches), or if API 26–28 devices prove too costly to keep working.
