# ADR-0008: Alert with a short pre-recorded cue from a pre-warmed AudioTrack, not text-to-speech

- **Status:** Accepted
- **Date:** 2026-10-20
- **Deciders:** project owner, with Claude as architect
- **Evidence:** latency budget NFR-2; S3 will measure T_alert and T_ear

## Context

The alert must be audible immediately and reliably. Android's TextToSpeech engine needs initialization, may depend on installed voices, may not be available on every device (for example F-Droid-only setups), and adds start-up latency. Bluetooth already adds an unavoidable output delay.

## Options considered

1. **TextToSpeech**: flexible (can say the name), but latency, availability, and consistency vary.
2. **Pre-recorded cue** (short chime or spoken phrase) loaded into memory, played from a pre-created low-latency `AudioTrack`: predictable and fast.

## Decision

Option 2. Cues are short original sounds that we author and release under a permissive licence (CC0), stored as small PCM assets and decoded once when the service starts. The `AudioTrack` is created when entering Listening and released when leaving it. The user can pick among a few cues and set cue volume.

## Consequences

- No dynamic speech such as saying the user's name back to them. If wanted later, it needs a new ADR and a latency-versus-availability analysis.
- Cue design (distinct enough to notice while music plays, short enough not to startle) is a small design task in M3-T1.
- We will know this was wrong if users cannot reliably notice the cue over loud music.
