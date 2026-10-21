# Third-party components

Generated from `docs/third-party.json` by `python3 tools/ci/check_third_party.py --render`. Do not edit by hand.
Allowed licences are defined in [ADR-0010](adr/0010-license-gpl-3.0.md).

## In use

| Name | Version | Licence | Scope | Shipped | Purpose |
|---|---|---|---|---|---|
| [GoogleTest](https://github.com/google/googletest) | 1.15.2 | BSD-3-Clause | test | no | C++ unit tests for the native core (host only, not shipped in the app) |
| [Kokoro multi-lang v1.0 (sherpa-onnx build)](https://github.com/k2-fsa/sherpa-onnx/releases/tag/tts-models) | 1.0 | Apache-2.0 | model | no | Synthetic voices that generate the benchmark's test audio; never shipped |
| [numpy](https://numpy.org) | 2.5.4 | BSD-3-Clause | test | no | Python benchmark tools (tools/corpus, tools/s4); development only |
| [Project Gutenberg texts (NLTK data packaging)](https://github.com/nltk/nltk_data) | 2026-10-21 | LicenseRef-PublicDomain | data | no | Public-domain book sentences spoken by the synthetic talkers in benchmark scenes |
| [python-soundfile](https://github.com/bastibe/python-soundfile) | 0.14.0 | BSD-3-Clause | test | no | WAV reading and writing in the benchmark tools; development only (uses libsndfile, LGPL-2.1) |
| [SentencePiece](https://github.com/google/sentencepiece) | 0.2.2 | Apache-2.0 | test | no | Tokenises typed names into the keyword model's BPE pieces in the benchmark tools |
| [sherpa-onnx (Python)](https://github.com/k2-fsa/sherpa-onnx) | 1.13.8 | Apache-2.0 | test | no | Keyword-spotting engine and text-to-speech runtime for the S4 screening |
| [sherpa-onnx KWS zipformer gigaspeech 3.3M](https://github.com/k2-fsa/sherpa-onnx/releases/tag/kws-models) | 2024-01-01 | Apache-2.0 | model | no | Candidate C1 keyword-spotting model, screening only. Model card says Apache-2.0; the terms of its GigaSpeech training data are unverified, so it must be checked before it is bundled in the app |
| [Silero VAD](https://github.com/snakers4/silero-vad) | 6.2.3 | MIT | model | no | Voice-activity model for the duty-cycle measurement; a candidate stage 1 of the pipeline |

## Evaluated and rejected

| Name | Licence | Reason |
|---|---|---|
| openWakeWord pretrained models | CC-BY-NC-SA-4.0 | Non-commercial and share-alike terms conflict with the project's licensing constraints (docs/plan/02-platform-facts.md, P28). The openWakeWord code may still be used to train our own models. |
| Picovoice Porcupine | proprietary | Requires an access key validated online; excluded by the offline and open-source constraints (P30, prior knowledge, not re-verified). |
| Snowboy | unknown | Discontinued (P30, prior knowledge, not re-verified). |
