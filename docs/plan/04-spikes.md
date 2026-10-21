# 04 — Spikes (M0 experiments)

Status: Draft v0.1 · 2026-10-20

A spike is a time-boxed experiment that answers one question so an ADR can be decided on evidence. Spike code is throwaway (`spikes/`). Spike **results** are permanent: they are committed to `docs/spikes/results/Sx-<name>.md` using the template at the end of this file.

Who does what: an AI agent writes the spike app and the analysis scripts. The user runs the app on their own phone and earbuds and sends back the exported report. The cloud sandbox has no phone, no earbuds, and no Bluetooth.

## Implementation status

The experiment app "EchoCatch Lab" (`spikes/`) implements S1, S2, S3, S5 and the R0 to R2 parts of S6, and shares a JSON report with no audio. The owner's step-by-step instructions are in [runbook-oneplus-11r-buds-4](../spikes/runbook-oneplus-11r-buds-4.md). Not covered by the app: S4 (host-side benchmark, built in M2), S6 runs R3 to R5 (need the native pipeline and Oboe), and S7 (the same app is reused by volunteers later).

First results (2026-10-21): [first-run results](../spikes/results/2026-10-21-first-run-oneplus-11r-buds-4.md). Lab app v2 adds guided S1 and S3 flows, because the first run showed that manual tests are easy to run incompletely.

## Primary test devices

The owner's devices are the first data point for every hardware spike:
- Phone: **OnePlus 11R** (Android version to be recorded by the spike app).
- Earbuds: **OnePlus Buds 4** (codec and LE Audio support to be recorded by the spike app; not assumed).

Notes:
- OnePlus phones run OxygenOS, which has its own battery manager. That makes this phone a good first test of background persistence (R19), but it is one vendor.
- This phone is a high-end-class device. It cannot stand in for the low-end reference needed by NFR-16.
- One pair of earbuds proves nothing about "all non-Apple TWS". S7 widens the data over time.

## Order and dependencies

```
S6 baseline power ─┐
S1 BT route ───────┼─► ADR-0002 default source ─► M4
S2 FGS lifecycle ──┴─► ADR-0004 ───────────────► M1, M3
S3 ducking matrix ─────► ADR-0005 ──────────────► M3
S4 detector bench ─────► ADR-0003 (can start now in the sandbox, no phone for accuracy)
S5 coexistence ────────► ADR-0004 / M5
```

S4 accuracy work needs no hardware and can start immediately. S7 (compatibility survey) is ongoing: it reuses the S1 protocol and continues after M0 through the beta.

---

## S1 — Bluetooth microphone route versus music quality and battery

**Question:** What happens to music quality, music continuity, earbud battery, and phone battery when we open the earbuds' mic, compared with the phone mic?

