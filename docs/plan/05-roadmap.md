# 05 — Roadmap and work breakdown

Status: Draft v0.1 · 2026-10-20

Each task is sized for one pull request (roughly ≤ 400 changed lines) so an AI agent can pick it up with the task card from `08-ai-agent-workflow.md`. `HW` marks tasks that need the user's phone or earbuds. Task IDs are stable; reference them in commits and PRs.

## Status (2026-10-20)

| Task | State |
|---|---|
| M0-T1 repo hygiene | done |
| M0-T2 Gradle skeleton and CI | done (CI green: native, pure Kotlin and Android jobs; Android verified by GitHub Actions only) |
| M0-T3 native skeleton | done (host build, tests and sanitizers pass; the Android ABI build of the native core is wired in M1 through Gradle) |
| M0-T4 `docs/third-party.md` and licence checker | done |
| M0-T5 permission guard | done (runs in CI on both APKs) |
| M0-T6 spike shell app and JSON report | done (`:spikes`, "EchoCatch Lab") |
| M0-T7a S1 route test, T7c S3 ducking test, T7b S2 service lifecycle (with S5 coexistence logging) and T7f S6 battery sampling | done, awaiting hardware runs. S6 covers runs R0 to R2 only; R3 and R4 need the native pipeline (M2), and R5 (Oboe versus `AudioRecord`) needs native code (M1) |
| M0-T7d S4 detector benchmark | not started. It is host-side work belonging to M2 (corpus tooling and the bench CLI) and does not need a phone |
| M0-T8 hardware runs | waiting for the owner: `docs/spikes/runbook-oneplus-11r-buds-4.md` |
| M0-T9 ratify ADRs | blocked on M0-T8 and S4 |

## Milestone map

```
M0 Foundations & spikes ──► M1 Walking skeleton ──► M3 Alert + policy ──► M4 Bluetooth ──► M5 Hardening ──► M6 Release
        │                                              ▲
        └──────────► M2 Detection core & benchmark ────┘   (M2 runs in parallel with M1; host-only, no phone)
```

M2 depends only on M0-T3 (native skeleton) and not on any Android work, so it can start as soon as M0-T3 lands.

---

## M0 — Foundations and spikes

**Goal:** a buildable empty project, CI, and evidence-backed ADRs.

| ID | Task | Notes |
|---|---|---|
| M0-T1 | Repo hygiene: `CONTRIBUTING.md`, `CODE_OF_CONDUCT.md`, `.gitignore`, `.editorconfig`, issue and PR templates (PR template mirrors the Definition of Done). `LICENSE` (GPL-3.0) is already in place (ADR-0010); `CONTRIBUTING.md` states that contributions are accepted under GPL-3.0-only and that new files carry the SPDX header. | |
| M0-T2 | Gradle skeleton: multi-module empty project (minSdk 26) that runs `./gradlew assembleDebug`; version catalog; CI workflow for build, lint, unit tests; emulator smoke job across a matrix of API levels (26 up to current). | |
| M0-T3 | Native skeleton: `native/pipeline` + `tools/bench` build on host via CMake and for Android ABIs; GoogleTest wired; one trivial test in CI. | unlocks M2 |
| M0-T4 | `docs/third-party.md` and a licence-check script (every dependency and model file has a recorded licence). | |
| M0-T5 | CI guard: fail if built APK requests `INTERNET` or any non-approved permission. | ADR-0007 |
| M0-T6 | Spike shell app with "export report as JSON" via the share sheet. The report schema is stable and contains no audio, so volunteers with other phones and earbuds can run it (S1, S7). | |
| M0-T7a…f | Implement spikes S1–S6 (one task each, per `04-spikes.md`). | |
| M0-T8 `HW` | Run spikes on the user's devices; commit results to `docs/spikes/results/`. | user runs |
| M0-T9 | Ratify ADRs: flip `Proposed` to `Accepted`, fill numeric budgets in `01-requirements.md` and ADR-0001. | |

**Exit criteria:** CI green on an empty app; all spike results committed; ADR-0001…0008 are `Accepted` with numeric budgets; default audio source, FGS strategy, focus strategy, and detector pipeline decided.

