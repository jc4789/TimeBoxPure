# Scope

`2026-08-17` 時点の `D:\Programes\TimeBox` を読み取り専用で監査し、次を棚卸しした。

- Gradle module / Kotlin Multiplatform target / 実在する verification・compile・link task
- 現行 renderer / UI / glyph / touch scaling / Android・Win32 presentation の到達経路
- 現在存在する renderer/UI テスト
- 再構築後の `commonMain` indexed primitive framebuffer、glyph rasterizer、presentation の逆入力変換、IMGUI hitbox、scene migration、Android/Win32 build を証明する最小検証スイート

production code は変更していない。実行した Gradle command は task inventory (`:shared-engine:tasks --all`, `:app:tasks --all`, `--version`) のみで、compile/test/link はまだ実行していない。

# Confirmed

## Build topology

- root は `:app` と `:shared-engine` の2 module。根拠: `settings.gradle.kts` の `include(":app")`, `include(":shared-engine")`。
- `shared-engine` は Android、iOS (`iosX64`, `iosArm64`, `iosSimulatorArm64`)、Windows (`mingwX64("win")`) を持つ。根拠: `shared-engine/build.gradle.kts` の `kotlin { ... }`。
- `commonTest` は `implementation(kotlin("test"))` を既に持つ。このため新しい検証依存関係や screenshot framework は不要。
- Windows executable link は `compileMiniaudioWin` に依存する。根拠: `tasks.matching { it.name.startsWith("link") && it.name.contains("Win") }.configureEach { dependsOn(compileMiniaudioWin) }`。
- task inventory で以下の task が実在することを確認した。
  - `:shared-engine:testDebugUnitTest`
  - `:shared-engine:winTest`
  - `:shared-engine:compileCommonMainKotlinMetadata`
  - `:shared-engine:compileDebugKotlinAndroid`
  - `:shared-engine:compileKotlinWin`
  - `:shared-engine:linkDebugExecutableWin`
  - `:app:compileDebugKotlin`
  - `:app:assembleDebug`
  - `:app:connectedDebugAndroidTest`
- repository wrapper は Gradle `9.4.1`。Android 用の指定 JBR `D:\Programes\Android Studio\jbr\bin\java.exe` は存在する。

## Existing tests

- `shared-engine/src/commonTest/kotlin` は directory だけで、監査時点では Kotlin test file が0件。
- `app/src/test` は存在せず、local JVM unit test は0件。
- 唯一の Kotlin test は `app/src/androidTest/java/com/example/ExampleInstrumentedTest.kt` の template context test 1件で、renderer/UI/scaling/framebuffer を一切検証しない。
- よって現時点で indexed pixel、glyph rasterization、input inverse mapping、hitbox、scene rendering、Android/Win32 presentation の regression coverage は0。

## Current reachable visual/input path

- Android render: `Pc98SurfaceView.surfaceChanged` -> `DisplayScalePolicy.deriveScale` -> `AndroidEngineCanvas` + `ScaledProceduralRenderer` -> render thread `Canvas.scale(scaleFactor, scaleFactor)` -> `SceneManager.render` -> active `Scene.render`。根拠: `Pc98SurfaceView.kt:63-102`, `256-293`。
- Android input: `MotionEvent` physical coordinates -> `event.x / currentScaleFactor`, `event.y / currentScaleFactor` -> fixed touch queue -> `SceneManager.update` -> `drainTouchBuffer` -> HUD and active scene。根拠: `Pc98SurfaceView.kt:110-133`, `325-373`; `SceneManager.kt:114-157`, `255-275`。
- Win32 render: client size + DPI -> `DisplayScalePolicy.deriveScale` -> `Win32EngineCanvas` physical ARGB `IntArray` -> `SceneManager.render` -> `StretchDIBits`。根拠: `Win32Host.kt:142-202`, `291-301`; `Win32EngineCanvas.kt:14-52`。
- Win32 input: WM mouse coordinates -> `raw / scaleFactor` -> fixed touch queue -> the same `SceneManager.update` path。根拠: `Win32Host.kt:114-139`, `357-368`。
- 現行 Android は host `Canvas` に scene primitives を直接描く。一方 Win32 は platform class が primitives を rasterize し、同時に palette を ARGB に展開する。共通の完成済み indexed framebuffer はまだ存在しない。
- `EngineCanvas` は platform-facing primitives と `density`, `presentationScale`, `drawPhysicalRect` を公開している (`EngineCanvas.kt:93-113`)。`ScaledProceduralRenderer` の glyph path は presentation scale を読み、physical rectangle を直接要求する (`ScaledProceduralRenderer.kt:67-80`, `107-175`)。したがって現在は glyph rasterization と presentation ownership が分離されていない。
- 色 contract の現行 source evidence は `Pc98GraphicsHardware.PALETTE_SIZE == 16` と `ShortArray(16)` (`Pc98GraphicsHardware.kt:32-55`)。Android/Win32 は各 12-bit RGB component を nibble replication で8-bitへ展開する (`AndroidEngineCanvas.kt:49-60`; `Win32EngineCanvas.kt:195-204`)。
- `Scenes.kt` は監査時点で約 175 KB、3,000行超で、`Scene` interface と複数 scene singleton を同居させる。主要宣言位置は `ActiveTimerScene:44`, `TemplateCustomizerScene:903`, `TemplateForgeScene:1221`, `SettingsScene:2193`, `EntropyScene:2677`, `BlockOverlayScene:3320`。Scene registry は `SceneManager.kt:45-51`。
- 現行 hitbox は UI widget が所有せず、render geometry と別に scene/HUD が再計算して `TouchColliderManager.checkAABB` を呼ぶ。例: `RetroHudComponent.kt:194-220`。これは IMGUI の「同じ rect が draw と hit test の唯一の authority」という性質を検証できる既存 seam がないことを意味する。

