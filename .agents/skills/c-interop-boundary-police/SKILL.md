---
name: c-interop-boundary-police
description: Protect Kotlin/Native C interop boundaries from dangling pointers, leaked nativeHeap allocations, unsafe StableRef callbacks, and invalid array pinning. Use for Win32, iOS/Metal, miniaudio, AVAudioEngine, AudioTrack, native buffers, and any kotlinx.cinterop code.
---

# C Interop Boundary Police

## Purpose

Protect the C/C++ boundary from:

- dangling pointers
- memory leaks
- GC-reclaimed callback state
- escaped scoped allocations
- invalid Kotlin array handoff
- native callbacks that outlive Kotlin objects

At the boundary, ownership and lifetime are laws.

## Trigger When

Use this skill when code touches:

- `kotlinx.cinterop`
- Win32
- Metal
- miniaudio
- AVAudioEngine
- AVAudioSourceNode
- AudioTrack native buffer bridges
- native callbacks
- C function pointers
- `CPointer`
- `COpaquePointer`
- `memScoped`
- `nativeHeap`
- `StableRef`
- `usePinned`
- `staticCFunction`

## Boundary Law

No pointer may cross the Kotlin/C boundary without explicit ownership.

Every crossing must be marked as one of:

```text
SCOPED      valid only inside memScoped
BORROWED    owned by platform/native API, not freed by Kotlin
NATIVE_OWNED allocated by nativeHeap or Arena, freed in shutdown
PINNED_SYNC Kotlin array pinned only for synchronous native call
STABLE_REF  Kotlin object handle passed as callback userdata
```

## memScoped Law

Use `memScoped` only for temporary, short-lived structs or pointers consumed synchronously inside the block.

Required:

```kotlin
memScoped {
    val rect = alloc<RECT>()
    GetClientRect(hwnd, rect.ptr)
    // consume values here
}
```

Forbidden:

```kotlin
val badPtr = memScoped {
    val device = alloc<ma_device>()
    device.ptr
}
// badPtr is dangling here
```

Flag persistent hardware structs allocated inside `memScoped`, including:

- `ma_device`
- audio engine state
- global window handles
- callback user data
- platform device/config objects retained after initialization
- anything passed to C for later use

## nativeHeap / Arena Law

Long-lived C structs must use:

- `nativeHeap.alloc`
- `nativeHeap.allocArray`
- custom Arena
- native API allocation with matching native API free

Every long-lived allocation requires an explicit shutdown path:

```kotlin
private val device = nativeHeap.alloc<ma_device>()

fun shutdown() {
    ma_device_uninit(device.ptr)
    nativeHeap.free(device.ptr)
}
```

Do not allocate long-lived native memory without a matching free/dispose/reset.

## staticCFunction / StableRef Law

`staticCFunction` must not capture Kotlin state.

If a C callback needs Kotlin object state:

1. Create a `StableRef`.
2. Pass `stableRef.asCPointer()` as userdata.
3. Recover with `asStableRef<T>().get()` inside callback.
4. Dispose the `StableRef` exactly once after native code can no longer call back.

Required shape:

```kotlin
private val stableSelf = StableRef.create(this)

nativeRegisterCallback(
    staticCFunction(::callbackThunk),
    stableSelf.asCPointer()
)

fun shutdown() {
    nativeUnregisterCallback()
    stableSelf.dispose()
}
```

Callback shape:

```kotlin
private fun callbackThunk(userData: COpaquePointer?) {
    val self = userData!!.asStableRef<Owner>().get()
    self.onCallback()
}
```

## usePinned Law

Use `.usePinned { pinned -> ... }` when passing Kotlin `IntArray`, `FloatArray`, `ShortArray`, or `ByteArray` memory to C for synchronous read/write only.

Required:

```kotlin
buffer.usePinned { pinned ->
    nativeWrite(pinned.addressOf(0), buffer.size)
}
```

Forbidden if C stores pointer after the call:

```kotlin
buffer.usePinned { pinned ->
    nativeRegisterBuffer(pinned.addressOf(0))
}
// C now holds an invalid pointer after pin ends
```

If C stores the pointer, use native-owned memory or a lifecycle-managed native buffer.

## commonMain Law

No C interop in `commonMain`.

Interop belongs in platform source sets only.

## Audit Checks

Search for:

```text
memScoped
nativeHeap.alloc
nativeHeap.allocArray
nativeHeap.free
staticCFunction
StableRef.create
asStableRef
usePinned
CPointer
COpaquePointer
addressOf
refTo
```

Flag:

- `CPointer` returned from `memScoped`
- pointer assigned to field/global inside `memScoped`
- persistent hardware structs allocated inside `memScoped`
- `nativeHeap.alloc` without a visible free path
- `StableRef.create` without dispose
- `staticCFunction` needing state but no StableRef userdata
- array `addressOf` / `refTo` passed to C without pinning
- `COpaquePointer` without ownership comment
- interop symbols in `commonMain`

## Output Format

```text
C INTEROP BOUNDARY CHECK:
File:
Pointer crossing:
Ownership class:
Lifetime owner:
Shutdown/free path:
Result: PASS / FAIL
```