---

## M1 — Walking skeleton

**Goal:** the thinnest end-to-end slice that proves the platform assumptions in a real app.

| ID | Task |
|---|---|
| M1-T1 | App shell: Compose navigation (Home, Settings, Onboarding stubs), theme, DataStore settings. |
| M1-T2 | Permission flow: `RECORD_AUDIO`, `POST_NOTIFICATIONS`; rationale screens; handle denied and "ask every time". |
| M1-T3 | `ListeningService` (FGS, type microphone), persistent notification with Stop action, started only from a visible UI or notification per ADR-0004. |
| M1-T4 | `AudioSource` interface and `PhoneMicSource`; frames flow into a stub sink; capture hop per ADR-0001 budgets. |
| M1-T5 | Status UI with the true state (FR-9) and a debug level meter. |
| M1-T6 | Instrumented smoke test on the emulator: service starts, runs, stops cleanly. |
| M1-T7 | Replace the placeholders in `CLAUDE.md` with the real build, test, and lint commands. |
| M1-T8 | API-level adapter pattern (ADR-0009): interfaces for Bluetooth route control, recording callbacks, and notification/permission handling, with one implementation per behaviour range. Robolectric tests run at the relevant SDK levels. A lint rule or test forbids direct `Build.VERSION.SDK_INT` checks outside adapters. |
| M1-T9 | Confirm minSdk 26 against the emulator matrix and the owner's devices. Record any API 26–28 problems as issues. |

**Exit criteria:** enable → persistent notification → mic frames flowing → Stop releases everything; 8 h idle run with no crash; CI green.

---

## M2 — Detection core and benchmark (host-first, parallel with M1)

**Goal:** a detector that meets the accuracy and CPU budgets, proven on the host.

| ID | Task |
|---|---|
| M2-T1 | `tools/corpus`: fetch and verify datasets with checksums; manifest records each licence; synthetic positive generation; SNR mixing. |
| M2-T2 | Native pipeline skeleton: frame types, ring buffer, resampler, stage interface, tests. |
| M2-T3 | VAD stage (Silero via ONNX Runtime, or the alternative S4 picks), with pre-roll and hangover; tests on labelled clips. |
| M2-T4 | Keyword stage wrapping sherpa-onnx KWS (candidate C1); keyword text → engine config per S4 findings. |
| M2-T5 | `tools/bench` CLI: run the pipeline over the corpus, emit JSON metrics (recall@SNR, FA/h, T_detect, duty cycle, CPU/audio-s). |
| M2-T6 | Candidate comparison report (S4) and ADR-0003 final. |
| M2-T7 | Optional Stage-2 prefilter, only if C1 after VAD fails a budget. |
| M2-T8 | Sensitivity calibration: ROC curve → sensitivity levels used by FR-6. |
| M2-T9 | JNI bridge and Kotlin `Detector` API with contract tests. |
| M2-T10 | Bench regression gate in CI (fails when metrics drop below the committed baseline). |

**Exit criteria:** host benchmark meets NFR-1, 3, 4, 5, 6 (as ratified in M0-T9); CPU budget confirmed on the user's phone (`HW`, small).

---

## M3 — Alert engine and policy

**Goal:** the product works end to end with the phone mic.

| ID | Task |
|---|---|
| M3-T1 | `CuePlayer`: pre-loaded PCM, pre-created low-latency `AudioTrack`; author the original cue sounds (CC0). |
| M3-T2 | `FocusController` per ADR-0005, with a fake `AudioManager` for tests. |
| M3-T3 | `ArmingPolicy` state machine (pure Kotlin) with exhaustive table tests; inputs from `AudioDeviceCallback` and `AudioPlaybackCallback`. |
| M3-T4 | Orchestration in `ListeningService`: policy ↔ source ↔ detector ↔ alert; cooldown; safe teardown on every error path. |
| M3-T5 | Settings: sensitivity, cooldown, cue, cue volume, arming policy. |
| M3-T6 | Name entry UI with validation (FR-1); spoken-sample enrollment only if ADR-0003 requires it. |
| M3-T7 `HW` | End-to-end test: say the name near the phone while music plays; record T_alert and a success rate. |

