# ADR-0004: Keep a lightweight foreground service alive and open the mic only when needed

- **Status:** Proposed (depends on S2 and S5)
- **Date:** 2026-10-20
- **Deciders:** pending
- **Evidence:** platform facts P1–P6, P21; S2 and S5 pending. First observation, 2026-10-21 ([M1 app on a OnePlus 11R](../spikes/results/2026-10-21-m1-app-first-run-oneplus-11r.md)): the service keeps listening with the screen locked, and swiping the app out of recent apps stops it and removes the notification, so this option cannot rely on the service surviving that on every phone

## Context

Android requires a `microphone` foreground service for background capture (P1). On Android 14+ it must be created while the app has a visible activity or through a documented exemption such as a notification interaction or widget (P2, P3). It cannot start from `BOOT_COMPLETED` (P4). The cheapest listening is no listening, so the mic should be open only while it is useful: a headset is connected and audio is playing (FR-3).

The open question is P5: whether a service started legitimately can open and close `AudioRecord` later without a visible activity.

## Options considered

1. **Mic open for as long as the service runs.** Simplest, but wastes battery whenever no media is playing.
2. **Event-driven mic inside a long-lived service** (`ArmedIdle` ↔ `Listening`). Best battery, but depends on P5.
3. **Start and stop the whole service on events.** Avoids P5, but runs into the background start restrictions (P2).

## Decision (proposed)

Option 2, falling back to option 1 with a stricter arming policy if S2 disproves P5. Start surfaces: the app UI and the notification action are assumed. Tile and widget are included only if S2 shows they work. The app does not try to auto-start after reboot. A reboot returns it to Disarmed, and the onboarding says so.

Event sources use cheap, permission-free APIs where possible: `AudioDeviceCallback` for headset presence and `AudioPlaybackCallback` for playing state. We avoid `BLUETOOTH_CONNECT` and `READ_PHONE_STATE`.

## Consequences

- The `ArmingPolicy` state machine (`docs/plan/03-architecture.md` §6) is the core of this design and is table-tested in pure Kotlin.
- Persistence depends on honest onboarding (battery optimization, per-OEM steps) as well as code (M5-T2).
- A process death means the user must re-enable. The app shows the true state and says so.
- We will know this was wrong if S2 or soak tests show that the service is routinely killed, which would force a rethink of the start and restart story.
