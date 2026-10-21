# Third-party components

Generated from `docs/third-party.json` by `python3 tools/ci/check_third_party.py --render`. Do not edit by hand.
Allowed licences are defined in [ADR-0010](adr/0010-license-gpl-3.0.md).

## In use

| Name | Version | Licence | Scope | Shipped | Purpose |
|---|---|---|---|---|---|
| [AndroidX Activity Compose](https://developer.android.com/jetpack/androidx/releases/activity) | 1.13.0 | Apache-2.0 | runtime | yes | Hosts the Compose UI, edge-to-edge drawing, back handling and the permission request launcher. |
| [AndroidX Core](https://developer.android.com/jetpack/androidx/releases/core) | 1.18.0 | Apache-2.0 | runtime | yes | ServiceCompat, NotificationCompat, ContextCompat and ActivityCompat: version differences for the foreground service, notifications and permission checks. |
| [AndroidX DataStore Preferences](https://developer.android.com/jetpack/androidx/releases/datastore) | 1.2.1 | Apache-2.0 | runtime | yes | Stores the user's name and setup progress on the device (Settings default in docs/plan/03-architecture.md). |
| [AndroidX Lifecycle Runtime Compose](https://developer.android.com/jetpack/androidx/releases/lifecycle) | 2.10.0 | Apache-2.0 | runtime | yes | Collects the listening status only while a screen is visible, so the level meter does not run in the background. |
| [GoogleTest](https://github.com/google/googletest) | 1.15.2 | BSD-3-Clause | test | no | C++ unit tests for the native core (host only, not shipped in the app) |
| [jellyfish](https://github.com/jamesturk/jellyfish) | 1.3.0 | MIT | test | no | Metaphone keys for phonetic name matching in the S4 benchmark tools; development only |
| [Jetpack Compose Material 3 (BOM 2026.06.01)](https://developer.android.com/jetpack/androidx/releases/compose-material3) | 1.4.0 | Apache-2.0 | runtime | yes | Buttons, text fields, cards and theming for the screens. |
| [Jetpack Compose UI, Foundation and Runtime (BOM 2026.06.01)](https://developer.android.com/jetpack/androidx/releases/compose) | 1.11.4 | Apache-2.0 | runtime | yes | Screens of the app (onboarding, home, settings). Versions are pinned through the Compose BOM; checked 2026-10-21 that the Maven POM and the AAR metadata need compileSdk 35 or lower. |
| [Kokoro multi-lang v1.0 (sherpa-onnx build)](https://github.com/k2-fsa/sherpa-onnx/releases/tag/tts-models) | 1.0 | Apache-2.0 | model | no | Synthetic voices that generate the benchmark's test audio; never shipped |
| [Kotlin standard library and kotlinx.coroutines (transitive)](https://github.com/JetBrains/kotlin) | Kotlin 2.4.21; coroutines resolved by Gradle | Apache-2.0 | runtime | yes | Pulled in by the AndroidX libraries above and by the Kotlin compiler; control-plane coroutines and the settings flow. |
| [numpy](https://numpy.org) | 2.5.4 | BSD-3-Clause | test | no | Python benchmark tools (tools/corpus, tools/s4); development only |
| [Project Gutenberg texts (NLTK data packaging)](https://github.com/nltk/nltk_data) | 2026-10-21 | LicenseRef-PublicDomain | data | no | Public-domain book sentences spoken by the synthetic talkers in benchmark scenes |
| [python-soundfile](https://github.com/bastibe/python-soundfile) | 0.14.0 | BSD-3-Clause | test | no | WAV reading and writing in the benchmark tools; development only (uses libsndfile, LGPL-2.1) |
| [SentencePiece](https://github.com/google/sentencepiece) | 0.2.2 | Apache-2.0 | test | no | Tokenises typed names into the keyword model's BPE pieces in the benchmark tools |
| [sherpa-onnx (Python)](https://github.com/k2-fsa/sherpa-onnx) | 1.13.8 | Apache-2.0 | test | no | Keyword-spotting engine and text-to-speech runtime for the S4 screening |
| [sherpa-onnx KWS zipformer gigaspeech 3.3M](https://github.com/k2-fsa/sherpa-onnx/releases/tag/kws-models) | 2024-01-01 | Apache-2.0 | model | no | Candidate C1 keyword-spotting model, screening only. Model card says Apache-2.0; the terms of its GigaSpeech training data are unverified, so it must be checked before it is bundled in the app |
| [sherpa-onnx streaming zipformer en 20M](https://github.com/k2-fsa/sherpa-onnx/releases/tag/asr-models) | 2023-02-17 | Apache-2.0 | model | no | Candidate C4: small streaming recogniser with hotword boosting, screening only. Model card says Apache-2.0 and links an icefall LibriSpeech model |
| [sherpa-onnx streaming zipformer en 70M](https://github.com/k2-fsa/sherpa-onnx/releases/tag/asr-models) | 2023-06-26 | Apache-2.0 | model | no | Candidate C4: larger streaming recogniser with hotword boosting, screening only; also provides the BPE model. Model card says Apache-2.0 and links an icefall LibriSpeech model |
| [Silero VAD](https://github.com/snakers4/silero-vad) | 6.2.3 | MIT | model | no | Voice-activity model for the duty-cycle measurement; a candidate stage 1 of the pipeline |

## Evaluated and rejected

| Name | Licence | Reason |
|---|---|---|
| openWakeWord pretrained models | CC-BY-NC-SA-4.0 | Non-commercial and share-alike terms conflict with the project's licensing constraints (docs/plan/02-platform-facts.md, P28). The openWakeWord code may still be used to train our own models. |
| Picovoice Porcupine | proprietary | Requires an access key validated online; excluded by the offline and open-source constraints (P30, prior knowledge, not re-verified). |
| Snowboy | unknown | Discontinued (P30, prior knowledge, not re-verified). |
