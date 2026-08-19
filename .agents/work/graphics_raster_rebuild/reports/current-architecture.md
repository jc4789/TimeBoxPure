# Scope

- 対象は 2026-08-19 の作業ツリーにある Active Timer の魔法陣描画だけである。HUD、UI レイアウト、platform wrapper、魔法陣の採用済み半径・配置・色・六層の回転仕様は対象外とする。
- 調査経路は `ActiveTimerScene.render` → `NestedTimeboxInstrumentRenderer.render` → `ScaledProceduralRenderer` / `UiMappedEngineCanvas` → `AliasedVectorLayer` / `SoftwareEngineCanvas` → `SoftwareGraphics` → `IndexedFramebuffer` である。
- 制作コードは変更していない。この文書だけを追加した。
- 根拠の基準点は HEAD `709e267` と現行作業ツリーである。現行の座標領域切替は commit `1061ddc` (`ui scaleing fix/ ui refractor.`) で導入され、HEAD でも残っている。

# Confirmed

## 現在の到達可能な呼出経路

1. `SceneManager.render` は出力寸法から `UiRasterGrid.configure` を呼び、scene と HUD を描く前に UI の logical bounds を決める（`shared-engine/src/commonMain/kotlin/com/example/timeboxvibe/engine/core/SceneManager.kt:145-177`）。`PrimitiveDisplayProfile` は framebuffer 寸法を terminal 寸法から決める（`PrimitiveDisplayProfile.kt:4`）。
2. `ActiveTimerScene` は `NestedTimeboxInstrumentRenderer` を一個だけ保持し（`ActiveTimerScene.kt:30`）、`update` を転送し（`:58`）、UI の play-area から viewport 四辺と preferred center Y を計算して `render` へ渡す（`:121-127`）。ここは UI レイアウトの責任であり、魔法陣 raster scale の責任ではない。
3. `NestedTimeboxInstrumentRenderer.render` は、その logical viewport を `renderer.outputX/outputY` で framebuffer 座標へ変換する（`NestedTimeboxInstrumentRenderer.kt:142-146`）。次に framebuffer 上の短辺から radius を連続値で決め（`:147-154`）、`graphicsUnit = graphicsRadius / 162`、`graphicsGlyphBlock = round(graphicsUnit)`、`graphicsCell = 16 * graphicsUnit` を別々に作る（`:155-158`）。
4. 同 renderer は `beginGraphics` 後、円、文字環、tick、bead、polygon、陰陽、中央文字をすべて physical-output 座標で描く（`:191-363`）。外円は `drawAliasedCircle`（`:194`）、接線文字は `drawPolarGlyph`（`:200-207` 等）、陰陽は直接 `canvas.setPixel` と半円 helper（`:400-443`）、中央文字は generic UI text helper と `graphicsGlyphBlock` を併用する（`:448-603`）。最後に physical bottom を `UiRasterGrid.logicalY` で UI 座標へ戻す（`:365`）。
5. `ScaledProceduralRenderer` 内の `UiMappedEngineCanvas` は `graphicsDepth` により同じ `canvas` の width/height と全 primitive の意味を logical UI / physical output の間で切り替える（`ScaledProceduralRenderer.kt:6-119`）。`beginGraphics/endGraphics` はその mutable mode を公開し（`:178-184`）、`outputX/outputY` は `UiRasterGrid` の変換を重ねて公開する（`:186-188`）。
6. `ScaledProceduralRenderer.drawPolarGlyph` は ROM glyph を自前走査し、ink centroid を求め、固定小数点 inverse sampling で回転 glyph を直接 `canvas.setPixel` へ出す（`:367-567`）。star/polygon/tick helper は `canvas.drawLine` へ出す（`:591-760`）。円と回転半円だけは `AliasedVectorLayer` へ出す（`:809-860`）。これら polar helper の production caller は現状 `NestedTimeboxInstrumentRenderer` だけである。
7. `AliasedVectorLayer` は独自 Bresenham line/circle/fill/half-circle を持ち、最終的に `EngineCanvas.setPixel/drawRect` へ出す（`AliasedVectorLayer.kt:27-208,417-459`）。一方 `SoftwareEngineCanvas.drawLine/drawCircle` は float を再丸めして `SoftwareGraphics` へ渡す（`SoftwareEngineCanvas.kt:33-79`）。`SoftwareGraphics` も別の Bresenham line と midpoint circle を持つ（`SoftwareGraphics.kt:49-119`）。同じ shape に二つの raster authority がある。
8. `SoftwareGraphics` は `GlyphRasterizer` を保持する（`SoftwareGraphics.kt:36-39,158-169`）が、`ScaledProceduralRenderer` は別の ROM glyph raster loop を持つ。`GlyphRasterizer` 自体は canonical 16×16 glyph を `IndexedFramebuffer` に直接描く（`GlyphRasterizer.kt:3-49`）。glyph にも二つの raster authority がある。
9. 最終保存先 `IndexedFramebuffer` は 0..15 の palette index だけを保持し、UI、glyph cell、platform pixel、presentation を知らない（`IndexedFramebuffer.kt:3-43`）。`PaletteIndices.MAGIC_CIRCLE_PRIMARY/SECONDARY` は 13/14（`Pc98GraphicsHardware.kt:5-30`）。これは維持すべき正しい末端境界である。

