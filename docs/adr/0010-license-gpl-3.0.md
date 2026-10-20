# ADR-0010: License the project under GPL-3.0-only

- **Status:** Accepted
- **Date:** 2026-10-20
- **Deciders:** project owner
- **Evidence:** the owner's choice ("GPL 3.0"); platform facts P23, P24 for dependency licences

## Context

EchoCatch is meant to be completely free and open source, and it listens through the microphone, so users benefit from being able to inspect it. Without a licence, nobody may legally reuse the code. The choice was between a permissive licence (Apache-2.0) and a copyleft licence (GPL-3.0).

## Options considered

1. **Apache-2.0:** maximum reuse, including inside closed-source commercial apps.
2. **GPL-3.0:** anyone may use and modify it, but a distributed modified version must also be released with its source under GPL. The project cannot be taken closed-source.

## Decision

GPL-3.0, using the identifier **GPL-3.0-only** (exactly what the owner chose). The full text is in `LICENSE` at the repository root. Moving to "GPL-3.0-or-later" later is possible for code we own, but it is a separate decision and must be made explicitly.

Rules that follow:
- **New source files** carry the header `SPDX-License-Identifier: GPL-3.0-only`.
- **Dependency allowlist** (must be compatible with GPL-3.0): Apache-2.0, MIT, BSD-2/3-Clause, ISC, MPL-2.0, LGPL-2.1-or-later and LGPL-3.0, GPL-3.0. Anything else (GPL-2.0-only, proprietary, non-commercial such as CC BY-NC, unknown) needs an explicit decision and an ADR before use.
- **Models and datasets** follow the same rule. Prefer CC0 and CC BY 4.0. Share-alike and non-commercial terms need a decision first.
- Our own assets (for example the alert cues, ADR-0008) are authored by the project and released under CC0 or GPL-3.0-only, recorded in `docs/third-party.md` or the asset's metadata.
- Contributions are accepted under the same licence. `CONTRIBUTING.md` (M0-T1) says so.

## Consequences

- The planned libraries fit: sherpa-onnx is Apache-2.0 and Silero VAD is MIT (P23, P24). Apache-2.0 and MIT code can be included in a GPL-3.0 project.
- Companies cannot ship a closed-source fork. That is the intent.
- F-Droid accepts GPL-3.0 apps, so distribution plans are unaffected.
- We will know this was wrong if a needed dependency or model carries a licence that is incompatible with GPL-3.0, which would force a different choice of dependency (not a change of project licence) unless the owner decides otherwise.
