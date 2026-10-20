# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Current state

The project is in the **planning phase**: the repository contains documentation only. There is no code, no build system, and no tests yet, so there are no build, lint, or test commands. Do not invent any. When M0-T2 and M0-T3 (Gradle and native skeletons) land, add the real commands here in the same PR, including how to run a single test (roadmap tasks M0-T2, M0-T3, M1-T7).

EchoCatch is a free, open-source, fully offline Android app. While the user listens to music on Bluetooth earphones, it listens to the environment and, when someone calls the user's name, ducks or pauses the media and plays an alert.

## Where things are

Start with `docs/README.md`, which indexes everything.

- `docs/plan/`: vision, requirements and budgets, platform facts, architecture, spikes, roadmap, test strategy, risks, agent workflow, open decisions.
- `docs/adr/`: Architecture Decision Records. An `Accepted` ADR is binding. To change one, write a superseding ADR first.
- `docs/spikes/results/`: evidence from hardware experiments (empty until M0 spikes run).

Read `docs/plan/08-ai-agent-workflow.md` before starting any task. Work is organised as roadmap tasks (`M0-T1` …) in `docs/plan/05-roadmap.md`, each sized for one PR.

## Architecture in brief

Kotlin shell (Compose UI, a foreground service, policy and alert control) around a **native C++17 pipeline with no Android dependencies** (`native/pipeline`) so that accuracy, latency, and CPU benchmarks run on a normal Linux host. Pipeline: audio source → ring buffer → VAD → keyword verifier → decision → alert via Android audio focus. Full detail and the planned module layout: `docs/plan/03-architecture.md`.

Three things need more than one file to understand:

- The microphone is a **pluggable source** (phone mic, Bluetooth classic, LE Audio, wired). The default is decided by a hardware spike, not assumed (ADR-0002).
- A long-lived foreground service stays in `ArmedIdle` with the **mic closed**, and opens it only when a headset is connected and media is playing (ADR-0004, `core/policy` state machine).
- Ducking is **cooperative**: Android does not let one app modify another's audio stream. The alert asks for audio focus (ADR-0005).

## Hard rules

- Never add the `INTERNET` permission or any networking, analytics, or crash-reporting library (ADR-0007). CI will check the permission list once M0-T5 lands.
- Never add a dependency, model file, dataset, or permission without a licence-checked entry in `docs/third-party.md` and the justification the plan requires. Exclude non-commercial licences (for example openWakeWord's pretrained models).
- Decide by measurement (ADR-0001). Numbers marked ⚠ in `docs/plan/01-requirements.md` are placeholders until the M0 spikes finish. Do not present them as measured.
- Keep the audio thread allocation-free and lock-free in the steady state. Never persist or log raw audio.
- The cloud sandbox has no phone or Bluetooth. Say clearly what was verified and what was only inferred. Hardware results come from the user via the protocol in `docs/plan/08-ai-agent-workflow.md`.
- Do not create a pull request unless the user asks.
