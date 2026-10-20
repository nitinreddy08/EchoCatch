# ADR-0007: Ship without the INTERNET permission and with a minimal, CI-enforced permission list

- **Status:** Accepted
- **Date:** 2026-10-20
- **Deciders:** project owner, with Claude as architect
- **Evidence:** the project constraint "100 % offline, no cloud, no tokens"

## Context

EchoCatch listens to the microphone continuously. Users have to be able to trust that audio never leaves the device. "We don't upload anything" is only a promise unless the app is technically unable to.

## Options considered

1. **Allow `INTERNET` and promise not to use it.** Easy, but unverifiable by users.
2. **Never request `INTERNET`.** Android then blocks all network access for the app. Anyone can check the manifest.

## Decision

Option 2. The manifest is limited to an approved list: `RECORD_AUDIO`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MICROPHONE`, `POST_NOTIFICATIONS`, plus any added later after a written justification in `docs/plan/01-requirements.md` (candidates: `MODIFY_AUDIO_SETTINGS`, `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`). CI builds the APK, dumps its permissions, and fails on `INTERNET` or any unlisted permission (M0-T5). Models ship inside the APK. There are no network libraries, no analytics, no crash-reporting SDKs, and no Play Services.

## Consequences

- Model updates and new language packs ship with app releases, not as downloads.
- Crash and diagnostic information stays local (counters only, FR-10). Bug reports come from users manually.
- Distribution via F-Droid and GitHub Releases fits this. Google Play is out of scope for v1.
- We will know this was wrong if a core feature genuinely needs the network. That would need a new ADR, an explicit user opt-in, and a way to keep the core app offline.
