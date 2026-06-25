---
name: token-briefing
description: Create or update compact project briefing files so Codex can work without rereading the whole lawbook. Use after major architecture changes, after adding skills, before long tasks, or when context needs to be compressed.
---

# Token Briefing

## Purpose

Save context by keeping a compact, current project brief.

Do not stuff every battle scar into `AGENTS.md`.

`AGENTS.md` is the constitution.

`ENGINE_BRIEF.md` is the current map.

Skills are task rituals.

## Trigger When

Use this skill when:

- starting a large task
- summarizing the engine state
- updating skills
- compressing repeated instructions
- preparing Codex for future work
- reducing prompt length
- handing work between sessions

## Files To Maintain

Recommended:

```text
ENGINE_BRIEF.md
SKILL_INDEX.md
```

## ENGINE_BRIEF.md Contents

Keep it short:

```text
# ENGINE_BRIEF

## Current Engine Laws
- Canonical Unit:
- Color:
- Display:
- Asset:
- Platform Firewall:

## Hot Path Files
- path:
- path:

## Platform Wrappers
- Android:
- iOS:
- Win32:

## Known Failure Modes
- Compose hallucination
- Java in commonMain
- collection operators in hot paths
- byte-array asset smuggling
- diagonal staircase IMGUI bug
- C interop pointer escape

## Current Constants
- U:
- PALETTE_SIZE:
- MIN_SAFE_LOGICAL_WIDTH:
- MAX_SAFE_LOGICAL_WIDTH:

## Current Task Focus
- ...
```

## SKILL_INDEX.md Contents

Map task types to skills:

```text
UI layout bug -> canonical-unit-layout
Renderer bug -> palette-index-rendering + pixel-diff-regression
Audio feature -> fm-dsp-audit
Platform port -> platform-firewall-port + c-interop-boundary-police
Vector art -> aliased-vector-engine + pixel-diff-regression
Build command -> local-build-jbr
General audit -> engine-law-check
```

## Token Saving Rules

1. Do not duplicate full skill contents in briefs.
2. Do not paste the whole constitution into every prompt.
3. Quote only the relevant law before code.
4. Use script summaries instead of raw giant file dumps.
5. Store test results as compact hashes/summaries when possible.
