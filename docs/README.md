# EchoCatch documentation

Project phase: **planning** (no product code yet). Date of this index: 2026-10-20.

## Plan (`docs/plan/`)

| File | What it answers |
|---|---|
| [00-vision-and-principles](plan/00-vision-and-principles.md) | What are we building, what are we not, and which principles break ties |
| [01-requirements](plan/01-requirements.md) | Functional requirements, performance budgets, constraints |
| [02-platform-facts](plan/02-platform-facts.md) | Android and library facts, with how well each is verified |
| [03-architecture](plan/03-architecture.md) | Modules, pipeline, state machine, threading, alert path |
| [04-spikes](plan/04-spikes.md) | The M0 experiments (S1–S6) plus the ongoing compatibility survey (S7) |
| [05-roadmap](plan/05-roadmap.md) | Milestones M0–M6 and task-level work breakdown |
| [06-quality-and-testing](plan/06-quality-and-testing.md) | Test layers, benchmark, battery protocol, release gates, Definition of Done |
| [07-risks](plan/07-risks.md) | Risk register |
| [08-ai-agent-workflow](plan/08-ai-agent-workflow.md) | How AI agents work in this repo: task cards, gates, hardware loop |
| [09-open-decisions](plan/09-open-decisions.md) | What still needs the user or hardware |

## Decisions (`docs/adr/`)

| ADR | Title | Status |
|---|---|---|
| [0001](adr/0001-measure-first-and-performance-budgets.md) | Decide by measurement and enforce performance budgets | Accepted (budgets provisional) |
| [0002](adr/0002-pluggable-audio-source.md) | Pluggable audio source; default chosen by measurement | Accepted |
| [0003](adr/0003-detection-cascade-and-engine-selection.md) | VAD-gated detection; engine chosen by benchmark | Accepted |
| [0004](adr/0004-foreground-service-lifecycle.md) | Lightweight foreground service, mic opened only when needed | Proposed |
| [0005](adr/0005-alert-via-audio-focus.md) | Duck or pause through audio focus | Proposed |
| [0006](adr/0006-native-core-and-kotlin-shell.md) | Native C++ core under a thin Kotlin shell | Accepted |
| [0007](adr/0007-no-network-permission.md) | No `INTERNET` permission, CI-enforced permission list | Accepted |
| [0008](adr/0008-prerecorded-alert-cue.md) | Pre-recorded alert cue, not text-to-speech | Accepted |
| [0009](adr/0009-broad-device-compatibility.md) | Broad device compatibility: universal baseline, capability tiers, per-headset profiles, minSdk 26 | Accepted |
| [0010](adr/0010-license-gpl-3.0.md) | License the project under GPL-3.0-only | Accepted |
| [0011](adr/0011-distribution-fdroid-github-then-play.md) | F-Droid and GitHub first; stay Google-Play-compatible for later | Accepted |
| [0012](adr/0012-consented-test-clips-in-the-lab-app.md) | Record consented test clips in the Lab app only | Accepted |

New ADRs start from [0000-template](adr/0000-template.md).

## Spike results (`docs/spikes/results/`)

Empty until M0 spikes run. Format: see the template at the end of [04-spikes](plan/04-spikes.md).

## Where to start

1. Answer the remaining user-owned items in [09-open-decisions](plan/09-open-decisions.md) (devices, names; the licence is decided).
2. Execute M0 in [05-roadmap](plan/05-roadmap.md).