## 確認できた責任分裂

- **幾何 scale が二つある。** 円・線・半径は連続 `graphicsUnit`、glyph bitmap は丸め済み `graphicsGlyphBlock` である（`NestedTimeboxInstrumentRenderer.kt:155-158`）。同一 graphic 内で geometry と text の pixel character が異なる段階で変化する。
- **座標領域が mutable hidden state である。** 同じ `renderer.canvas` が `beginGraphics` の深さにより logical / physical のどちらにもなる（`ScaledProceduralRenderer.kt:6-119,178-188`）。caller が領域を局所的に証明できない。
- **文字寸法 authority が三層に割れている。** `graphicsCell` が配置、`graphicsGlyphBlock` が bitmap pixel、generic `ProceduralTextRenderer` が wrap/height を決める（`NestedTimeboxInstrumentRenderer.kt:453-603`）。さらに tangent glyph は canonical cell center ではなく glyph ごとの ink centroid を回転中心にする（`ScaledProceduralRenderer.kt:402-428`）。
- **vector raster が重複する。** `AliasedVectorLayer` と `SoftwareGraphics` が line/circle を別実装し、`SoftwareEngineCanvas` がその境界で再度丸める。
- **glyph raster が重複する。** `GlyphRasterizer` と `ScaledProceduralRenderer.drawGlyphRaw/drawPolarGlyph/emitRotatedGlyph` が別実装である。
- **UI unit が graphics sampling へ漏れている。** `AliasedVectorLayer` は曲線 segment 数に private `U = 16` を使う（`AliasedVectorLayer.kt:16-24,462-470`）。physical-output drawing ではこの 16 は UI cell でも魔法陣の local source pixel でもなく、根拠のない sampling authority になる。
- **色 role の caller/graphic 分裂がある。** `ActiveTimerScene` は generic accent/danger/highlight を渡す（`ActiveTimerScene.kt:131-135`）一方、Nested は外層の多くを palette 13/14 に置換し（`NestedTimeboxInstrumentRenderer.kt:187-194`）、陰陽等では渡された色を使う。採用済みの実色は変えず、role の所有だけ Nested 側に一元化する必要がある。
- **到達経路には既存 allocation もある。** `firstOrNull`、毎 render の `IntArray(0)`、`SessionMacroDisplay.resolveMacro` の `Pair` destructuring が `ActiveTimerScene.kt:102-105` / `SessionMacroDisplay.kt:47-64` にある。これは raster 再設計とは別だが、「hot path allocation zero」を受入条件にする場合は同じ変更内で機械的に除去が必要である。

