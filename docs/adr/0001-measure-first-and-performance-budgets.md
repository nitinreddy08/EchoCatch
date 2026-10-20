# ADR-0001: Decide by measurement and enforce performance budgets

- **Status:** Accepted (budget values are provisional until M0 spikes S4 and S6 complete)
- **Date:** 2026-10-20
- **Deciders:** project owner, with Claude as architect
- **Evidence:** none yet; this ADR defines how evidence is produced

## Context

The product's value depends on three qualities that are easy to claim and hard to deliver: speed (alert soon after the name), battery (always-listening must be cheap), and accuracy (few misses, few false alarms). The platform is full of device-specific behaviour (Bluetooth routes, focus handling, OEM service killing) that documentation does not settle (`docs/plan/02-platform-facts.md`).

## Options considered

1. **Decide by opinion and best practice**, then tune later. Fast to start, but risks building on assumptions that hardware disproves (for example the Bluetooth mic route).
2. **Decide by measurement.** Define budgets and measurement methods first, run spikes, and let results choose engines and defaults.

## Decision

Option 2. Specifically:
- Budgets are defined in `docs/plan/01-requirements.md` (NFR-1…14) with explicit definitions and measurement methods. Values marked ⚠ are placeholders. They are replaced with numbers from S4 and S6 and recorded in this ADR's revision log.
- Engine, audio-source, and focus decisions follow the decision rules in `docs/plan/04-spikes.md`.
- Budgets are release gates (`docs/plan/06-quality-and-testing.md`). Changing one requires a superseding or amended ADR that states the reason.
- No budget is set below the measurement noise floor established in S6.

## Consequences

- M0 comes before any product code, and some M0 work needs the user's hardware.
- Some decisions will remain open for a while (for example the default audio source).
- The benchmark and measurement tooling are first-class deliverables, not afterthoughts.
- We will know this was wrong if budgets keep being revised downward to match what was built.

## Revision log

| Date | Change |
|---|---|
| 2026-10-20 | Initial budgets (placeholders) |
