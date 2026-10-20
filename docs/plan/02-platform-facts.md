# 02 — Platform facts and their verification status

Status: Draft v0.1 · 2026-10-20

Every design decision that rests on an Android or library behaviour is listed here with how well it is established. Tags:

- **[VERIFIED]** read in official documentation or the project's own repository during planning (source linked).
- **[UNVERIFIED]** found only in secondary sources, or not found. A spike must confirm it.
- **[PRIOR]** from general knowledge, not re-checked during planning. Treat as a hypothesis.

## Foreground service and microphone

| # | Fact | Tag | Source |
|---|---|---|---|
| P1 | A `microphone` foreground service needs `FOREGROUND_SERVICE_MICROPHONE` plus runtime `RECORD_AUDIO`. | VERIFIED | https://developer.android.com/develop/background-work/services/fgs/service-types |
| P2 | On Android 14+ (targeting 14+), creating a foreground service of type microphone while the app is in the background throws `SecurityException`. The service must be started while the app has a visible activity, unless an exemption applies. | VERIFIED | https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start |
| P3 | Exemptions for while-in-use access listed by the docs: a notification interaction, app widgets, a system component, a `VoiceInteractionService` app, and a few device-policy cases. Quick Settings tiles are **not** listed. | VERIFIED (tile status: UNVERIFIED) | same as P2 |
| P4 | Microphone foreground services cannot be started from a `BOOT_COMPLETED` receiver (with few exceptions). EchoCatch therefore cannot auto-start after reboot without a user tap. | VERIFIED | same as P2 and service-types |
| P5 | A running microphone foreground service that was started legitimately lets us open and close `AudioRecord` later without a visible activity. This underpins "FGS stays alive, mic opens only when needed" (ADR-0004). | **UNVERIFIED — spike S2** | none |
| P6 | Disabling battery optimization exempts an app from background-start restrictions, but the while-in-use microphone rule (P2) still applies. | VERIFIED (restriction list) | same as P2 |

## Audio focus and ducking

| # | Fact | Tag | Source |
|---|---|---|---|
| P7 | Android 8.0+ ducks automatically when another app requests `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK`, **unless** the playing app is playing `CONTENT_TYPE_SPEECH` or set `setWillPauseWhenDucked(true)`. | VERIFIED | https://developer.android.com/media/optimize/audio-focus |
| P8 | Speech players (podcasts, audiobooks) are expected to pause, not duck. Whether a given podcast app does anything on a duck request is app-specific. | VERIFIED (principle); per-app behaviour: spike S3 | same |
| P9 | On Android 12+, the system fades out a `USAGE_MEDIA`/`USAGE_GAME` player when another app requests full `AUDIOFOCUS_GAIN` (not the transient-duck request). | VERIFIED | same |
| P10 | The best `AudioAttributes` usage/content type for a short spoken alert (e.g. `USAGE_ASSISTANCE_NAVIGATION_GUIDANCE`, `CONTENT_TYPE_SPEECH`) is not stated by that docs page. | **UNVERIFIED — spike S3** | none |
| P11 | An app cannot modify another app's audio stream without system privileges. Ducking is cooperative only. | PRIOR | — |
| P12 | `AudioManager.getActivePlaybackConfigurations()` can tell us whether media is playing (and its attributes), which supports the "audio is playing" arming policy and choosing pause vs duck. | PRIOR — spike S2/S3 | — |

## Bluetooth microphone

| # | Fact | Tag | Source |
|---|---|---|---|
| P13 | A2DP is one-way (output only). Using a classic Bluetooth microphone needs the HFP/SCO link, which is narrowband (about 8 kHz mono, per vendor docs) and lowers audio quality. | VERIFIED (via Android BLE Audio docs and vendor notes) | https://developer.android.com/develop/connectivity/bluetooth/ble-audio/audio-recording |
| P14 | With LE Audio, Android documents input sampling up to 32 kHz, and the recommended approach is `AudioRecord` with `setPreferredDevice` for the BLE input device. | VERIFIED | same |
| P15 | Many LE Audio headsets are dual-mode and may fall back to classic transport. | VERIFIED | same |
| P16 | Whether the user's specific phone and earbuds support LE Audio input, and what happens to music while the mic route is open. | **UNVERIFIED — spike S1** (needs user hardware) | none |
| P17 | `setCommunicationDevice` (API 31+) is the modern replacement for `startBluetoothSco`, and the permissions it needs. | PRIOR — spike S1 | — |
| P18 | Opening the headset mic costs battery in the earbuds as well as the phone. | PRIOR — spike S1 | — |

## Capture path

| # | Fact | Tag | Source |
|---|---|---|---|
| P19 | Oboe `PerformanceMode::PowerSaving` "uses larger internal buffers and a data path that trades off latency for lower power". The default input preset is `VoiceRecognition`. `setDeviceId` is honoured on AAudio (API 28+) but not OpenSL ES. | VERIFIED | https://github.com/google/oboe/blob/main/docs/FullGuide.md |
| P20 | Whether power-saving input streams actually reduce power for *capture* on real devices. The Oboe guide gives no input-specific power data. | **UNVERIFIED — spike S6** | none |
| P21 | Another app capturing audio can silence our capture on Android 10+ (concurrent-capture rules), e.g. during a call. | PRIOR — spike S5 | — |
| P22 | Third-party apps cannot use the phone's always-on hotword DSP (SoundTrigger); it is reserved for the system assistant. The `VoiceInteractionService` exemption in P3 applies only to the default assistant. | PRIOR | — |

## Libraries and models

| # | Fact | Tag | Source |
|---|---|---|---|
| P23 | Silero VAD is MIT-licensed, about 2 MB, processes a 30 ms+ chunk in under 1 ms on one CPU thread, and supports 8 and 16 kHz. Android is not explicitly documented. | VERIFIED (repo); Android use: spike S4 | https://github.com/snakers4/silero-vad |
| P24 | sherpa-onnx is Apache-2.0, supports keyword spotting, lists Android and a Kotlin API, and publishes pre-trained KWS models at https://github.com/k2-fsa/sherpa-onnx/releases/tag/kws-models. | VERIFIED | https://github.com/k2-fsa/sherpa-onnx |
| P25 | The English sherpa KWS model (`zipformer-gigaspeech-3.3M`) is English-only. | UNVERIFIED (secondary source) | https://docs.m5stack.com/en/stackflow/models/kws |
| P26 | Whether a sherpa KWS `keywords_file` accepts arbitrary text directly, or needs keywords pre-tokenized into the model's BPE pieces. The official docs page was not reachable from the planning sandbox. | **UNVERIFIED — spike S4** | none |
| P27 | The licences of the individual sherpa KWS model files. | **UNVERIFIED — check before bundling** | none |
| P28 | openWakeWord's official pretrained models are CC BY-NC-SA 4.0 (non-commercial). **Excluded** by the "no commercial licensing restrictions" constraint. The openWakeWord *code* is reported as Apache-2.0 (third-party listing), so training our own models remains possible. | VERIFIED (model licence) | https://huggingface.co/davidscripka/openwakeword/raw/main/README.md |
| P29 | microWakeWord is a training framework for tiny streaming wake-word models, using synthetic speech. Its licence was not confirmed. | UNVERIFIED | https://esphome.io/components/micro_wake_word.html |
| P30 | Picovoice Porcupine needs an access key checked online, and Snowboy is discontinued. Both are excluded. | PRIOR | — |

## Test players (all open source, so the spikes are reproducible)

VLC, AntennaPod (podcasts), NewPipe (video), plus the user's real players (Spotify, YouTube, YouTube Music) for the S3 matrix.
