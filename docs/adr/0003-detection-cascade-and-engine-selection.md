# ADR-0003: Gate detection with VAD, keep stage 2 conditional, choose the engine by benchmark

- **Status:** Accepted (engine and whether stage 2 exists are decided by spike S4; the first screening found no candidate within budget, see Revision below)
- **Date:** 2026-10-20
- **Deciders:** project owner, with Claude as architect
- **Evidence:** platform facts P22–P30 and P49–P55; [S4 first screening](../spikes/results/S4-first-screening-2026-10-21.md) (synthetic scenes only)

## Context

Always-on keyword detection runs on the main CPU, because third-party apps cannot use the phone's always-on hotword DSP (P22). Most real-world audio is silence, noise, or music, so doing less work on it is the main battery lever. The detector must work for any typed name (open vocabulary) and be fully offline with permissive licences. Picovoice Porcupine and Snowboy are excluded (P30). openWakeWord's pretrained models are non-commercial (P28). sherpa-onnx (Apache-2.0, KWS, Android/Kotlin) and Silero VAD (MIT) fit the constraints (P23, P24), with open questions about keyword specification, the English-only model, and model licences (P25–P27).

## Options considered

1. **Single always-on keyword model.** Simple, but spends inference on silence and noise.
2. **VAD → keyword verifier (C1).** Inference runs only on speech segments. Simple and likely cheap enough.
3. **VAD → tiny prefilter → verifier (C2).** Cheapest steady state, but adds a component and a failure mode.
4. **Per-name trained tiny model (C3).** Best per-name accuracy and efficiency, but needs training for each name, which does not fit "type your name".

## Decision

Design for the cascade, **build the simplest stage first.**
- Stage 1 (VAD) always runs while Listening.
- Stage 3 (keyword verifier) runs only on VAD-gated segments, over the ring-buffer window, with a 300 ms pre-roll and a ~500 ms hangover.
- Stage 2 (prefilter) is introduced **only if** S4 shows VAD → C1 fails the CPU or duty-cycle budgets.
- The engine is chosen by the S4 decision rule: the simplest candidate meeting NFR-1, 3, 4, 5, 6, and 8.
- Alert cooldown lives in `AlertController`, not in the pipeline.

## Revision (2026-10-21, first S4 screening)

The decision stands: a voice-activity-gated cascade, the simplest pipeline that meets the budgets, chosen by benchmark. The first screening on synthetic scenes adds evidence that changes what to try next:

- No candidate met the provisional budgets. The keyword spotter with typed names reached recall 0.09 to 0.31, with 0.4 to 15 false alarms per hour on ordinary audio (4.5 to 32 when near-miss names are counted); a small recogniser with hotwords or sound matching reached up to 0.44 recall at 32 false alarms per hour (counting near-miss names). Recall at 10 dB was at most 0.18 when false alarms were held to about 1 per hour.
- Recall depends strongly on the name (about 0 to 0.9) because recognisers write rare names by sound. A sound hint for the name lifted hard names from about 0 to 0.5 to 0.7.
- A voice-activity gate is open 91 to 100 percent of the time in babble and competing-talker scenes.
- Consequently a permissive first stage that "catches almost every call" does not exist among the tested candidates, and the duty-cycle budget is unproven in crowds.

Before this ADR can be closed: repeat the screening with real recordings, add per-name reporting to the decision rule, and evaluate the options listed in the report (per-name calibration, enrollment-derived pronunciations, a per-name trained small model, a stronger verifier, a nearness cue).

## Consequences

- If S4 shows VAD → C1 is within budget, the system has two stages, not three. That is a success, not a deviation.
- Latency has a floor from the VAD hangover and the verifier window. It is measured as T_detect.
- The benchmark corpus and `tools/bench` are required before engine choice.
- The CPU budget must also hold on a low-end reference device (NFR-16, ADR-0009). If the chosen engine cannot meet it there, a lighter model tier or a stage-2 prefilter becomes necessary, even if the budget is met on a flagship phone.
- Keyword text to engine tokens is resolved (arbitrary typed text works through the model's BPE pieces, P49). Model licences (P27, P54) must be resolved before any model is bundled.
- We will know this was wrong if the duty cycle in real use is much higher than on the corpus (for example constant TV or crowd noise), which would mean adding stage 2 or adaptive gating.
