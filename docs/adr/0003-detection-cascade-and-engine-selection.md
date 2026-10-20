# ADR-0003: Gate detection with VAD, keep stage 2 conditional, choose the engine by benchmark

- **Status:** Accepted (engine and whether stage 2 exists are decided by spike S4)
- **Date:** 2026-10-20
- **Deciders:** project owner, with Claude as architect
- **Evidence:** platform facts P22–P30; S4 pending

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

## Consequences

- If S4 shows VAD → C1 is within budget, the system has two stages, not three. That is a success, not a deviation.
- Latency has a floor from the VAD hangover and the verifier window. It is measured as T_detect.
- The benchmark corpus and `tools/bench` are required before engine choice.
- Keyword text to engine tokens (P26) and model licences (P27) must be resolved before any model is bundled.
- We will know this was wrong if the duty cycle in real use is much higher than on the corpus (for example constant TV or crowd noise), which would mean adding stage 2 or adaptive gating.
