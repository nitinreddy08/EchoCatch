# First hardware run: OnePlus 11R and OnePlus Buds 4

Date of run: 2026-10-21 · app: EchoCatch Lab 0.0.1-lab1 (first build, plain tests) · runbook: [runbook-oneplus-11r-buds-4](../runbook-oneplus-11r-buds-4.md)

This is a partial result. Two of the three experiments that matter most were not completed, and one (the service test) arrived cut off. What it does show is listed first.

## Device facts (observed)

| Item | Value |
|---|---|
| Phone | OnePlus 11R, model CPH2487 |
| Android | **16 (API 36)**, security patch 2026-09-01, build CPH2487_16.0.5.1301 |
| Earbuds | OnePlus Buds 4, connected during the run |
| Bluetooth devices the phone listed | Music output (A2DP) named "OnePlus Buds 4"; a call link (SCO) as input and output |
| A2DP output | 44.1, 48 and 96 kHz; PCM 16-bit, float and 24-bit. A 96 kHz, 24-bit output suggests a high-resolution codec is available. This is an inference, not confirmed |
| Call-link (SCO) input | **mono, 16-bit, 8 kHz and 16 kHz only** |
| LE Audio device | **none listed**, so this pair was using classic Bluetooth |
| Phone microphones | two built-in microphones, 8 to 48 kHz |
| Product name on the call link | "CPH2487" (the phone's name, not the earbuds'), so product names cannot identify the headset |

## S1: microphone route (8 runs, all with method PREFERRED_DEVICE)

| Input | Routed to | Rate | First data | Result | Music active before, during, after |
|---|---|---|---|---|---|
| Bluetooth call link (3 runs) | Bluetooth call link | 16 kHz | 164 to 176 ms | **all samples zero (silence)** | yes, yes, yes |
| Phone microphone (2 runs) | phone microphone | 16 kHz | 192 to 220 ms | works: RMS −38.4 and −35.6 dBFS, peak about −17 dBFS | yes, yes, yes |
| FM tuner (type 16) | FM tuner | 16 kHz | 222 ms | silence | yes, yes, yes |
| Telephony and remote-submix inputs | fell back to phone mic | n/a | none | read error −3 (invalid operation) | yes, yes, yes |

### Findings

1. **The earbuds' microphone returned only silence with this method.** Android accepted `setPreferredDevice`, reported the call link as the routed device, and delivered 16 kHz buffers, but every sample was zero. Routing labels are not proof of audio. The product must check that the signal is non-zero before trusting a route. *Inference:* the call link was never actually opened. The audio mode stayed NORMAL and the communication device stayed BUILTIN_EARPIECE throughout.
2. **Music was not disturbed in these runs.** Music stayed active before, during and after every test. This is expected, because the call link was never opened, so it says nothing yet about what opening it does to music quality.
3. **The method that opens the call link (`setCommunicationDevice` with call mode) was never run.** It is the one that decides the central question (does the earbuds' mic ruin music quality, and does it work at all). It is now the first thing to test.
4. **The phone microphone works while music plays**, with data in about 0.2 s. The two recordings have 96 to 99 percent of their measured band power at 250 Hz, which usually means little or no speech was captured (speech spreads energy across 300 to 3500 Hz). The speech-quality question is therefore still open.
5. **The call link caps the earbuds' microphone at 16 kHz mono.** That matches the planned pipeline (16 kHz) and confirms platform facts P13 and P36 for this pair. It does not tell us the quality of the earbuds' microphone itself.
6. **No LE Audio device was listed**, so hypothesis H1.2 (LE Audio keeps quality) cannot be tested with this pair as connected.
7. **A music-quality note exists but is unusable.** "good" was attached to the last test, which was a failed remote-submix input. No verdict was recorded for the earbuds' microphone.

Hypotheses: H1.1 (classic mic degrades music) **not yet tested**; H1.2 **cannot be tested** (no LE Audio); H1.3 (earbud battery) **not tested**; H1.4 (pocket pickup) **not tested**.

## S3: ducking (2 runs, one combination)

Combination: focus `GAIN_TRANSIENT_MAY_DUCK`, usage `ASSISTANCE_NAVIGATION_GUIDANCE`, content type `SPEECH`.

| Measure | Run 1 | Run 2 |
|---|---|---|
| Focus request result | GRANTED in 5 ms | GRANTED in 7 ms |
| Alert output start (app side, not acoustic) | 49 ms | 31 ms |
| Focus callbacks received by our app | none | none |
| Tester observation | "ducked" | "alert hard to hear" |
| Music stream volume index | 81 before and after | 81 before and after |

### Findings

1. **Ducking worked for the one player used, as the tester heard it** (run 1). Run 2 reported the alert hard to hear, so the result is not stable yet.
2. **The music player declares usage MEDIA with an unknown content type (0).** Per the official audio-focus guide (P7) such a player is eligible for automatic ducking. This is consistent with run 1, but it is one player.
3. **Platform fact P12 is now verified on this device:** `getActivePlaybackConfigurations()` shows other apps' players with their usage and content type, and shows our own alert as usage 12 (navigation guidance), content type 1 (speech) while it plays.
4. **The music volume index did not change** (81 throughout), which suggests the system ducked the player, not the music stream. This is an inference.
5. **Which player was tested was not recorded** (the field held something else), and podcast or speech players, other focus types and other alert attributes were not tested. S3 remains open.

## S2, S5, S6: service, coexistence, power

The report was cut off after two events, so almost nothing is known:
- Service started from the app screen in `cycle` mode at 2026-10-21 09:43:42 local time, with the screen on, not in Doze, power-save off, and **no battery-optimization exemption**.
- The phone had been running about 18 hours since boot (so no reboot test was done).
- No microphone-cycle, notification, tile, boot, coexistence or battery events were received.

## Problems found in the lab app (fixed in the next build)

| Problem | Effect | Fix |
|---|---|---|
| S1 offered non-microphone inputs (FM tuner, telephony, remote submix) | wasted tests | only real microphones are offered |
| Music-quality buttons applied to the last test | a verdict attached to a failed test | a prompt appears after each test and attaches to that test |
| Only one S1 method was tried | the key question stayed open | a guided test runs the call-route method first |
| No reminder to speak | the phone-mic recordings contain little speech | the guided test says to talk throughout, and the report now includes loudness per 100 ms window |
| Player field was ambiguous | player not recorded | clearer label, and the guided alert test requires it |
| Report too long to paste (indented JSON) | service events lost | compact JSON and a "Share events only" button |
| Debug signing key differed per CI build | each update needs an uninstall | one fixed public debug key (never for release) |

## What to run next

1. S1 guided earbuds microphone test (four 10-second tests, about 5 minutes). Highest priority.
2. S3 guided alert test with a named player (four alerts), then repeat with a podcast app.
3. S2 service run, sharing events only.

## Effect on decisions

No ADR changes yet. Evidence so far:
- ADR-0002 (pluggable audio source): supports building the signal check into the source layer, because a route can report success and deliver silence. Default choice still waits for the call-route test.
- ADR-0005 (audio focus): `GAIN_TRANSIENT_MAY_DUCK` with navigation-guidance attributes worked once for an unknown-content-type media player. Not enough to decide.
