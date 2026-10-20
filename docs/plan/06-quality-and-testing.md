# 06 — Quality and testing

Status: Draft v0.1 · 2026-10-20

## Principle

If a claim can be measured, a test or tool measures it, and CI or a release gate enforces it. Anything that needs hardware is batched into planned `HW` sessions with a written protocol.

## Test layers

| Layer | What | Where it runs |
|---|---|---|
| Native unit tests | Ring buffer, resampler, VAD windows, decision logic (GoogleTest) | CI and sandbox |
| Detector benchmark | Recall@SNR, FA/h, T_detect, duty cycle, CPU per audio-second over the corpus (`tools/bench`) | CI (smoke subset) and sandbox (full) |
| Kotlin unit tests | `ArmingPolicy` table tests, `FocusController` with a fake `AudioManager`, settings, event queue | CI and sandbox |
| JNI contract tests | Kotlin ↔ native boundary: types, lifetimes, threading | CI (emulator or host JVM) |
| Instrumented tests | Service start/stop, permission flows, notification actions | CI emulator |
| Hardware sessions (`HW`) | Bluetooth routes, real players, ducking, battery, OEM behaviour, soak | the user's devices |

The emulator cannot test Bluetooth or real players, so those results are only ever produced in `HW` sessions.

## Detector benchmark

- **Corpus** (`tools/corpus`): large audio is never committed. Scripts fetch datasets and verify checksums. A small licensed smoke set lives in the repo for CI. The manifest records the licence of every source. Only datasets that allow our use are included.
- **Positives:** recorded and synthetic utterances of the test names, mixed at 20, 10, 5, and 0 dB SNR into environment noise.
- **Negatives:** hours of speech without the name, TV, music, street, and café sounds, plus near-miss names (similar-sounding words).
- **Metrics:** recall at each SNR, FA/h, T_detect (p50, p95), duty cycle, CPU time per audio-second.
- **Regression gate:** `bench` results are compared with a committed baseline file. A change that worsens a gated metric by more than the allowed margin fails CI. Updating the baseline requires a commit that explains why.
- **No real people's voices in the repo** without explicit consent and a licence record.

## Battery and performance measurement

The S6 protocol is the single method used from M0 onward (see `04-spikes.md`). Rules:
- Compare against the same baseline scenario on the same device.
- Record charge level, temperature, and brightness (screen off), and repeat runs.
- Record the measurement noise floor, and never set a budget below it.
- Keep a results table in `docs/spikes/results/` and add a row for each tuning change in M5.

## Static checks and hygiene (CI)

- Android Lint, detekt, ktlint, clang-format, clang-tidy.
- **Permission guard:** build the APK, dump permissions, and fail on `INTERNET` or any permission outside the approved list (ADR-0007).
- **Licence guard:** every dependency and model file must appear in `docs/third-party.md` with a licence on the GPL-3.0-compatible allowlist in ADR-0010. New source files must carry `SPDX-License-Identifier: GPL-3.0-only`.
- **No proprietary blobs:** no Google Play Services, Firebase, or closed-source SDKs (this also keeps F-Droid acceptance possible).
- Reproducible build check for release builds (M6-T1).

## Compatibility testing (ADR-0009)

We cannot own every phone and earbud, so compatibility is tested in layers and reported honestly.

| Layer | Covers | Limit |
|---|---|---|
| Robolectric at several SDK levels | API-level adapters and tier selection logic | Not real audio or Bluetooth |
| Emulator matrix (API 26 up to current) in CI | Service start/stop, permissions, notification flow, crash-free startup | No Bluetooth, no real players |
| Owner's devices (`HW`) | Real audio, ducking, battery, OEM behaviour | One or a few devices |
| Low-end reference device (`HW`) | CPU and memory budgets (NFR-16) | One device class |
| Volunteer device reports (S7, from M6) | Wide spread of phones and earbuds | Self-reported, so labelled as such |

Rules:
- The Tier 0 baseline (phone mic plus alert out of the active output) is tested on **every** level in the matrix.
- For headset tiers, the test is that the route either works or falls back cleanly (NFR-17), not that quality is identical.
- Entries in the public compatibility matrix are labelled *tested* or *reported by a user*, with date and app version.
- A compatibility bug that needs special handling becomes a profile or a vendor-quirks data entry, not a branch on a model name.

## Release gates (v1.0 needs all of these)

1. All NFR budgets met, or revised by an accepted ADR with the reason.
2. Bench regression gate green on the full corpus.
3. 24 h soak passes on at least one reference device (NFR-14).
4. Media is restored after an alert in 100 out of 100 trials per tested player.
5. Permission and licence guards green.
6. S1–S6 results are committed and the ADRs are consistent with them.
7. README states the no-guarantee disclaimer and the privacy statement.
8. Compatibility: Tier 0 passes on every API level in the matrix (NFR-15); the headset route works or falls back cleanly on every earbuds model tested (NFR-17); the CPU budget holds on the low-end reference device (NFR-16); the compatibility matrix exists and states what is tested.

## Definition of Done (per task)

- Acceptance criteria in the task card are met and demonstrated (test, benchmark output, or hardware note).
- Tests added or updated at the right layer; CI green.
- No new dependency, permission, or model file without a recorded licence and justification.
- Docs updated if behaviour, commands, or architecture changed (including `CLAUDE.md` when commands change).
- If a budget or architectural decision is touched, the ADR is updated in the same PR.
