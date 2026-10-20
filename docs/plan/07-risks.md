# 07 — Risk register

Status: Draft v0.1 · 2026-10-20 · L = likelihood, I = impact (H/M/L). Review at the end of every milestone.

| ID | Risk | L | I | Mitigation | Early signal / owner |
|---|---|---|---|---|---|
| R1 | Headset-mic route degrades music or drains earbuds, so the core "headset mic" idea is weak | H | H | S1 first; pluggable source (ADR-0002); phone mic as the default if S1 says so | S1 result |
| R2 | Android 14+ blocks starting or restarting the mic service from the background, so persistence feels fragile | H | M | S2 decides start surfaces; keep the service alive in ArmedIdle; one-tap re-enable; honest onboarding | S2 result |
| R3 | OEM battery managers kill the service | H | H | Onboarding guidance, optional battery-optimization exemption, watchdog and "stopped" notification, soak tests on the user's device | M5 soak |
| R4 | Podcast and other speech apps ignore or mishandle focus, so no ducking | M | H | S3 matrix; choose pause-type focus for speech content; document known-ignoring apps; volume-duck fallback off by default | S3 result |
| R5 | Too many false alarms (annoying) or too many misses (useless) | M | H | Benchmark with near-miss negatives; calibrated sensitivity; cooldown; regression gate | M2 bench |
| R6 | The English KWS model handles the user's name or accent poorly | M | H | Test names include the user's real name and varied phonetics in S4; fallback C3 or spoken-sample enrollment | S4 result |
| R7 | Model or dataset licences do not meet "no commercial restrictions" | M | H | Licence record for every artifact (`third-party.md`); exclude openWakeWord pretrained models; check sherpa model licences before bundling | M0-T4 |
| R8 | Hardware work blocks progress because the cloud sandbox has no phone | H | M | Host-testable native core; batch `HW` sessions; spike app exports reports; clear protocols | ongoing |
| R9 | Battery budget unreachable with the main-CPU approach (no DSP access, P22) | M | H | Duty-cycle gating, power-saving capture, hop tuning, ArmedIdle; budgets set from S6 baseline, not guessed | S6 result |
| R10 | Bluetooth output latency makes the alert late | M | M | Measure T_ear; pre-warm the `AudioTrack`; cue is short and instant; communicate expectations | S3 result |
| R11 | Calls or other recorders break or silence capture | M | M | S5; never interfere with calls; backoff and recovery | S5 result |
| R12 | Privacy perception: an always-on mic app is distrusted | M | M | No `INTERNET` permission, CI-verified; no audio persistence; open source; plain privacy statement | M6 |
| R13 | Safety expectations (user relies on it near traffic) | L | H | Clear disclaimer in README and onboarding; never advertise as a safety device | M6-T3 |
| R14 | Android version fragmentation (API 26 and up) behaves differently | H | M | minSdk 26 (ADR-0009); API-level adapters; Robolectric at several SDK levels; emulator matrix; S2 and S5 repeated on other Android versions | M1-T8, M1-T9 |
| R15 | Dependency or model bloat (APK size, memory) | L | M | Size and memory budgets; review each model and library | M2 |
| R16 | AI-agent drift: inconsistent decisions across sessions | M | M | ADRs, `CLAUDE.md`, task cards, small PRs, review by the user on the gates in `08-ai-agent-workflow.md` | ongoing |
| R17 | Earbuds variety: HFP narrowband versus wideband, single-mic roles, firmware quirks, LE Audio gaps. The headset mic misbehaves on some models. | H | M | Universal Tier 0 baseline; tier fallback; "Test my headset" wizard with per-headset profiles; volunteer reports (S7); never promise headset-mic quality on every model | S1, S7 |
| R18 | Low-end phones are too slow or short on RAM for the detector, so budgets fail there | M | H | Low-end reference device and NFR-16; small models; VAD gating; consider a lighter model tier | S4, M5-T7 |
| R19 | OEM background restrictions differ per vendor and change often. Some vendors need user steps that are not documented in one place. Devices without Google services lack some conveniences. | H | H | Maintained per-vendor guide (M5-T2) verified on real devices; no Play Services dependency; honest onboarding; user reports | M5 soak |
| R20 | We cannot test every phone and earbud, so "works on all" cannot be proven | H | M | State the promise precisely (ADR-0009: universal baseline plus detected upgrades); label tested versus reported; keep the compatibility matrix public | M6 |
