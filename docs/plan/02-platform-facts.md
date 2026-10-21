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
| P21 | On Android 10+, two ordinary apps can never capture audio at the same time. An app with a visible UI wins over a background app, privacy-sensitive sources (`CAMCORDER`, `VOICE_COMMUNICATION`) win over others, and the later starter wins among equals. An active call (`MODE_IN_CALL` / `MODE_IN_COMMUNICATION`) always receives audio. Our background capture will therefore be silenced in those cases. | VERIFIED | https://developer.android.com/media/platform/sharing-audio-input |
| P21a | We can detect silencing with `AudioManager.AudioRecordingCallback` (register before capture starts) and `AudioRecordingConfiguration.isClientSilenced()`. Android 11 added `setPrivacySensitive(true)`; we must **not** set it, so that the system assistant is not blocked. | VERIFIED | same |
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

## API-level behaviour map (compatibility, ADR-0009)

| # | Fact | Tag | Source |
|---|---|---|---|
| P31 | API 26 (Android 8.0): `AudioFocusRequest` and automatic ducking exist. | VERIFIED | audio-focus docs (P7) |
| P32 | API 26–29: foreground services need no `microphone` type attribute; the type attribute and while-in-use mic limits arrive with Android 10/11, and Android 11 added background-start limits for mic access. The exact boundaries per level are not confirmed. | PRIOR — spike S2 on several API levels | — |
| P33 | API 29 (Android 10): concurrent-capture rules begin (P21). Before that, capture was limited to the foreground app or a foreground service. | VERIFIED | https://developer.android.com/media/platform/sharing-audio-input |
| P34 | API 31 (Android 12): foreground services cannot be started from the background without an exemption; `setCommunicationDevice` appears. API 34 (Android 14): every foreground service must declare a type and the matching permission. | VERIFIED (31 background start, 34 type declaration); `setCommunicationDevice` level PRIOR | service-types and restrictions-bg-start pages (P1, P2) |
| P35 | OEM background-process killing varies widely by vendor (Xiaomi, Huawei, Oppo, Vivo, Samsung, OnePlus and others are known for it). The vendor-specific user steps change often. | PRIOR (dontkillmyapp.com could not be reached from the planning sandbox) | — |
| P36 | HFP can be wideband (mSBC, 16 kHz) or narrowband (CVSD, 8 kHz) depending on the phone and earbuds, and some earbuds expose only one mic or switch mic roles. LE Audio availability depends on Android version, phone, and headset. | PRIOR — spikes S1 and S7 | — |

## Verified on a real device (OnePlus 11R, Android 16, OnePlus Buds 4; first run 2026-10-21)

Evidence: [first-run results](../spikes/results/2026-10-21-first-run-oneplus-11r-buds-4.md). One phone and one pair of earbuds only.

| # | Fact | Tag |
|---|---|---|
| P41 | The Bluetooth call link (SCO) input exposed by this phone is mono, 16-bit, 8 and 16 kHz only. This confirms P13 and P36 for this pair. | VERIFIED on device |
| P42 | `AudioRecord.setPreferredDevice(call-link input)` is accepted and `getRoutedDevice()` reports the call link, but all samples are zero while the audio mode stays NORMAL and no communication device is set. **A reported route is not proof of audio; check the signal.** Whether the call-route method (`setCommunicationDevice` with call mode) delivers real audio is untested. | VERIFIED on device (silence); route method UNTESTED |
| P43 | With that silent route, `isMusicActive` stayed true before, during and after; the effect of opening a real call link on music is still unknown. | VERIFIED (silent route only) |
| P44 | `getActivePlaybackConfigurations()` shows other apps' players with usage and content type (here usage 1, content type 0) and shows our own alert player (usage 12, content type 1). | VERIFIED on device (resolves P12) |
| P45 | Audio focus `GAIN_TRANSIENT_MAY_DUCK` with navigation-guidance and speech attributes was granted in 5 to 7 ms and, for one media player of unknown content type (the Brave browser playing YouTube in the background), the tester heard ducking in one run and a hard-to-hear alert in the other. | VERIFIED once; not enough to decide |
| P46 | No LE Audio device was listed for this pair, so LE Audio is not in use with the OnePlus 11R and Buds 4 as connected. | VERIFIED on device |
| P47 | The call link reports the phone's name as its product name, not the earbuds'. Product names cannot identify a headset. | VERIFIED on device |
| P48 | Opening the phone microphone with `AudioRecord` at 16 kHz returns data in about 0.2 s while music plays through the earbuds. | VERIFIED on device |