**Hypotheses:**
- H1.1 Classic Bluetooth (HFP/SCO) mic degrades music to narrowband mono or pauses it.
- H1.2 LE Audio input (if the user's phone and earbuds support it) keeps quality acceptable.
- H1.3 The headset-mic route drains the earbuds noticeably faster than idle A2DP.
- H1.4 The phone mic in a pocket or bag has too little usable speech SNR to be the default.

**Matrix:** source ∈ {phone mic, BT classic SCO, LE Audio (if available), wired (if available)} × capture preset ∈ {`VOICE_RECOGNITION`, `UNPROCESSED`, `MIC`} × media ∈ {music, podcast}.

**Broad compatibility (ADR-0009):** the goal is any non-Apple TWS, so S1 is designed as a **short protocol anyone can run** (about 10 minutes per earbuds) with the spike app. It writes a stable JSON report (no audio) that volunteers can paste into a GitHub issue. The owner's earbuds are the first data point, and every additional model adds a row to the compatibility matrix (S7). S1's decision rule uses the whole set of reports, not one pair of earbuds.

**Measure per cell (each 30 min ⚠):**
- route actually in use (`dumpsys audio` / `AudioRecord.getRoutedDevice()`), codec, sample rate
- does media keep playing; audible quality change (A/B listening notes, plus the reported playback sample rate and codec)
- time to open the route and time to restore after close
- earbuds battery change (companion app or the Android Bluetooth battery level) and phone battery change
- speech capture quality: record a scripted set of spoken names at 1 m and 3 m from the wearer, and compute simple SNR and intelligibility proxies offline

**Decision rule (feeds ADR-0002):**
- If a headset-mic route keeps music quality acceptable *and* the earbud battery cost is acceptable → default `auto` prefers headset mic.
- Otherwise → default to phone mic, with the headset mic as an opt-in setting that warns about the cost.

**Output:** `docs/spikes/results/S1-bt-route.md` and an update to ADR-0002.

---

## S2 — Foreground service lifecycle and start surfaces

**Question:** How can the microphone foreground service be started and kept alive, and can the mic be opened and closed later without a visible activity?

**Hypotheses:**
- H2.1 (P5) A mic FGS started from the visible app can open and close `AudioRecord` later from the background.
- H2.2 A notification action can start the service from the background (P3).
- H2.3 A Quick Settings tile tap can start it (tiles are not listed in the docs).
- H2.4 Boot cannot auto-start it (P4), so a reboot returns the app to Disarmed.
- H2.5 The service survives screen-off, Doze, and 8 hours overnight on stock Android. OEM skins may differ.

**Method:** a test service logs every state transition with timestamps. Scenarios: start from UI → background → screen off 30 min → open/close the mic every 5 min; start from notification action; start from tile; start after reboot; battery-optimization on versus exempt; overnight 8 h. Run on the user's phone, and on any other Android versions the user can borrow.

**Output:** `S2-fgs-lifecycle.md`, ADR-0004 finalised (which start surfaces are supported, whether `ArmedIdle` can keep the service alive with the mic closed, and what the restart story is).

---

## S3 — Ducking and pause behaviour across players

**Question:** For each player, what do the focus request type and `AudioAttributes` do to the music?

**Matrix:**
- Players: VLC, AntennaPod, NewPipe, plus the user's Spotify, YouTube, YouTube Music, and any other player the user relies on.
- Focus requests: `GAIN_TRANSIENT_MAY_DUCK`, `GAIN_TRANSIENT`, `GAIN_TRANSIENT_EXCLUSIVE`.
- Alert attributes: `USAGE_ASSISTANCE_NAVIGATION_GUIDANCE`, `USAGE_ASSISTANCE_ACCESSIBILITY`, `USAGE_NOTIFICATION`, `USAGE_ALARM`, each with `CONTENT_TYPE_SPEECH` and `CONTENT_TYPE_SONIFICATION`.
- Content: music versus spoken-word.

**Measure:** does the player duck, pause, or ignore it; does it resume on focus release and how long after; audible mixing of the cue with music; `dumpsys audio` focus stack; T_alert on the device clock; and extra delay on the Bluetooth route (T_ear, using a loopback recording where practical).

**Also test:** (a) can `getActivePlaybackConfigurations()` identify speech versus music content so we can choose pause for speech apps? (b) the temporary stream-volume duck fallback: does it work with Bluetooth absolute volume, and does it reliably restore?

**Decision rule (ADR-0005):** choose the focus type and attributes with the best coverage across the user's real players. List known-ignoring apps in the docs.

**Output:** `S3-ducking-matrix.md` (a table), ADR-0005 finalised.

---

## S4 — Detector candidates on the benchmark

**Question:** Which engine and pipeline shape meets the accuracy, latency, and CPU budgets?

**Candidates:**
- **C1:** VAD → sherpa-onnx KWS (open-vocabulary).
- **C2:** VAD → tiny prefilter → C1, introduced only if C1 after VAD busts a budget.
- **C3:** a custom tiny streaming model trained offline on synthetic speech for a given name (microWakeWord-style). Only a fallback, because per-name training does not fit a "type your name" product.
- **Excluded by licence:** openWakeWord pretrained models (P28), Porcupine, Snowboy (P30).

**Test names** (chosen to cover length and phonetic difficulty; the owner's own name is added later, D3b):

| Group | Names |
|---|---|
| Short | Alex, Maya, Jack |
| Medium | Daniel, Priya, Arjun, Sofia |
| Long | Alexandra, Venkatesh |
| Hard phonetics | Siobhan, Nguyen, Xiomara |

Each name is also tested with a nickname where natural (for example Alexandra → Alex). **Near-miss negatives** for false alarms: Maya↔Mia, Jack↔Zack, Daniel↔Danielle, Sofia↔Sophie, Priya↔Pria, Arjun↔Argon.

**To establish first (the unknowns):** how keywords are specified for C1 (P26), the licences of the model files (P27), and whether non-English or Indian-English names work with the English model (P25).

**Method:**
1. Build the corpus (`tools/corpus`): positives made by mixing recorded and synthetic name utterances into noise at 20, 10, 5, and 0 dB SNR; negatives of several hours of speech without the name, TV, music, street, and café noise. Test names must include short, long, and phonetically tricky names. Use only datasets whose licences allow this, recorded in the corpus manifest.
2. Run each candidate in `tools/bench` on the host. Report recall at each SNR, FA/h, T_detect, duty cycle, and CPU time per audio-second.
3. Run the top candidates on the user's phone and measure on-device CPU and wake-ups (simpleperf or Perfetto).

**Decision rule (ADR-0003):** the simplest candidate that meets NFR-1, 3, 4, 5, 6, 8. Add stage 2 only if needed.

**Output:** `S4-detector-bench.md` with the full table, and ADR-0003 finalised.

---

## S5 — Coexistence with calls and other recorders

**Question:** What happens when a phone call, voice assistant, voice message, or screen recorder wants the mic?

**Method:** run Listening, then trigger each scenario. Observe whether capture is silenced (P21 is verified in the docs; this spike confirms it on real devices), whether `AudioRecordingCallback` / `isClientSilenced()` reports it (P21a), whether we recover on our own, and whether we break the other app. Include an app with a visible UI that records audio, and a voice assistant.

**Output:** `S5-coexistence.md`, feeding ADR-0004 and M5-T3. The rule is that we must never prevent a call from working.

---

## S6 — Power baseline and measurement protocol

**Question:** What do idle and each listening configuration cost, and can we measure it reliably?

**Protocol (define once, reuse in M5):** a fixed reference scenario: screen off, music over Bluetooth, volume fixed, the same background apps and radios every run, charge level and temperature recorded, repeated runs. Compare:

| Run | State |
|---|---|
| R0 | music only (baseline) |
| R1 | + foreground service, mic closed (ArmedIdle) |
| R2 | + mic open, frames discarded |
| R3 | + VAD only |
| R4 | + full pipeline (C1) |
| R5 | R2–R4 with Oboe power-saving versus `AudioRecord` (tests P20) |

**Instruments:** `adb shell dumpsys batterystats` and Battery Historian, Perfetto traces, Android Studio Energy Profiler where available, and plain battery-percent deltas over long runs (≥ 2 h) as a sanity check. Record the method's noise floor so budgets are not set below what we can measure.

**Output:** `S6-power-baseline.md`, with numeric NFR-9, NFR-10, and NFR-7 values added to ADR-0001.

---

## S7 — Device and earbuds compatibility survey (ongoing)

**Question:** Across many phones, Android versions, and non-Apple earbuds, which tier works, and where are the problems?

**Method:** reuse the S1 protocol and the "Test my headset" wizard (FR-11) as the data collector. Sources of reports: the owner's devices, volunteers via GitHub issue template (M6-T4), and any emulator or loaner devices available. S2 (service lifecycle) and S5 (coexistence) are also repeated on additional Android versions where possible.

**Output:** `docs/compat/` matrix (from M6), with columns for phone, Android version, earbuds, chipset/codec if known, tier that works, music-quality note, and issues. Report entries are tagged *tested* or *reported by a user*. A pattern that cannot be handled by a profile (for example one vendor's firmware) becomes a vendor-quirks data file, never code branches on model names.

---

## Spike result template

```markdown
# Sx — <name>
Date · device (model, Android version, build) · earbuds (model, codec/LE Audio) · app commit
## Question
## Method (what was actually done, deviations from plan)
## Raw results (table)
## Findings (each linked to a hypothesis: confirmed / rejected / inconclusive)
## Decision / ADR changes
## Open issues
```