# Rejected

- platform screenshot / Roborazzi を主 regression oracle にする案。完成 framebuffer 自体が決定論的な indexed data になるため、platform rasterization・density・device差を混ぜる必要がない。PNG は必要なら failure artifact のみにする。
- Android instrumentation test を renderer correctness の中心にする案。遅く、device を要求し、commonMain と Win32 の同一性を証明しない。
- すべての scene について巨大 golden buffer を保持する案。変更意図まで固定し、review が困難になる。primitive/glyph は小さな exact buffer、scene は代表 frame の hash + anchors + palette invariants に限定する。
- test 専用 renderer、diagnostic draw command recorder、screenshot infrastructure を production に追加する案。`IndexedFramebuffer` の公開 read-only pixels/dimensions がそのまま oracle でなければならない。
- platform wrapper 個別に primitive correctness を再テストする案。primitive rasterization は commonMain の責任。platform tests は presentation mapping/format と compile/link boundary のみにする。
- `colorIndex and 0x0F` による silent wrapping を期待値にする案。4-bit active palette contract の違反を隠す。commonMain draw boundary では index `0..15` を受け入れる明示 contract とし、範囲外は test で失敗させる（release hot path の具体的 enforcement 方法は実装 authority に委ねる）。
- 現存の `ExampleInstrumentedTest` を今回の合否 gate とする案。renderer/UI contract に到達しないうえ、assertion は `com.example` で、`app/build.gradle.kts` の `applicationId` は `com.aistudio.timeboxvibe.pxqyva`。今回の stack の証拠にならない。

# Unknown

- 再構築後の実際の class/package 名、primitive framebuffer の固定 logical width/height、letterbox/crop policy はまだ production tree に存在しない。下記 test 名は責任単位を示す仮名であり、実装 API に合わせる。
- pointer が presentation 外の letterbox/pillarbox にある場合の contract（reject、clamp、sentinel）が未確定。inverse mapping test は選ばれた挙動を明示的に固定する必要がある。
- scene の visual golden を固定するための deterministic timer/UI state fixture が未確定。`TimerActions` の既存非visual behavior を変えず、代表 scene に必要な state を注入できる seam が必要。
- scene migration 完了時に旧 `Scenes.kt` を削除するか、`Scene` contract/catalog の小ファイルとして残すかは未確定。検証上必要なのは旧 singleton と新 registry の二重登録がなく、全 `SceneId` が exactly one implementation に解決されること。
- indexed framebuffer の pixel storage が `ByteArray` か nibble-packed かは未確定。検証は storage detail ではなく、各 logical pixel の observable palette index と dimensions を対象にする。
- `testDebugUnitTest` と `winTest` は inventory で存在を確認したが、commonTest が空の現在は実行していない。新 suite 実装後に両方を実行して初めて cross-target の test evidence になる。

# Recommendation

## Smallest strong suite

追加先は原則 `shared-engine/src/commonTest/kotlin/...` の6 test file。production test hooks や外部ライブラリは追加しない。

1. `IndexedFramebufferTest`
   - 小さい buffer（例 `8x8`）を作り、`clear`, clipped `pixel/rect/line` の結果を `ByteArray` 相当の exact index sequence で比較する。
   - width/height/pixel count を確認する。
   - edge clipping（負座標、right/bottom overflow）が範囲外 write を起こさないことを guard value ではなく最終 buffer 全体で確認する。
   - 全 pixel が `0..15`、範囲外 palette index が拒否されることを確認する。
   - 同じ commands を2 bufferに描き、同じ stable 64-bit hash と changed-pixel count になることを確認する。hash function は test source 内の単純 FNV-1a 等でよく、production utility は不要。

2. `GlyphRasterizerTest`
   - retained `ShinonomeFont.glyphFor('A')` の16 row bitsを、背景 index 0 / foreground index 6 の `16x16` framebuffer に scale 1 で rasterizeし、各 row の bit と各 output pixel を直接比較する。
   - non-zero origin と clip rect を1ケース追加し、glyph が clip 外を書かないことを全 buffer exact comparison で確認する。
   - glyph rasterizer が presentation scale/density/platform type を引数にも状態にも持たないことは compile-time API shape と test construction で証明する。