# Rejected

- **whole-scene low-resolution framebuffer は不採用。** UI と魔法陣を同じ低解像度 raster authority に入れ、最後に画面全体を拡大する案は単純に見えるが、ユーザー指定の「UI ≠ graphics」ownership を破る。UI/HUD の glyph grid、魔法陣の source grid、出力 framebuffer capacity が再び一つの解像度に結合され、今回の対象外である UI/HUD の見え方まで変える。既存 `UiRasterGrid` を graphics design authority にするため、現問題を別位置で再生する。
- **現在の continuous physical scaling + rounded glyph block は不採用。** geometry と text に異なる scale authority を残す。
- **各 helper へ `rasterBlock`/scale を追加する案は不採用。** 一個の presentation 決定を全 draw call へ複製し、所有を拡散する。
- **第二の circle/line/glyph rasterizer は追加しない。** local surface は追加 storage であって、追加 raster algorithm であってはならない。
- **`beginGraphics/endGraphics` の mutable coordinate mode は残さない。** explicit target を選ぶだけにする。
- **platform wrapper、HUD、scene layout、魔法陣半径・配置、色、rotation state は変更しない。** 出力解像度は capacity にだけ使い、graphic の内部形状や glyph source size の authority にしない。
- **ARGB、antialias、native color、fractional presentation は不採用。** 最終出力は 4-bit active palette index と整数 pixel block を維持する。

# Unknown

- viewport が local source 328×328 より小さい場合、block=1 で clip するか、明示的に魔法陣を非表示にするかは現行要件から確定できない。fractional shrink は採用しない。
- 最大 presentation block を現行 reference の 3 に固定するか、十分大きい viewport で 4 以上を許可するかは、採用済み Android 見本の「半径 486 を上限とする」契約確認が必要である。現行定数 `REFERENCE_GRAPHICS_RADIUS = 162 * 3`（`NestedTimeboxInstrumentRenderer.kt:31-34`）をそのまま契約と読むなら上限 3 である。
- local surface の未描画 pixel を BG で全面 blit してよいか、透明扱いで skip すべきかは明文化されていない。現行 `SceneManager.render` は scene 前に framebuffer を BG clear するため、現画面では BG skip が同じ見た目になる（`SceneManager.kt:145-167`）。
- 中央の可変文字列が固定 local source grid で全 locale に収まるかは実データ確認が必要である。これを理由に output-dependent glyph size を戻してはならない。

# Recommendation

## 一つの所有モデル

**`NestedTimeboxInstrumentRenderer` が固定 328×328 の magic-local indexed surface を唯一所有し、その surface を一回だけ整数 nearest-neighbor で main framebuffer に提示する。**

- local source cell = 16×16 source pixels。
- local source radius = 162 source pixels。
- boundary pad = 2 source pixels。
- local span = `(162 + 2) * 2 = 328`、center = `(164,164)`。
- 全円、線、dot、tick、polygon、陰陽、rune、中央文字をこの座標だけで rasterize する。通常 glyph は local scale 1、大表示 glyph は local scale 2。output 寸法は source geometry/text/alias character を変更しない。
- viewport の physical capacity から presentation block を一回だけ `min(availableWidth / 328, availableHeight / 328)` の整数で決める。現行 reference radius 486 を契約とする場合 `coerceIn(1,3)`。destination origin も一回だけ整数 snap/clamp する。
- `SoftwareEngineCanvas(328,328)` / `IndexedFramebuffer(328,328)` は renderer field として一度だけ生成し、毎 frame は `clear` と既存配列再利用だけにする。local surface の各 index を block×block の nearest rectangle として raw output へ blit する。blitter は rasterizer ではなく presentation である。
- この案は whole-scene low-res framebuffer と違い、UI の logical grid を一切変えない。固定 local surface の解像度は魔法陣だけの design authority、main framebuffer の解像度は配置可能量、`UiRasterGrid` は UI layout だけの authority になる。

