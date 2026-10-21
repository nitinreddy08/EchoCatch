# S4 first screening: keyword engines on synthetic scenes

Date: 2026-10-21 · tools: [tools/corpus](../../../tools/corpus), [tools/s4](../../../tools/s4) · budgets: [01-requirements](../../plan/01-requirements.md) · decision: [ADR-0003](../../adr/0003-detection-cascade-and-engine-selection.md)

## Summary

- **No candidate meets the provisional budgets** on these synthetic scenes. The best recall at 10 dB SNR with at most about 1 false alarm per hour of ordinary audio is **0.18** (the budget is at least 0.90 at 10 dB and at most 1 false alarm per hour). The highest recall anywhere in the sweep is 0.64, and it comes with hundreds of false alarms per hour.
- **The result depends enormously on the name.** Daniel and Alex are detected 60 to 90 percent of the time by some engines; Venkatesh, Siobhan, Nguyen, Xiomara and Priya are mostly or entirely missed. A typed spelling is often not how the name sounds.
- **Telling the matcher how the name sounds helps a lot** (for Siobhan, Nguyen and Xiomara recall went from about 0 to 0.5 to 0.7 with a respelled alias and sound matching), but it also raises false alarms, because short sound-alike words trigger it.
- **A competing talker is the hardest background** (recall 0.15 with a TV-like talker versus 0.53 in street noise or a quiet room for the larger recogniser).
- **A voice-activity gate does not save work in crowds:** it is open 91 to 100 percent of the time in babble and TV backgrounds, and about 25 percent in quiet or street scenes.
- **Latency:** the median detection arrives 0.35 to 0.50 s after the end of the name, and the 95th percentile 0.56 to 0.69 s, above the provisional 0.4 s budget. This includes the 100 ms streaming chunk and each model's look-ahead.

These are synthetic voices and noise. Treat them as a comparison between engines and settings, not as the product's accuracy. See "Limits" below.

## What was run

- **Scenes:** 1,968 scenes of 8 s (4.4 h): 468 with one of 12 test names (alone, before a phrase, or after a lead-in) at 20, 10, 5 and 0 dB SNR in babble, synthetic street noise or a competing talker (plus a quiet room at 30 dB); 1,500 negatives (600 speech without names, 300 near-miss names, 300 other names, 300 background only; 3.33 h).
- **Voices:** 8 synthetic voices (US, GB, and two Hindi voices reading English). Pronunciations of hard names are the project's approximations (`tools/corpus/names.json`).
- **Candidates:**
  - **C1:** the keyword spotter `kws-zipformer-gigaspeech-3.3M`, with names as BPE pieces of the typed spelling.
  - **C4:** a small streaming recogniser (20M and 70M parameters, LibriSpeech lineage) with hotword boosting for the names.
  - **C5:** the same 70M recogniser run plainly, with its transcript matched to the names by exact text or by Metaphone sound key.
  - "With aliases" adds a respelled variant of the name (for example "SHIVAWN" for Siobhan), standing in for a "how it sounds" hint from the user.
- **Hit:** the right name detected from 0.3 s before the name starts to 1.5 s after it ends. **False alarm:** any detection in a negative scene. "Ordinary audio" excludes the near-miss scenes (names built to sound like the target, such as Mia for Maya).

## Results

