# ADR-0011: Release on F-Droid and GitHub first; keep the app Google-Play-compatible for later

- **Status:** Accepted (Play policy details are unverified and must be re-checked at M6)
- **Date:** 2026-10-20
- **Deciders:** project owner
- **Evidence:** the owner's choice (F-Droid and GitHub now, Google Play maybe in future). Play policy pages could not be fetched from the planning sandbox (P37–P39 are unverified).

## Context

The owner wants EchoCatch on F-Droid and GitHub Releases, and possibly on Google Play later. Play is the widest channel but has its own rules for background microphone services, battery-optimization exemptions, and disclosure of sensitive permissions. F-Droid builds from source and requires no proprietary components. Building for F-Droid first and bolting Play compliance on later would risk redesigning features such as onboarding and persistence.

## Options considered

1. **F-Droid and GitHub only.** Simplest, but narrower reach and contradicts the owner's future plan.
2. **Design for both from the start** and ship to F-Droid and GitHub first.
3. **Ship to Play first.** Reaches most users, but Play review risk would block the project before the core is proven.

## Decision

Option 2.
- v1.0 ships through F-Droid and GitHub Releases. Google Play is a later milestone with its own readiness task (M6-T7).
- From now on, design choices stay Play-compatible:
  - Use the current targetSdk, bumped yearly.
  - The microphone foreground service is user-initiated, always shows a notification, and the app shows a prominent in-app disclosure of what it listens to and that nothing leaves the device, before asking for `RECORD_AUDIO`.
  - **Avoid depending on `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`.** Onboarding sends users to the system battery settings screens instead (the exact intents are chosen in M5-T2). This reduces Play policy risk (P38) and keeps the permission list minimal (ADR-0007).
  - A privacy policy page (hosted from the repo) states that no data is collected or transmitted.
  - No Google Play Services dependency (ADR-0009), which Play does not require anyway.
- Store-specific decisions are deferred to M6-T7: signing keys and application ID strategy across stores, and the contributor-licensing approach.

## Consequences

- The distribution plan does not change core architecture.
- F-Droid builds from source and signs with its own key unless the build is reproducible, while Play re-signs releases with a Play-managed key. The same app may therefore have different signatures per store, which stops users from updating across stores. M6-T7 must decide this (for example one channel per application ID suffix, or a single signing strategy).
- GPL-3.0 and app-store terms can conflict when outside contributors hold copyright in the code. This is not legal advice. Before accepting external contributions that will ship on Play, decide on a contributor agreement or sign-off policy (M6-T7, `CONTRIBUTING.md`).
- We will know this was wrong if Play policy review rejects a microphone foreground service for this use case, in which case Play is dropped and F-Droid and GitHub remain the channels.
