# 08 — Working with AI agents

Status: Draft v0.1 · 2026-10-20

This project is built by AI agents (Claude Code sessions) directed by the user. Agents start every session with no memory, so the repo has to carry the context. These rules keep sessions consistent.

## Sources of truth (read in this order)

1. `CLAUDE.md`: commands, layout, hard rules (kept short).
2. `docs/adr/`: decisions and their status. An `Accepted` ADR is binding.
3. `docs/plan/`: requirements, architecture, roadmap, spikes.
4. The task card for the current task.

Plans link to each other and do not duplicate content. When two documents disagree, the ADR wins, and the disagreement is fixed in the same PR.

## Task card (one per task; put it in the issue or the PR description)

```markdown
**ID:** M3-T2 · **Milestone:** M3 · **Needs HW:** no
**Goal:** one sentence of user-visible or system-visible outcome.
**Context:** links to the ADRs, plan sections, and spike results that matter.
**Scope:** files or modules to touch.
**Out of scope:** what not to do (prevents drift).
**Acceptance criteria:** testable statements.
**How to verify:** commands to run, or the hardware protocol to follow.
**Budget impact:** which NFR could this affect, and how it is checked.
```

## Definition of Ready (an agent must not start without this)

- Task card complete, dependencies done, ADRs it relies on are `Accepted`.
- If it needs a decision that is not an `Accepted` ADR: stop, write the ADR as `Proposed`, and ask the user.

## Definition of Done

See `06-quality-and-testing.md`. In short: acceptance criteria demonstrated, tests at the right layer, CI green, no unrecorded dependency, permission, or model, and docs and ADRs updated in the same PR.

## Branches, commits, PRs

- One task = one branch = one PR, sized to review in one sitting (≈ 400 lines).
- Branch names: `claude/<task-id>-<slug>` unless the session is assigned a specific branch.
- Commit messages: imperative summary, with the task ID in the body.
- PR description: task ID, what changed, how it was verified (paste benchmark or test output), ADRs touched, budget impact. A PR is created only when the user asks.
- Never push to `main` directly. Never rewrite history on a shared branch.

## Human-in-the-loop gates (the agent stops and asks)

- Choosing or changing a licence.
- Adding any dependency, model file, dataset, or permission.
- Changing a budget (needs an ADR).
- Anything that publishes (a release, F-Droid submission, a public post).
- Interpreting inconclusive hardware results.
- Anything that involves real people's voices or personal data.

## Hardware-in-the-loop protocol

1. The agent builds the spike or test build and writes a one-page runbook (steps, durations, what to note).
2. The user runs it on their device. The app exports a JSON report through the share sheet.
3. The user sends the report back (paste or file in the session). The agent analyses it and commits `docs/spikes/results/<id>.md` using the template in `04-spikes.md`.
4. The agent updates the ADR and the budgets, and lists what is still unproven.

Batch hardware work: collect everything a session needs so the user runs one session, not ten.

## Rules agents must follow

- Follow `Accepted` ADRs. To change one, write a superseding ADR first.
- Never add the `INTERNET` permission or any networking library.
- Never add a dependency or model without a `third-party.md` entry (name, version, licence, source URL, why).
- Keep the audio thread allocation-free and lock-free in the steady state.
- Never persist or log raw audio. The one exception is the Lab app's consented test clips (ADR-0012): never in `:app`, never committed to the repository; clips an owner sends stay outside the repository.
- Keep `CLAUDE.md` short. When a command changes, update it in the same PR.
- State clearly what was and was not verified. Never claim a hardware behaviour that was only inferred.
- Do not widen a task. Note follow-ups as new task cards.

## Parallelising agents safely

- Different modules are safe in parallel. Examples: `native/pipeline` and `core/policy`; `core/alert` and `core/audio`.
- Never run two agents on the same file or the same ADR at once.
- Interfaces (`AudioSource`, `Detector`, `AlertController`) are agreed in a design PR before parallel implementation begins.
- One agent owns each milestone's integration task.

## Context hygiene

- Put long-running facts in docs, not in chat.
- Spike results and decisions live in the repo, never only in a conversation.
- At the end of a session, the agent leaves a short handoff note in the PR description: done, not done, next step, open questions.
