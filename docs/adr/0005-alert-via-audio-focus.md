# ADR-0005: Duck or pause other audio through audio focus, never by touching other apps' streams

- **Status:** Proposed (the focus type and attributes are decided by S3)
- **Date:** 2026-10-20
- **Deciders:** pending
- **Evidence:** platform facts P7–P12; S3 partly run on the OnePlus 11R (P44, P45): one combination, one media player, ducking heard once and a hard-to-hear alert once; the rest of the matrix and speech players are pending

## Context

An app cannot modify another app's audio stream without system privileges (P11), so "intercepting" playback is not possible. Android's audio focus lets us ask. Automatic ducking applies to music and video unless the player plays speech content or opts out (P7). Speech players are expected to pause (P8). The best attributes for a short alert are not stated by the docs (P10).

## Options considered

1. **Transient focus with ducking** (`AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK`). Music dips and the cue is heard. Speech apps may ignore it.
2. **Transient focus without ducking** (`AUDIOFOCUS_GAIN_TRANSIENT`). Players are asked to pause, which suits podcasts but interrupts music completely.
3. **Choose adaptively** by what is playing (via `getActivePlaybackConfigurations()`, P12): duck for music, pause for speech.
4. **Temporarily lower the stream volume** for players that ignore focus. Works around bad apps, but conflicts with Bluetooth absolute volume and risks failing to restore.

## Decision (proposed)

Option 3 as the default, with option 1 as the baseline if adaptive detection is unreliable. Option 4 is **off by default** and only offered if S3 shows it restores reliably. The final focus type, `AudioAttributes` usage, and content type come from the S3 matrix and its decision rule. Safety bias: if focus is not granted within ~100 ms ⚠, play the cue anyway. Always abandon focus when the cue ends or on any error, so media resumes.

## Consequences

- Some players will never duck. The docs will list known-ignoring apps (S3 output) so users know.
- `FocusController` is isolated behind an interface and tested with a fake `AudioManager`, because the real behaviour can only be verified on hardware.
- We will know this was wrong if the S3 matrix shows that no single approach covers the user's main players.
