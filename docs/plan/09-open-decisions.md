# 09 — Open decisions and questions

Status: Draft v0.1 · 2026-10-20

Items the project cannot resolve without the user (or hardware). Each says what it blocks and carries a recommendation so the user can answer quickly.

## For the user

| ID | Decision | Blocks | Recommendation |
|---|---|---|---|
| D1 | **Licence**: Apache-2.0 or GPL-3.0 (or another) | M0-T1, first public release | GPL-3.0 if you want improvements to stay open and F-Droid is the main channel. Apache-2.0 if you want maximum reuse (including closed-source). Both are compatible with sherpa-onnx (Apache-2.0) and Silero VAD (MIT). |
| D2 | **Test devices**: phone model and Android version; earbuds model and codec / LE Audio support. Because the goal is broad support, also list *any other* Android phones (especially an older Android version or an entry-level phone) and other non-Apple earbuds you or friends can lend for S1/S7. | S1–S7, all `HW` tasks | Please share these first. S1 results depend on them, and one low-end phone is needed for NFR-16. |
| D3 | **Language and spelling** of the name (and nicknames) | S4, M3-T6 | Provide 1–3 real names to use as S4 test names. |
| D4 | **minSdk** | M1 | **Set to 26 by ADR-0009** to meet the "most Android versions" goal. Confirm in M1-T9. Going lower is possible but needs legacy focus code and loses automatic ducking. Tell me if you want API 24 or 21 anyway. |
| D5 | **targetSdk** | M1 | The current stable targetSdk when M1 starts; bump every year. |
| D6 | **Application ID** | M1 | `io.github.nitinreddy08.echocatch` (conventional for GitHub-hosted projects), or a domain you own. |
| D7 | **Distribution** | M6 | F-Droid and GitHub Releases first. Reaching "all Android phones" would be easier on Google Play, which has extra rules for background microphone services and battery-optimization exemptions that I have not verified. Revisit at M6 (the no-`INTERNET` design does not prevent it). |
| D8 | **Multiple names** | FR-1, M3-T6 | Up to 3 keywords (name plus two nicknames) in v1. |
| D9 | **Project governance** | M6 | Maintainer-led, with a short CONTRIBUTING and a code of conduct. |

## For the spikes (cannot be decided on paper)

| ID | Question | Spike |
|---|---|---|
| Q1 | Default audio source: headset mic or phone mic | S1 |
| Q2 | Can the mic be opened and closed from a live FGS without a visible activity | S2 |
| Q3 | Which start surfaces work (notification, tile, widget) | S2 |
| Q4 | Focus type and attributes with the best player coverage; pause for speech content | S3 |
| Q5 | How keywords are specified for sherpa KWS and whether the English model suits the names | S4 |
| Q6 | Whether a stage-2 prefilter is needed | S4 |
| Q7 | Does Oboe power-saving input reduce power versus `AudioRecord` | S6 |
| Q8 | How capture behaves during calls and other recorders | S5 |

## Resolved

| Decision | Resolution | Where |
|---|---|---|
| Plan first, then code | Agreed 2026-10-20 | this plan |
| Measure-first, budgets, pluggable source, cascade, native core, pre-recorded cue | Agreed 2026-10-20 | ADR-0001…0003, 0006, 0008 |
| Event-driven mic (mic open only when needed) | Agreed in principle 2026-10-20; final shape depends on S2 | ADR-0004 (Proposed) |
