# Third-party components

Generated from `docs/third-party.json` by `python3 tools/ci/check_third_party.py --render`. Do not edit by hand.
Allowed licences are defined in [ADR-0010](adr/0010-license-gpl-3.0.md).

## In use

| Name | Version | Licence | Scope | Shipped | Purpose |
|---|---|---|---|---|---|
| [GoogleTest](https://github.com/google/googletest) | 1.15.2 | BSD-3-Clause | test | no | C++ unit tests for the native core (host only, not shipped in the app) |

## Evaluated and rejected

| Name | Licence | Reason |
|---|---|---|
| openWakeWord pretrained models | CC-BY-NC-SA-4.0 | Non-commercial and share-alike terms conflict with the project's licensing constraints (docs/plan/02-platform-facts.md, P28). The openWakeWord code may still be used to train our own models. |
| Picovoice Porcupine | proprietary | Requires an access key validated online; excluded by the offline and open-source constraints (P30, prior knowledge, not re-verified). |
| Snowboy | unknown | Discontinued (P30, prior knowledge, not re-verified). |
