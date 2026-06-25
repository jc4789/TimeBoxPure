---
name: procedural-asset-law
description: Enforce zero external assets and prevent data smuggling. Use when adding visuals, fonts, audio, text content, configuration, resources, file IO, embedded arrays, or generated art/audio.
---

# Procedural Asset Law

## Purpose

The executable contains rules, not content files.

Prevent Codex from sneaking assets into the project.

## Trigger When

Use this skill when code or files involve:

- images
- fonts
- audio
- icons
- UI decorations
- text content
- JSON/XML
- resource folders
- file loading
- embedded byte arrays
- generated art
- generated LUTs

## Forbidden Runtime Assets

Reject:

```text
PNG
JPG
JPEG
SVG
TTF
OTF
WAV
MP3
OGG
JSON
XML
YAML
resource folders
runtime asset loading
```

## Smuggling Law

Do not replace a file asset with a giant embedded blob.

Forbidden:

```kotlin
val sprite = byteArrayOf(/* thousands of bytes copied from image */)
```

Allowed:

- compact mathematical shape definitions
- procedural generator rules
- small named LUTs with clear derivation
- ROM glyph bitmasks if part of engine law
- palette constants
- hardcoded vector commands that are human-auditable

## File IO Law

Core engine must not read files for runtime content.

Tooling may generate code, test fixtures, or reports, but runtime engine behavior must not depend on external content files.

## Output Format

```text
ASSET LAW CHECK:
New files:
Runtime file IO:
Embedded data:
Procedural representation:
Result: PASS / FAIL
```
