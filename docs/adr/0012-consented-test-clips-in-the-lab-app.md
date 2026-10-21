# ADR-0012: Record consented test clips in the Lab app only

- **Status:** Accepted (the owner asked to record clips in the app on 2026-10-21; the owner can withdraw this exception at any time)
- **Date:** 2026-10-21
- **Deciders:** project owner, with Claude as architect
- **Evidence:** [S4 first screening](../spikes/results/S4-first-screening-2026-10-21.md): every candidate detector was scored on synthetic voices only, and the report names real recordings as the next evidence.

## Context

ADR-0001 says to decide by measurement. The S4 screening used synthetic voices and approximated pronunciations, so its numbers say little about how real people sound when they call a name across a room, at a distance, over a TV. Real recordings of the owner's name are the evidence that moves the detector decision.

The project rule "never persist or log raw audio" protects the people who use EchoCatch. A voice recording is personal data, and this repository is public. The rule must stay true for the product while still allowing the project's own tester to record test material on purpose.

## Options considered

1. **Keep the rule absolute and use synthetic voices only.** Safest, but the detector decision stays weakly evidenced.
2. **Add recording to the EchoCatch app behind a debug switch.** Puts audio-saving code into the shipped program. Rejected.
3. **Record in the throwaway Lab app (`:spikes`), with consent controls.** The Lab app is never shipped and already has the microphone permission. Audio saving never reaches the product.
4. **Record with another recorder app and send the files.** Works, but the clips would have no consistent format, labels or timing cue, and each would need manual annotation.

## Decision

Option 3, under these rules:

- The recorder lives only in the Lab app. `:app` never stores or logs audio, and the "never persist or log raw audio" rule is unchanged for it.
- A clip is recorded only when the person holding the phone taps a Record button. Each clip is a fixed 8 seconds, started after an on-screen countdown, with a visible level bar. There is no background or automatic recording.
- Clips are stored in the Lab app's private storage. The screen shows how many exist; there are "delete this clip" and "delete all" buttons. Uninstalling the Lab app removes them.
- Clips leave the phone only when the owner taps Share and picks a destination. The app has no network permission (ADR-0007). Only the zip built for sharing is reachable by other apps, one share at a time.
- People other than the owner are recorded only if they agree. The screen says so.
- Clips are never committed to the repository (`*.wav` and the share zips are git-ignored). Agents keep received clips outside the repository, in the scratch data directory, and do not publish them. Results may be published as numbers and per-clip pass or fail, with speakers and names pseudonymised.
- Each clip is labelled by the recorder (kind, place, distance, speaker tag, and for name clips a cue window in which the name is said). The labels are approximate: they say when the person was prompted, not the exact second of the word.

## Consequences

- Real-voice evidence becomes possible without weakening the product's privacy promise.
- The project now handles personal data in its test process and must keep to the rules above. If a recording ever lands in the repository, remove it from history and say so.
- Timing labels are coarse. The scorer treats a name clip as a hit if the right name is detected from 0.3 s before the cue window to 1.5 s after it, so recall is slightly generous and false-alarm counts are exact.
- We will know this was wrong if testers are unwilling to record, or if clips cannot be handled safely. Then the fallback is option 1 or a smaller, owner-only set.
