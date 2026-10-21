# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Current state

Milestone M0 (foundations and spikes) is waiting on hardware results, and milestone M1 (walking skeleton) has a first app: in-app setup (disclosure, microphone and notification permission, name), a microphone foreground service and a status screen with a level meter. The detection pipeline and the alert do not exist yet, so the app does not detect names, and its screens say so. Status per task is in `docs/plan/05-roadmap.md`.

EchoCatch is a free, open-source, fully offline Android app. While the user listens to music on Bluetooth earphones, it listens to the environment and, when someone calls the user's name, ducks or pauses the media and plays an alert.

## Commands

Native core, scripts and licence register (host build, runs anywhere):

```
cmake --preset host && cmake --build --preset host && ctest --preset host     # everything
ctest --preset host -R PipelineSkeleton                                        # one test by name
cmake --preset host-sanitize && cmake --build --preset host-sanitize && ctest --preset host-sanitize   # ASan + UBSan
python3 -I -m unittest discover -s tools/ci/tests                              # CI script tests (also run by ctest)
python3 -I -m unittest discover -s tools/ci/tests -k permissions               # one script's tests
python3 -I tools/ci/check_third_party.py [--render]                            # validate / regenerate docs/third-party.md
python3 -I tools/ci/check_permissions.py --profile app|spikes --manifest|--apk <file>
python3 -I tools/ci/check_sdk_checks.py app/src                                # SDK_INT only inside the platform/ adapter
```

Pure Kotlin modules (`core:model`, `core:policy`), Gradle 9.6 wrapper, no Android SDK needed:

```
./gradlew -Pechocatch.android=false :core:model:test :core:policy:test
./gradlew -Pechocatch.android=false :core:policy:test --tests '*ArmingStateTest'     # one test class
```

Android modules (`:app`, `:spikes`) need an Android SDK. `settings.gradle.kts` includes them only when `ANDROID_HOME`, `ANDROID_SDK_ROOT` or `local.properties` provides one (override with `-Pechocatch.android=true|false`):

```
./gradlew :app:assembleDebug :app:lintDebug :spikes:assembleDebug :spikes:lintDebug
```

Environment notes (cloud sandbox):
- The sandbox has no Android SDK and cannot reach `dl.google.com`, so Android modules are verified by GitHub Actions (`.github/workflows/ci.yml`). Read results with the GitHub `actions_list` and `get_job_logs` tools; request `tail_lines` of 300 or more to reach the Gradle error above the post-job output.
- Google Maven (`dl.google.com`, which serves AndroidX) is unreachable from the sandbox, so AndroidX versions and their `minCompileSdk` cannot be looked up here. Add a throwaway workflow that prints `maven-metadata.xml` and the `aar-metadata.properties` inside the AAR, push it, read its log, then delete it (this is how the Compose line was chosen; see commit b1c922a and the comment in `gradle/libs.versions.toml`). Compose 1.12 and `lifecycle-runtime-compose` 2.11 need compileSdk 37; the project is on 36.
- Maven Central intermittently answers HTTP 429 through the sandbox proxy. Gradle caches what it downloads, so re-run the build (a loop of a few attempts works) before suspecting the build files.
- Do not declare `org.jetbrains.kotlin.jvm` (or any Kotlin plugin) with `apply false` in the root build. It loads Kotlin in a parent classloader and AGP 9 then fails with `NoClassDefFoundError: BaseVariant`. Each module applies the plugins it needs.

## Where things are

Start with `docs/README.md`, which indexes everything.

- `docs/plan/`: vision, requirements and budgets, platform facts, architecture, spikes, roadmap, test strategy, risks, agent workflow, open decisions.
- `docs/adr/`: Architecture Decision Records. An `Accepted` ADR is binding. To change one, write a superseding ADR first.
- `docs/spikes/`: `runbook-oneplus-11r-buds-4.md` (how the owner runs the EchoCatch Lab app on their devices) and `results/` (evidence from hardware experiments, empty until they have been run).

Read `docs/plan/08-ai-agent-workflow.md` before starting any task. Work is organised as roadmap tasks (`M0-T1` …) in `docs/plan/05-roadmap.md`, each sized for one PR.

## Architecture in brief

Kotlin shell (Compose UI, a foreground service, policy and alert control) around a **native C++17 pipeline with no Android dependencies** (`native/pipeline`) so that accuracy, latency, and CPU benchmarks run on a normal Linux host. Pipeline: audio source → ring buffer → VAD → keyword verifier → decision → alert via Android audio focus. Full detail and the planned module layout: `docs/plan/03-architecture.md`.

Target reach: Android 8.0 (API 26) and newer, any non-Apple TWS earbuds (ADR-0009). The phone mic with the alert played through the active output is the universal baseline; headset-mic routes are capability-detected upgrades that fall back to it.

Three things need more than one file to understand:

- The microphone is a **pluggable source** (phone mic, Bluetooth classic, LE Audio, wired). The default is decided by a hardware spike, not assumed (ADR-0002).
- A long-lived foreground service stays in `ArmedIdle` with the **mic closed**, and opens it only when a headset is connected and media is playing (ADR-0004, `core/policy` state machine).
- Ducking is **cooperative**: Android does not let one app modify another's audio stream. The alert asks for audio focus (ADR-0005).

## Hard rules

- Never add the `INTERNET` permission or any networking, analytics, or crash-reporting library (ADR-0007). CI will check the permission list once M0-T5 lands.
- The project is GPL-3.0-only (ADR-0010). New source files start with `SPDX-License-Identifier: GPL-3.0-only`.
- Never add a dependency, model file, dataset, or permission without a licence-checked entry in `docs/third-party.md` and the justification the plan requires. The licence must be on the GPL-3.0-compatible allowlist in ADR-0010. Exclude non-commercial licences (for example openWakeWord's pretrained models).
- Distribution is F-Droid and GitHub Releases first, Google Play later (ADR-0011). Keep choices Play-compatible: user-initiated mic service with a visible notification and in-app disclosure, no reliance on `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`.
- Primary test devices: OnePlus 11R and OnePlus Buds 4. They are one data point, not proof of broad compatibility.
- Feature-detect; never branch on phone or earbud model names. Put API-level differences behind adapters, not scattered `SDK_INT` checks. No Google Play Services dependency. The phone-mic baseline must keep working on every supported API level (ADR-0009).
- Decide by measurement (ADR-0001). Numbers marked ⚠ in `docs/plan/01-requirements.md` are placeholders until the M0 spikes finish. Do not present them as measured.
- Keep the audio thread allocation-free and lock-free in the steady state. Never persist or log raw audio.
- The cloud sandbox has no phone or Bluetooth. Say clearly what was verified and what was only inferred. Hardware results come from the user via the protocol in `docs/plan/08-ai-agent-workflow.md`.
- Do not create a pull request unless the user asks.