## 完成後の唯一の経路

`ActiveTimerScene`（UI viewport だけ） → `NestedTimeboxInstrumentRenderer`（local 328 grid、layer composition、色 role、既存 phase） → canonical `AliasedVectorLayer` + canonical `GlyphRasterizer`（同じ local indexed target） → local `IndexedFramebuffer` → integer nearest blitter（origin/block 一個） → raw `SoftwareEngineCanvas` → main `IndexedFramebuffer`。

`UiMappedEngineCanvas` は UI primitive 専用であり、この経路には入らない。platform wrapper は main indexed framebuffer を従来どおり提示するだけである。

## 削除・置換・保持境界

| ファイル / class | 決定 | 正確な境界 |
|---|---|---|
| `ActiveTimerScene.kt` | 保持 | viewport 四辺と preferred center を渡す責任だけ保持。resolution/scale/radius/raster code を追加しない。既存 hot-path allocation だけは別の機械的 cleanup とする。 |
| `NestedTimeboxInstrumentRenderer.kt` | 置換・単一 owner | `outputX/Y`、continuous `graphicsUnit`、rounded `graphicsGlyphBlock`、`beginGraphics/endGraphics` を削除。固定 local geometry、preallocated local indexed surface、block/origin 一回計算、blit をここに集約。既存 layer order、半径、色、rotation fields は保持。 |
| `ScaledProceduralRenderer.kt` / `UiMappedEngineCanvas` | graphics 部分を削除 | `graphicsDepth`、`beginGraphics/endGraphics`、`outputX/outputY` を削除。mapper は常時 UI-only。magic-only の polar/tick/bead/star/polygon/rotated-glyph helpers を削除し、local renderer が canonical rasterizer を組み合わせる。raw output は state を切り替えない package-internal sink としてだけ提示する。 |
| `AliasedVectorLayer.kt` | canonical vector rasterizer として保持・整理 | line/circle/fill/arc/Bezier/half-circle の唯一の実装にする。target は palette-index pixel/rect sink。UI `U=16` による segment sampling を削除し、local pixel length による整数 sampling にする。 |
| `SoftwareGraphics.kt` | low-level writer に縮小 | pixel、clear、fillRect、dither、framebuffer write を保持。重複する line/circle algorithm を削除し、呼出側を canonical `AliasedVectorLayer` へ統一。 |
| `GlyphRasterizer.kt` | canonical glyph rasterizer として保持・拡張 | ROM 16×16 source の唯一の bit raster を担当。整数 source-pixel scale と canonical cell center を使う rotated emission を同じ実装へ集約。 |
| `ScaledProceduralRenderer` の glyph raw/polar code | 削除 | `drawGlyphRaw`、`drawCanonicalGlyphRaw`、ink-centroid scan、`emitRotatedGlyph` の重複 raster code を canonical `GlyphRasterizer` 呼出へ置換。wrap/layout は純粋な cell layout として再利用し、bitmap raster を複製しない。 |
| `SoftwareEngineCanvas.kt` | 保持・薄型化 | main/local framebuffer への共通 low-level adapter。float→int の二重 snap を raster 後に行わない。line/circle は canonical vector rasterizer へ委譲。 |
| `IndexedFramebuffer.kt`、`Pc98GraphicsHardware.kt` | 無変更で保持 | 4-bit active palette-index storage と現行 13/14 magic slots を最終 authority とする。 |
| `SceneManager.kt`、`UiShellLayout.kt`、`EngineCanvas.kt` の `UiRasterGrid` | UI 専用で保持 | UI logical layout/input のみ。magic-local geometry/raster/presentation block を一切所有しない。 |

この境界なら追加されるものは **preallocated local indexed storage と一個の integer blitter** だけであり、circle/line/glyph algorithm は増えない。output resolution は「328×328 を何整数倍で置けるか」だけを決め、魔法陣自身の相対寸法・glyph cell・alias pattern は常に同一になる。
