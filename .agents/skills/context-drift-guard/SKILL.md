---
name: context-drift-guard
description: Prevent context drift during long Codex tasks by keeping a compact task ledger and scoped subagent reports. Use for multi-step work, subagents, audits, implementation plans, interrupted work, or likely context compaction.
---

# Context Drift Guard

## Purpose

Preserve the task, constraints, evidence, and next action outside chat so work survives compaction and handoffs.

## Trigger When

Use when any applies:

- The task has multiple steps or subsystems.
- Subagents are used.
- Context compaction is likely.
- Work resumes after interruption.
- A plan, audit, or previous attempt may drift.

Skip for trivial single-step work.

## Working Memory

Create:

```text
.agents/work/<task>/STATE.md
.agents/work/<task>/reports/<workstream>.md
```

Do not commit these files unless asked.

`STATE.md` is the source of truth. Only the root agent edits it.

## STATE.md

Keep it short:

```md
# Objective
# Constraints
# Plan
- [ ] <step>
# Confirmed
- <fact + evidence>
# Rejected
- <hypothesis + reason>
# Unverified
# Next
<one action>
```

Use the plan as the task list. Update it before changing workstreams.

If evidence invalidates a step, record why and replace it. Do not silently drift or preserve a bad plan.

Mark only completed work complete. Attempted, delegated, assumed, or unchecked work remains unverified.

## Subagents

Use subagents to partition context, not to vote.

Give each a narrow, non-overlapping scope and a report path. Prefer read-only investigation unless implementation is explicitly delegated.

Each report contains only:

```md
# Scope
# Confirmed
# Rejected
# Unknown
# Recommendation
```

Every important claim must cite a file, symbol, commit, command, or specification.

The root verifies load-bearing claims before acting. Agent agreement is not evidence.

## Resume After Compaction

Before continuing:

1. Read applicable `AGENTS.md` files.
2. Read `STATE.md`.
3. Read only reports relevant to `# Next`.
4. Inspect `git status` and the diff.
5. Re-open the cited code before editing.

Repository state and the latest user instruction override stale notes.

## Completion

Before declaring completion, compare the result with the original request, inspect the diff, and record what was verified or remains unverified.

Passing tests, agent approval, or plausible code is not proof. Do not invent tests or expected values to validate new code.