| Configuration | Recall | Recall at 10 dB | Recall at 0 dB | False alarms per hour, ordinary audio | False alarms per hour, including near-miss names | T_detect p50 / p95 (s) | Host CPU (x real time) |
|---|---|---|---|---|---|---|---|
| C1 spotter, typed spelling, boost 1, threshold 0.1 | 0.30 | 0.28 | 0.19 | 7.5 | 23.4 | 0.39 / 0.59 | 0.021 |
| C1 spotter, typed spelling, boost 1, threshold 0.25 | 0.26 | 0.23 | 0.16 | 4.1 | 19.8 | 0.39 / 0.59 | 0.021 |
| C1 spotter, typed spelling, boost 1, threshold 0.4 | 0.22 | 0.23 | 0.12 | 3.4 | 13.5 | 0.39 / 0.59 | 0.021 |
| C1 spotter, typed spelling, boost 2, threshold 0.1 | 0.31 | 0.30 | 0.20 | 10.5 | 28.2 | 0.37 / 0.59 | 0.021 |
| C1 spotter, typed spelling, boost 2, threshold 0.25 | 0.24 | 0.20 | 0.15 | 4.1 | 19.8 | 0.37 / 0.58 | 0.021 |
| C1 spotter, typed spelling, boost 2, threshold 0.4 | 0.15 | 0.17 | 0.08 | 0.8 | 6.6 | 0.35 / 0.56 | 0.021 |
| C1 spotter, typed spelling, boost 3, threshold 0.1 | 0.28 | 0.32 | 0.18 | 15.0 | 31.8 | 0.37 / 0.59 | 0.021 |
| C1 spotter, typed spelling, boost 3, threshold 0.25 | 0.19 | 0.21 | 0.10 | 5.2 | 17.7 | 0.37 / 0.59 | 0.020 |
| C1 spotter, typed spelling, boost 3, threshold 0.4 | 0.09 | 0.10 | 0.04 | 0.4 | 4.5 | 0.35 / 0.59 | 0.021 |
| C4 recogniser 20M, hotword boost 0 (none), with aliases | 0.09 | 0.07 | 0.04 | 0.8 | 1.5 | 0.37 / 0.57 | 0.032 |
| C4 recogniser 20M, hotword boost 2, with aliases | 0.24 | 0.19 | 0.12 | 1.5 | 9.9 | 0.37 / 0.58 | 0.034 |
| C4 recogniser 20M, hotword boost 4, with aliases | 0.28 | 0.22 | 0.17 | 3.0 | 12.6 | 0.37 / 0.56 | 0.034 |
| C4 recogniser 70M, hotword boost 4, with aliases | 0.35 | 0.31 | 0.21 | 4.1 | 21.0 | 0.47 / 0.65 | 0.081 |
| C5 recogniser 70M plain + exact match, typed only | 0.21 | 0.17 | 0.12 | 0.0 | 1.8 | 0.49 / 0.67 | 0.077 |
| C5 recogniser 70M plain + exact match, with aliases | 0.21 | 0.18 | 0.12 | 0.0 | 1.8 | 0.49 / 0.67 | 0.077 |
| C5 recogniser 70M plain + phonetic match (tau 1), typed only | 0.29 | 0.23 | 0.17 | 6.4 | 18.6 | 0.49 / 0.68 | 0.077 |
| C5 recogniser 70M plain + phonetic match (tau 1), with aliases | 0.44 | 0.39 | 0.25 | 22.5 | 32.4 | 0.48 / 0.68 | 0.077 |
| C5 recogniser 70M plain + phonetic match (tau 0.85), typed only | 0.29 | 0.23 | 0.17 | 7.5 | 20.1 | 0.49 / 0.68 | 0.077 |
| C5 recogniser 70M plain + phonetic match (tau 0.85), with aliases | 0.44 | 0.39 | 0.25 | 23.6 | 33.9 | 0.48 / 0.68 | 0.077 |
| C5 recogniser 70M plain + phonetic match (tau 0.7), typed only | 0.37 | 0.31 | 0.19 | 60.4 | 74.4 | 0.49 / 0.68 | 0.077 |
| C5 recogniser 70M plain + phonetic match (tau 0.7), with aliases | 0.53 | 0.47 | 0.29 | 84.4 | 95.7 | 0.49 / 0.69 | 0.077 |
| C5 recogniser 70M plain + phonetic match (tau 0.6), typed only | 0.51 | 0.45 | 0.30 | 295.5 | 300.0 | 0.50 / 0.69 | 0.077 |
| C5 recogniser 70M plain + phonetic match (tau 0.6), with aliases | 0.64 | 0.58 | 0.38 | 444.8 | 435.0 | 0.48 / 0.69 | 0.077 |

Host CPU is one CPU thread on a development machine, not a phone. T_detect is measured from the end of the name to the detection.

### Recall by name

| Name | C1 spotter typed (boost 1, thr 0.25) | C4 recogniser 20M + hotwords 4, aliases | C4 recogniser 70M + hotwords 4, aliases | C5 70M + phonetic match, typed only | C5 70M + phonetic match, with aliases |
|---|---|---|---|---|---|
| Alex | 0.72 | 0.62 | 0.67 | 0.62 | 0.62 |
| Alexandra | 0.00 | 0.46 | 0.69 | 0.74 | 0.74 |
| Arjun | 0.05 | 0.00 | 0.00 | 0.05 | 0.05 |
| Daniel | 0.87 | 0.79 | 0.82 | 0.72 | 0.72 |
| Jack | 0.49 | 0.46 | 0.49 | 0.36 | 0.36 |
| Maya | 0.46 | 0.31 | 0.51 | 0.18 | 0.18 |
| Nguyen | 0.00 | 0.13 | 0.54 | 0.00 | 0.69 |
| Priya | 0.08 | 0.00 | 0.08 | 0.08 | 0.08 |
| Siobhan | 0.00 | 0.00 | 0.03 | 0.00 | 0.54 |
| Sofia | 0.49 | 0.31 | 0.38 | 0.62 | 0.62 |
| Venkatesh | 0.00 | 0.00 | 0.00 | 0.08 | 0.08 |
| Xiomara | 0.00 | 0.28 | 0.00 | 0.00 | 0.62 |

### Why many names fail: what the recogniser writes for a clean, correctly pronounced name

The 70M recogniser transcribing the clean name clips (no noise), one result per voice:

