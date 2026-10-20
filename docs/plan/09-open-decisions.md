# 09 — Open decisions and questions

Status: Draft v0.1 · 2026-10-20

Items the project cannot resolve without the user (or hardware). Each says what it blocks and carries a recommendation so the user can answer quickly.

## For the user

| ID | Decision | Blocks | Recommendation |
|---|---|---|---|
| D2 | **More test devices** (primary devices are known, see Resolved): your phone's exact Android version (Settings → About device), plus *any other* Android phone, especially an older Android version or an entry-level phone, and other non-Apple earbuds you or friends can lend for S1/S7. | S7, NFR-16, M5-T7 | One low-end phone is needed to check the CPU budget on weaker hardware. |
| D3b | **Your own name and nicknames** for the acceptance demo (spelling and how you say it) | M3-T6, M3-T7 | Optional until M3. The benchmark uses the test-name set in `04-spikes.md`, so nothing is blocked. |
| D4 | **minSdk** | M1 | **Set to 26 by ADR-0009** to meet the "most Android versions" goal. Confirm in M1-T9. Going lower is possible but needs legacy focus code and loses automatic ducking. Tell me if you want API 24 or 21 anyway. |
| D5 | **targetSdk** | M1 | The current stable targetSdk when M1 starts; bump every year. |
| D6 | **Application ID** | M1 | `io.github.nitinreddy08.echocatch` (conventional for GitHub-hosted projects), or a domain you own. |
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
| D1 Licence: GPL-3.0-only | Chosen by the owner 2026-10-20 | ADR-0010, `LICENSE` |
| D2 (part) Primary test devices: **OnePlus 11R** phone and **OnePlus Buds 4** earbuds | Provided by the owner 2026-10-20. The phone's Android version and the earbuds' codec / LE Audio support are not yet known; S1 will record them from the device. | `04-spikes.md` |
| D3 Test names | Claude chose a varied set for the benchmark (owner delegated) | `04-spikes.md` S4 |
| D7 Distribution: F-Droid and GitHub first, Google Play later | Chosen by the owner 2026-10-20 | ADR-0011 |
| Measure-first, budgets, pluggable source, cascade, native core, pre-recorded cue | Agreed 2026-10-20 | ADR-0001…0003, 0006, 0008 |
| Event-driven mic (mic open only when needed) | Agreed in principle 2026-10-20; final shape depends on S2 | ADR-0004 (Proposed) |