## Detector and development environment (S4 screening, 2026-10-21)

Evidence: [S4 first screening](../spikes/results/S4-first-screening-2026-10-21.md). Synthetic scenes only.

| # | Fact | Tag |
|---|---|---|
| P49 | sherpa-onnx's keyword spotter takes **arbitrary typed text**: the text is split into the model's BPE pieces (SentencePiece, using the model's `bpe.model`) and written one keyword per line. Every target name tokenised into pieces the model knows. This resolves P26. | VERIFIED |
| P50 | The English keyword model (`kws-zipformer-gigaspeech-3.3M`) runs at about 0.02 times real time on one host CPU thread and detects its own test keywords. | VERIFIED (host) |
| P51 | With typed spellings the spotter's recall depends heavily on the name: 0.87 for Daniel and 0.72 for Alex, but about 0 for Alexandra, Nguyen, Siobhan, Venkatesh and Xiomara in the synthetic benchmark. | VERIFIED (synthetic data) |
| P52 | Small LibriSpeech-trained streaming recognisers (20M and 70M parameters) write rare names by sound ("SOPHIA", "PRIA", "CHIVONNE", "WHEN" for Nguyen), so exact-spelling matching misses them. Their host cost is about 0.03 (20M) and 0.08 (70M) times real time. | VERIFIED (synthetic data) |
| P53 | A recogniser's transcript matched by Metaphone key finds many more names but also raises false alarms sharply for short names and common words. Telling the matcher how the name sounds (respelled aliases) lifted recall for hard names from about 0 to 0.5 to 0.7. | VERIFIED (synthetic data) |
| P54 | The models' training-data terms: the keyword model card says Apache-2.0 and the model was trained on GigaSpeech XL, whose data terms could not be verified; the two recognisers link to icefall LibriSpeech models (LibriSpeech is CC BY 4.0). This narrows P27 but does not close it. | PARTLY VERIFIED |
| P55 | In the development sandbox: GitHub release downloads, `raw.githubusercontent.com`, PyPI, npm and the Ubuntu archive are reachable. OpenSLR, Zenodo, Hugging Face, `dl.google.com` and `support.google.com` are not. Real speech and noise corpora therefore could not be fetched. | VERIFIED (2026-10-21) |

## Distribution (ADR-0011)

| # | Fact | Tag | Source |
|---|---|---|---|
| P37 | Google Play requires declarations for foreground service types for apps targeting Android 14+, which may include a justification or demonstration for the microphone type. | UNVERIFIED (the Play policy page could not be fetched from the planning sandbox) | — |
| P38 | Google Play restricts use of the `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` permission to specific use cases. Sending the user to the system battery settings screen does not need that permission. | UNVERIFIED | — |
| P39 | Google Play expects prominent in-app disclosure and consent before sensitive data access such as microphone recording, plus a privacy policy. | UNVERIFIED | — |
| P40 | F-Droid builds from source, rejects proprietary components, and signs with its own key unless the build is reproducible. Play re-signs with a Play-managed key. | PRIOR | — |

Re-verify P37–P40 against current official policy in M6-T7 before any submission.

## Test players (all open source, so the spikes are reproducible)

VLC, AntennaPod (podcasts), NewPipe (video), plus the user's real players (Spotify, YouTube, YouTube Music) for the S3 matrix.