| Name (as said) | What was written across the eight voices |
|---|---|
| Alex | ALEX, ALEX, ALEX, ALEX, (nothing), LEGS, ALEX, ALEX |
| Maya | MAYA ×4, MY, MI, MY, MAYA |
| Daniel | DANIEL in all eight |
| Alexandra | ALEXANDRA in all eight |
| Sofia | SOPHIA ×7, SO FEAR |
| Priya (said "Preeya") | PRIA, PRIA, A, PREY, (nothing), H, PREA, PRE YE |
| Arjun | ORIGIN, ARGON, MARGIN, OR GUN, ORIGIN, (nothing), ORIGIN, OUR GIN |
| Venkatesh | VAN KERISH, VANQUISH, VANKHYRIS, VANQUIRISH, KATISH, VAN KAYLISH, VANCADAS, VANQUIIRASH |
| Siobhan (said "Shivawn") | CHIVON, CHIVONNE, CHIVONNE, CHIVON, CHIVONNE, YVONNE, SHE VONNE, CHIVONNE |
| Nguyen (said "Win") | WHEN ×7, WIND |
| Xiomara (said "See oh mara") | C OMARA ×3, OMARA, C O MARA, SEE OMARA ×3 |

So the recogniser spells rare names by sound ("SOPHIA", "CHIVONNE"), common words win over rare names ("ORIGIN" for Arjun, "WHEN" for Win), and for some names the synthetic voice may not be saying what a native speaker would (Venkatesh). Both engine behaviour and synthetic pronunciation contribute; the data cannot separate them.

### Voice-activity gate (Silero, threshold 0.5, 0.3 s pre-roll, 0.5 s hangover)

Share of audio inside an open gate (the duty cycle, provisional budget at most 0.25):

| Background | Scenes with a spoken name | Speech only | Background only |
|---|---|---|---|
| Babble | 0.95 | 0.95 | 0.91 |
| Competing talker (TV) | 1.00 | 1.00 | 1.00 |
| Street noise | 0.25 | 0.80 | 0.00 |
| Quiet room | 0.27 | 0.81 | 0.00 |

The gate covered at least half of the spoken name in 99 to 100 percent of scenes at every SNR (trivially so in babble and TV, where it is always open). The gate's own cost is about 0.005 times real time.

## What this means

1. **ADR-0003's shape still holds, but its first stage is not good enough.** A cheap spotter in front of a stronger verifier needs the spotter to catch nearly every call; here it catches about a third. A stronger verifier cannot recover what the first stage drops. The cascade needs a different first stage or an approach that does not depend on typed spelling.
2. **Detection quality must be reported per name.** A product that cannot detect "Siobhan" should say so (proposed FR-12, "name check").
3. **The user's own pronunciation is valuable.** A "how it sounds" spelling, or a few spoken samples from which the recogniser's own spellings are derived, is the most promising lever seen so far (FR-1 updated). This was only simulated here with respelled aliases.
4. **Babble and competing talkers will limit both accuracy and battery savings.** A speech gate alone will not hold the 25 percent duty-cycle budget in a crowd; a gate that looks at loudness or nearness, not just speech, needs to be evaluated.
5. **Options to test next** (each needs a plan and probably an ADR): per-name threshold calibration; enrollment-derived aliases; a per-name trained small model fed by synthetic voices (candidate C3); a stronger verifier (for example a small Whisper-class model) on candidate windows; a nearness cue so far-away talkers do not count.

## Limits

- Synthetic voices and noise are cleaner and more regular than real ones; recall on real speech may be lower or higher.
- Pronunciations of Priya, Arjun, Siobhan, Nguyen, Venkatesh and Xiomara are approximations by the project; native speakers should check them.
- One seed, 12 names, 8 voices, 1,968 scenes. False-alarm rates per hour rest on 2.7 h of ordinary audio, so they are coarse (one alarm is about 0.4 per hour).
- Host CPU is not phone CPU; phone cost and battery come from the S6 protocol and on-device runs.
- Real speech and noise corpora (OpenSLR, Zenodo, Hugging Face) could not be fetched from the sandbox, and no real recordings of the project's test names exist yet.
- The keyword model's GigaSpeech training-data terms are unverified (P54), so it cannot be bundled until checked.

## Reproduce

```
pip install -r tools/requirements-s4.txt
export ECHOCATCH_DATA=/path/to/scratch
python3 tools/corpus/fetch.py --pin              # also: asr-zipformer-en-20M, asr-zipformer-en-70M, silero-vad
python3 tools/corpus/synth.py && python3 tools/corpus/build_scenes.py
python3 tools/s4/screen.py --model $ECHOCATCH_DATA/kws-zipformer-gigaspeech-3.3M --sweep
python3 tools/s4/screen.py --engine asr --model $ECHOCATCH_DATA/asr-70M --bpe-model $ECHOCATCH_DATA/asr-70M/bpe.model --aliases --boost 4
python3 tools/s4/transcribe.py --model $ECHOCATCH_DATA/asr-70M --out transcripts.json && python3 tools/s4/match_eval.py --transcripts transcripts.json
python3 tools/s4/vad_duty.py --vad-model $ECHOCATCH_DATA/silero-vad/silero_vad.onnx
```

The spotter-with-aliases sweep was stopped after two configurations (boost 1, thresholds 0.1 and 0.25: recall 0.37 and 0.33, 33.6 and 24.9 false alarms per hour including near-miss names) because it was not competitive. The 70M recogniser's own hotword sweep ran for boost 4 only.
