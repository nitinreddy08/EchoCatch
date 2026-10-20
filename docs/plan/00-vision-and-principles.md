# 00 — Vision and principles

Status: Draft v0.1 · 2026-10-20

## Vision

EchoCatch is a free, open-source Android app. While the user listens to music or a podcast on earphones, it listens to the surroundings and, when someone nearby calls the user's name, it briefly lowers or pauses the media and plays an alert in the earphones.

It must be fast, seamless, and use as little battery as physics allows. Everything runs on the device, offline.

## Reach

EchoCatch should work on most Android phones and versions (Android 8.0 and newer) with any non-Apple TWS earbuds. The promise is precise (ADR-0009): a universal baseline that always works, plus detected upgrades for earbuds whose mic and Bluetooth stack allow it. Compatibility is published honestly, with tested and user-reported results kept separate.

## Non-goals (v1)

- Not a safety-certified device. The app never claims to guarantee an alert (README and onboarding must say so).
- No cloud, accounts, analytics, or telemetry.
- No speaker identification and no "is this person talking to me" intelligence. It detects the name only.
- No wake phrases other than the user's own name and up to two nicknames.
- No iOS, no Wear OS, no Google Play distribution (see ADR-0007 and `09-open-decisions.md`).
- Not a hearing aid and not a general transparency or pass-through mode.

## Principles (in priority order)

1. **Measure before deciding.** Choices about engines, audio sources, and parameters are settled by benchmarks and hardware spikes, not opinions (ADR-0001).
2. **The mic is closed unless it is needed.** The cheapest listening is no listening. Every wake-up, buffer, and inference must justify itself (ADR-0003, ADR-0004).
3. **Offline is provable.** The app has no `INTERNET` permission, so anyone can verify the claim (ADR-0007).
4. **Never break the user's media or calls.** On any error the app fails quiet: it releases audio focus and the mic route and stops. It must never leave music muted or degraded.
5. **Cooperate with other apps.** We negotiate through Android's audio focus instead of fighting other apps (ADR-0005).
6. **Privacy by construction.** Audio exists only in a small in-RAM ring buffer. Nothing is written to disk or sent anywhere in v1.
7. **Small and boring.** Few dependencies, few permissions, plain Kotlin and C++, and every dependency licence-checked.

## How we know it is "so good"

Quality is defined by budgets in `01-requirements.md`, measured by the tools in `06-quality-and-testing.md`, and enforced as release gates. A feature that cannot be measured against a budget is not done.