**Exit criteria:** demo works on the user's phone using the phone mic; T_alert within budget; media always restored after an alert in 100 trials.

---

## M4 — Bluetooth and routes

**Goal:** the headset mic path (or the fallback) behaves as S1 decided.

| ID | Task |
|---|---|
| M4-T1 | `RouteMonitor` over `AudioDeviceCallback`; `SourceSelector` driven by S1 policy data. |
| M4-T2 | `BluetoothMicSource` for classic (legacy `startBluetoothSco` below API 31, `setCommunicationDevice` from API 31) and LE Audio (`setPreferredDevice`), as S1 supports. Tier fallback chain: LE Audio → classic → phone mic. |
| M4-T3 | Prompt route teardown on idle so music quality is restored; test the release path. |
| M4-T4 | Fallback logic and clear user messaging (what source is active and why). |
| M4-T5 `HW` | Hardware matrix run with the user's earbuds. |
| M4-T6 | "Test my headset" wizard (FR-11) and `HeadsetProfile` storage; `SourceSelector` uses the profile. Device-report export (JSON, no audio). |
| M4-T7 `HW` | Run the wizard on every earbuds model the owner and volunteers can provide; add each to the S7 data set. |

**Exit criteria:** the user's earbuds work with the chosen default; music quality and earbud battery match the S1 expectations.

---

## M5 — Hardening and efficiency

**Goal:** meet every budget and survive real life.

| ID | Task |
|---|---|
| M5-T1 | Power tuning loop using the S6 protocol until NFR-9 and NFR-10 are met. |
| M5-T2 | Doze and OEM resilience: onboarding for battery optimization (`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` if justified), a maintained per-vendor guidance document (Xiaomi, Huawei, Oppo, Vivo, Samsung, OnePlus, others), restart paths per ADR-0004. Verify the vendor steps against real devices or reports, not memory (P35). |
| M5-T7 `HW` | Low-end device pass: run the benchmark and the S6 power protocol on a low-end reference device; tune against NFR-16. |
| M5-T3 | Coexistence (S5 results): calls, other recorders, silenced capture detection and recovery. |
| M5-T4 | Robustness: watchdog, process-death story, state recovery, notification when stopped unexpectedly. |
| M5-T5 | Accessibility (TalkBack, large text) and string resources ready for translation. |
| M5-T6 `HW` | 8 h and 24 h soak tests with an automated alert injection harness where possible. |

**Exit criteria:** all NFR budgets met or consciously revised by ADR; 24 h soak passes (NFR-14).

---

## M6 — Release

| ID | Task |
|---|---|
| M6-T1 | F-Droid metadata and a reproducible build. |
| M6-T2 | Release signing process and GitHub Releases workflow. |
| M6-T3 | README, user guide, privacy statement ("no data leaves the device"), and the no-guarantee disclaimer. |
| M6-T4 | Beta programme: issue templates, device-report template, a public known-issues list, and the `docs/compat/` compatibility matrix (tested versus user-reported, ADR-0009). |
| M6-T5 | v1.0 tag, once all release gates in `06-quality-and-testing.md` pass. |
| M6-T7 | Google Play readiness (ADR-0011), done after v1.0 on F-Droid and GitHub: re-verify current Play policy (foreground service declaration, battery-optimization rules, disclosure, privacy policy), prepare the Data safety answers, decide the signing-key and application-ID strategy across stores, and decide the contributor-licensing policy. Requires the owner. |

---

## Critical path and parallelism

- Critical path: M0 → (M1 ∥ M2) → M3 → M4 → M5 → M6.
- The longest unknowns are S1 and S2. They are first in M0, because they can change M1 and M4.
- Safe parallel agent work: M2 (host-only) alongside M1; `core/policy` alongside `core/alert` (separate modules, no shared files).
- Anything marked `HW` serialises on the user's availability. Batch hardware work into a few sessions, not many small ones.
