# ADR-0002: Treat the microphone as a pluggable audio source; pick the default by measurement

- **Status:** Accepted (the default source is decided by spike S1)
- **Date:** 2026-10-20
- **Deciders:** project owner, with Claude as architect
- **Evidence:** platform facts P13–P18 (`docs/plan/02-platform-facts.md`); S1 partly run on the OnePlus 11R and Buds 4 (P41–P43, P46–P48): a preferred-device route to the call link reported success but delivered silence, so the source layer must verify the signal; the call-route method is still untested

## Context

The original idea was to listen through the headset mic. Platform facts show that a classic Bluetooth mic needs the HFP/SCO link, which is narrowband and lowers audio quality (P13). LE Audio improves this (P14), but support depends on the phone and earbuds (P15, P16). Opening the headset mic probably also costs the earbuds' battery (P18). The phone mic avoids all of that but may be muffled in a pocket or bag.

## Options considered

1. **Hard-wire the headset mic.** Matches the original idea, but fails the product if S1 shows it ruins music or drains earbuds.
2. **Hard-wire the phone mic.** Simple and cheap, but gives up better pickup when the headset mic is good.
3. **Abstract the source** (`AudioSource` interface, `RouteMonitor`, `SourceSelector`) and choose the default from S1 data, with a user setting to override.

## Decision

Option 3. The interface and selector are specified in `docs/plan/03-architecture.md` §8. `close()` must release the route promptly so media quality is restored. The default for `auto` is set by the S1 decision rule: prefer headset mic only if music quality and earbud battery are acceptable; otherwise phone mic.

## Clarification (2026-10-20, from ADR-0009)

The phone mic is the universal Tier 0 baseline, and headset mic tiers always fall back to it. A per-headset profile created by the "Test my headset" wizard feeds `SourceSelector`. See ADR-0009 and `docs/plan/03-architecture.md` §11.

## Consequences

- A little more structure in `core/audio`, in exchange for being able to follow evidence.
- The capture preset (`VOICE_RECOGNITION`, `UNPROCESSED`, `MIC`) becomes part of source configuration and is chosen in S1/S4.
- Users may see different defaults on different devices. The UI must always show which source is active and why.
- We will know this was wrong if the selector grows so many device-specific branches that it needs its own subsystem.
