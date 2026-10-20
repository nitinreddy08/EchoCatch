# 01 — Requirements

Status: Draft v0.1 · 2026-10-20

Numbers marked **⚠** are placeholders until the M0 spikes produce baselines (ADR-0001). They are targets to aim at, not claims about what is achievable.

## Functional requirements

| ID | Requirement |
|---|---|
| FR-1 | The user enters their name (and up to two nicknames) as text. Optional spoken samples may be added if the detector ADR calls for them. |
| FR-2 | The user can enable and disable listening from the app UI and from the persistent notification (Stop action). Other start surfaces (Quick Settings tile, widget) depend on spike S2. |
| FR-3 | Arming policy, user-selectable: **(a)** always while enabled, **(b)** only while a headset is connected, **(c)** only while a headset is connected *and* audio is playing. Default: (c), pending S2. |
| FR-4 | On detection, the app requests transient audio focus, plays a short pre-recorded cue, then releases focus so the media resumes (ADR-0005, ADR-0008). |
| FR-5 | After an alert, a cooldown (default 8 s ⚠, user-adjustable) suppresses repeat alerts. |
| FR-6 | A sensitivity setting maps to calibrated thresholds from the benchmark ROC (not an arbitrary slider). |
| FR-7 | Settings: audio source preference (auto/phone/headset), cue sound, cue volume, cooldown, arming policy. |
| FR-8 | Onboarding explains each permission, the persistent notification, and the per-OEM battery-optimization steps. |
| FR-9 | The notification and the status screen always show the true state: Disarmed, Armed-idle, Listening, Alerting. |
| FR-10 | Local diagnostics: counters only (detections, alerts, source in use). No audio is recorded or exported in v1. |
| FR-11 | A "Test my headset" wizard (about 30 s) opens the headset mic route and records which device type, sample rate, and codec result, and whether music keeps playing. It saves a per-headset profile locally (preferred source) and recommends a source. It can export a device report (JSON the user shares manually, with no audio) for the compatibility matrix. The phone mic is always the fallback. |

## Non-functional requirements and budgets

Definitions (all measured by tools in `06-quality-and-testing.md`):

- **T_detect:** time from the end of the spoken name (ground-truth annotation) to the detector emitting TRIGGER. Measured on the host benchmark and on device.
- **T_alert:** time from TRIGGER to the first cue sample leaving the app, measured on device with timestamps.
- **T_ear:** T_alert plus route output latency (Bluetooth adds its own delay, often 100–250 ms, which we cannot remove). Reported for information, not gated.
- **Recall@SNR:** fraction of annotated name utterances detected, mixed with noise at the given signal-to-noise ratio.
- **FA/h:** false alarms per hour on the negative corpus (speech without the name, TV, music, street, café).
- **Duty cycle:** fraction of audio time during which any stage beyond VAD runs.

| ID | Budget | Target | Status |
|---|---|---|---|
| NFR-1 | T_detect p95 | ≤ 400 ms | ⚠ |
| NFR-2 | T_alert p95 | ≤ 150 ms | ⚠ |
| NFR-3 | Recall at 10 dB SNR | ≥ 90 % | ⚠ |
| NFR-4 | Recall at 0 dB SNR | ≥ 70 % | ⚠ |
| NFR-5 | False alarms | ≤ 1 per hour | ⚠ (tighten later) |
| NFR-6 | Duty cycle, mixed-environment corpus | ≤ 25 % | ⚠ |
| NFR-7 | Capture wake-ups | ≤ 10 per second (hop ≥ 100 ms) | ⚠ |
| NFR-8 | CPU time per audio-second while Listening | set after S4 | TBD |
| NFR-9 | Battery: Armed-idle overhead | not measurable above noise floor | ⚠ |
| NFR-10 | Battery: Listening overhead (mAh/h vs baseline) | set after S6 | TBD |
| NFR-11 | Earbud battery impact of the headset-mic route | measured in S1; may decide the default source | TBD |
| NFR-12 | Resident memory, Listening | < 100 MB | ⚠ |
| NFR-13 | APK size | < 30 MB incl. models | ⚠ |
| NFR-14 | Alert reliability after 24 h soak (service alive, alert fires) | 100 % of soak runs | ⚠ |
| NFR-15 | Phone-mic mode works on every tested API level from 26 up | 100 % of the emulator and device matrix | ⚠ |
| NFR-16 | CPU time per audio-second on a low-end reference device | within NFR-8, scaled for that device; set after S4 | TBD |
| NFR-17 | Headset-mic route on any tested non-Apple earbuds either works or falls back cleanly to the phone mic | 0 unrecovered failures; every fallback logged | ⚠ |

## Constraints

- 100 % free and open source, licensed GPL-3.0-only (ADR-0010). All dependencies and model files must be under licences compatible with GPL-3.0 (allowlist in ADR-0010) and recorded in `docs/third-party.md` (created in M0).
- Offline: no `INTERNET` permission, ever (ADR-0007).
- Permission budget (each needs written justification): `RECORD_AUDIO`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MICROPHONE`, `POST_NOTIFICATIONS` (API 33+). Possible, pending spikes: `MODIFY_AUDIO_SETTINGS`, `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`. Avoided unless a spike proves they are needed: `BLUETOOTH_CONNECT`, `READ_PHONE_STATE`.
- Compatibility goals below (ADR-0009). minSdk is 26; targetSdk is the current stable level, bumped yearly.

## Compatibility requirements (ADR-0009)

| ID | Requirement |
|---|---|
| CR-1 | Support Android 8.0 (API 26) and newer. |
| CR-2 | **Universal baseline (Tier 0):** phone-mic capture with the alert played through the active output works on every supported API level, with any earbuds, whether or not their mic is usable. |
| CR-3 | Any Bluetooth audio device can receive the alert, because that needs only ordinary audio output. The headset-mic path is an optional, capability-detected upgrade with automatic fallback to Tier 0. |
| CR-4 | Non-Apple TWS earbuds are the target. Apple earbuds are out of scope for testing and support. There is no Apple-specific code, and they may work as generic Bluetooth devices. |
| CR-5 | Entry-level phones are supported: the CPU and memory budgets are checked on a low-end reference device (NFR-16). |
| CR-6 | No dependency on Google Play Services, so the app runs on devices without them. |
| CR-7 | Differences between devices are handled by runtime capability detection and per-headset profiles. Code never branches on phone or earbud model names. |
| CR-8 | ABIs: `arm64-v8a` and `armeabi-v7a`, plus `x86_64` for emulators. |
| CR-9 | A public compatibility matrix lists tested devices, devices expected to work by capability, and known issues. Be honest about what is tested versus inferred. |
