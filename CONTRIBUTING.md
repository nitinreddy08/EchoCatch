# Contributing to EchoCatch

EchoCatch is free software under [GPL-3.0-only](LICENSE). Thank you for helping.

## Before you start

- Read [docs/README.md](docs/README.md). The plan and the Architecture Decision Records (ADRs) explain *why* things are the way they are.
- An **Accepted** ADR is binding. To change a decision, propose a new ADR that supersedes it before writing code.
- Work is organised as tasks (`M0-T1`, `M3-T2`, …) in [docs/plan/05-roadmap.md](docs/plan/05-roadmap.md). Each task is sized for one pull request.

## Licence of contributions

By submitting a contribution you agree that it is licensed under GPL-3.0-only, the same as the project. New source files start with:

```
SPDX-License-Identifier: GPL-3.0-only
```

If you add code from elsewhere, say where it comes from in the pull request. It must be compatible with GPL-3.0 (see the allowlist in [ADR-0010](docs/adr/0010-license-gpl-3.0.md)).

## Hard rules for every change

- **No `INTERNET` permission** and no networking, analytics, or crash-reporting libraries ([ADR-0007](docs/adr/0007-no-network-permission.md)).
- **No new dependency, model file, dataset, or permission** without an entry in `docs/third-party.md` that records its licence, source, and the reason.
- **Never store or log raw audio.**
- **Do not branch on phone or earbud model names.** Use capability detection and profiles ([ADR-0009](docs/adr/0009-broad-device-compatibility.md)).
- **Claims need evidence.** Performance and battery numbers come from the benchmark or the measurement protocol, not from guesses. Say what was measured and on which device.

## Pull requests

- One task per pull request, small enough to review in one sitting.
- Describe what changed, how you verified it, which ADRs and budgets it touches. The pull request template lists the Definition of Done.
- CI must be green. Update docs and ADRs in the same pull request as the change that needs them.

## Reporting compatibility results

Phone and earbuds reports are very welcome. Use the "Device report" issue template. Reports never contain audio.

## Conduct

Be kind and constructive. See [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md).