3. `PresentationTransformTest`
   - commonMain の純粋な presentation transform に対し、framebuffer `320x240` から physical `1280x720` など非同一 aspect ratio の transform を作る。
   - expected integer scale/viewport offset/extent を確認する。
   - framebuffer corner/center/last-pixel の forward mapping後に inverse mappingして元座標へ戻ることを確認する。
   - viewport外 pointer の chosen contract と、right/bottom exclusive boundary を固定する。
   - Android/Win32 はこの同じ transform result を使うだけにし、platform固有の scale math はテスト対象ではなく削除対象にする。

4. `ImGuiHitboxTest`
   - 1 frame の `beginFrame(pointer)` -> `button(id, rect, ...)` -> `endFrame` を直接駆動する。
   - rect 内部、left/top inclusive、right/bottom exclusive、1 pixel 外部を確認する。
   - DOWNで active capture、MOVEで状態維持、UP-insideで click exactly once、UP-outside/CANCELで clickなしを確認する。
   - overlap 1ケースで決めた ownership order（通常 last submitted/topmost）が exactly one widget に入力を渡すことを確認する。
   - 最重要: test が渡す rect は button draw に使われた同じ `RectI` instance/valueであり、別 hitbox 計算 APIを使わない。

5. `SceneCatalogTest`
   - `SceneId.entries` の各値が registry/catalog で exactly one scene に解決され、missing/duplicate がないことを確認する。
   - 各 scene を deterministic fixture で1 frame、共通 framebufferへ render し、例外なし、dimensions不変、全 index `0..15` を確認する。
   - visual regression はまず `ActiveTimerScene` 1件だけを authoritative golden とし、固定 state で expected hash、changed-pixel count、背景以外の pixel 数、2〜4個の anchor index を確認する。これはユーザー指定の「最初に Active Timer 一画面」を強く証明し、全 scene の巨大 golden は避ける。
   - scene switch/input smoke を1ケース追加し、IMGUI click result -> scene command -> catalog switch が成立することを確認する。platform touch codeは介さない。
   - migration structural gate として production source search を別 script にせず review/CI commandで行う: 旧 `ScaledProceduralRenderer`, `DisplayScalePolicy`, `TextRasterScale`, platform primitive draw calls、および巨大 `Scenes.kt` の scene implementations が残っていないことを `rg` で確認する。この search は test infrastructure としてコード化しない。

6. `Palette12BitTest`
   - active palette entry countが16であることを確認する。
   - `0x000`, `0xABC`, `0xFFF` の12-bit RGB component decodeを exact comparison する。
   - common framebuffer pixel は palette index のままで、palette entryを変えても pixel buffer/hashが変わらず、presentation-expanded outputだけが変わることを確認する。これが graphics と presentation の境界を証明する。

## Acceptance commands

まず最速の commonMain-on-JVM regression gate:

```powershell
$env:JAVA_HOME="D:\Programes\Android Studio\jbr"; .\gradlew.bat :shared-engine:testDebugUnitTest
```

同じ commonTest を Kotlin/Native Windows でも実行し、platform-independent APIであることを確認:

```powershell
$env:JAVA_HOME="D:\Programes\Android Studio\jbr"; .\gradlew.bat :shared-engine:winTest
```

Android application 全体の compile/package gate:

```powershell
$env:JAVA_HOME="D:\Programes\Android Studio\jbr"; .\gradlew.bat :app:assembleDebug
```

Win32 terminal の Kotlin/Native + C interop + miniaudio object + executable link gate:

```powershell
$env:JAVA_HOME="D:\Programes\Android Studio\jbr"; .\gradlew.bat :shared-engine:linkDebugExecutableWin
```

最終一括 gate（上記4責任を1 invocationで実行）:

```powershell
$env:JAVA_HOME="D:\Programes\Android Studio\jbr"; .\gradlew.bat :shared-engine:testDebugUnitTest :shared-engine:winTest :app:assembleDebug :shared-engine:linkDebugExecutableWin
```

## Required evidence format

最終報告では最低限、代表 Active Timer frame について次を出す。

```text
PIXEL DIFF:
Scene: ACTIVE_TIMER
Expected hash: <fixed after intentional approval>
Actual hash: <actual>
Changed pixels: 0
Invalid palette indices: 0
Framebuffer dimensions: <contract width>x<contract height>
Result: PASS
```

platform boundary は次の証拠で十分。

```text
PLATFORM FIREWALL CHECK:
Platform: Android / Win32
Allowed responsibility: present completed indexed framebuffer; forward raw input
Core responsibility preserved: transform, rasterization, palette meaning, UI, scene layout
Leakage found: none
Result: PASS
```

この構成なら correctness の大部分は高速な6個の commonTest fileで証明し、Android/Win32には重複した visual testを置かず、compile/linkで dumb-terminal boundary を確認できる。
