---
name: fm-dsp-audit
description: Build or audit allocation-free procedural FM synthesis. Use for renderAudioBlock, oscillators, phase accumulators, LUTs, envelopes, channels, audio callbacks, and no-sample audio generation.
---

# FM DSP Audit

## Purpose

Keep audio procedural, deterministic, and allocation-free.

No sample files. No runtime asset loading. No pretty Kotlin allocations in the audio callback.

## Trigger When

Use this skill when code touches:

- `renderAudioBlock`
- FM operators
- oscillators
- phase accumulators
- envelopes
- audio buffers
- LUTs
- audio output platform adapters
- clipping / mixing
- channel state

## Audio Laws



-Audio is generated.

-Our sound architecture has two main layers,    A Procedural, clean-room YM2608-based sound engine. And, a  Separate clean-room PMD-based MML language and performance model. 



Forbidden:

- WAV
- MP3
- OGG
- sample loading
- audio asset files
- allocation inside `renderAudioBlock`
- collections in audio callback
- coroutine scheduling in audio callback
- trig calls in hot loop unless explicitly approved
- Verification Architecture.
- Production snapshots, counters, inspection renderers, or diagnostic APIs.
## Required Pattern

State is preallocated:

```kotlin
val phase = FloatArray(operatorCount)
val freq = FloatArray(operatorCount)
val amp = FloatArray(operatorCount)
```

Callback writes into caller-provided buffer:

```kotlin
fun renderAudioBlock(out: FloatArray, frames: Int) {
    var i = 0
    while (i < frames) {
        // synth
        i++
    }
}
```

## Buffer Law

If passing `FloatArray` to native C, use the `c-interop-boundary-police` skill.

## Python Tooling

Python may be used outside runtime to:

- inspect waveform shape
- plot spectra
- generate LUT constants
- test phase continuity
- test clipping behavior

Python output must not become runtime asset files unless encoded as compact named constants.

## Output Format

```text
FM DSP CHECK:
Callback:
Allocations:
State buffers:
Sample asset leakage:
Native handoff:
Result: PASS / FAIL
```
