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

## Constraints

- 100 % free and open source. All dependencies and model files must be under permissive or otherwise redistributable licences, recorded in `docs/third-party.md` (created in M0).
- Offline: no `INTERNET` permission, ever (ADR-0007).
- Permission budget (each needs written justification): `RECORD_AUDIO`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MICROPHONE`, `POST_NOTIFICATIONS` (API 33+). Possible, pending spikes: `MODIFY_AUDIO_SETTINGS`, `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`. Avoided unless a spike proves they are needed: `BLUETOOTH_CONNECT`, `READ_PHONE_STATE`.
- minSdk and targetSdk: see `09-open-decisions.md`.
