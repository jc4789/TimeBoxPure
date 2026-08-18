# Objective
Refine the new indexed framebuffer stack from the supplied Win32 screenshot: remove fixed 640x400 over-scaling, eliminate visible letterboxing, and stop Win32 flicker without reintroducing DPI/density/display scale policy.

# Constraints
- commonMain owns framebuffer dimensions and presentation math.
- Platform wrappers only report client dimensions, present the completed indexed frame, and forward input.
- No DPI/density ratio, per-widget scale, UI framework, external asset, or platform-owned layout.
- Preserve the 12-bit RGB / 16 active palette-index path and existing scene behavior except layout consequences of the corrected framebuffer dimensions.
- Preserve unrelated dirty worktree files.

# Plan
- [x] Confirm exact Win32 flicker and letterbox call paths.
- [x] Replace the fixed primitive profile with client-shaped adaptive framebuffer dimensions.
- [x] Make Win32 presentation one completed-frame blit with no background erase/black pre-clear.
- [x] Update deterministic common tests for mapping and pixel-budget behavior.
- [x] Pass common, Android, Win32, and runtime visual gates.

# Confirmed
- Screenshot client aspect is wider than 640:400; current aspect-fit creates large black side bars.
- Fixed 640x400 is enlarged several times, making each primitive pixel visibly oversized.
- Current Win32 present performs a black PatBlt immediately before StretchDIBits.
- Win32 class background erasure and calling present before BeginPaint provided two additional completed-frame erase paths.
- The supplied 2560x1368 client now derives a 1280x684 framebuffer; clients at or below the software budget remain 1:1.
- The completed frame covers the full client and pointer input uses the same inverse transform.
- Runtime inspection showed full-client rendering across Active Timer and Template Forge with no side bars or observed black intermediate frame.
- `:shared-engine:testDebugUnitTest`, `:shared-engine:winTest`, `:app:assembleDebug`, and `:shared-engine:linkDebugExecutableWin` pass.

# Rejected
- Hiding bars with decoration while retaining fixed 640x400; it preserves the wrong source geometry.
- Non-uniform full-window stretching; it removes bars by distorting the indexed image and input geometry.

# Unverified
- Long-duration flicker behavior on the user's exact monitor/refresh configuration.

# Next
User visual confirmation on the same maximized Win32 setup as the supplied screenshot.
