# Scope

- `HEAD`（`709e26757770f39865d1292ddad7681d79d5cd97`）までの `commonMain` 描画履歴を、魔法陣の座標・文字・エイリアス生成に限定して追跡した。対象は `EngineCanvas.kt`、`ScaledProceduralRenderer.kt`、`NestedTimeboxInstrumentRenderer.kt`、`PrimitiveDisplayProfile.kt`、`GlyphRasterizer.kt`、`SoftwareGraphics.kt`、`ActiveTimerScene.kt`、`AliasedVectorLayer.kt`。
- 調査コマンドは `git log --follow --format=... -- <file>`、`git show <commit>:<file>`、`git show --unified=8 <commit> -- <file>`、`git blame -L <range> 709e267 -- <file>`、`rg -n <symbol> shared-engine/src/commonMain/kotlin`、`git diff --numstat`。
- 制作コードは編集していない。調査中に既に変更されていた `AliasedVectorLayer.kt`、`EngineThemes.kt`、`NestedTimeboxInstrumentRenderer.kt` はユーザー所有の変更として保持した。

# Confirmed

1. **固定16×16文字を先に作る旧基準は `15a3c4f657f0b23b44a1c671d865e47128a99869` に存在した。** `ScaledProceduralRenderer.drawPolarGlyph` は ROM の16×16ビットを固定セル中心 `(7.5, 7.5)` の周りで回し、再利用 `rotatedGlyphBuffer[16*16]` に整数スナップしてから、`emitRotatedGlyph` が各確定ピクセルを `scale × scale` に複製した。したがって文字の回転マスクは拡大前の固定16×16空間で決まり、出力解像度は文字形を再サンプリングしていなかった。証拠: `git show 15a3c4f:shared-engine/src/commonMain/kotlin/com/example/timeboxvibe/engine/core/ScaledProceduralRenderer.kt` の `rotatedGlyphBuffer`、`drawPolarGlyph`、`emitRotatedGlyph`。
2. **最初の表示依存導入は UI 用だった。** `30f0b34b6e5b23402659fb249da78f7540c818af` は `EngineCanvas.kt` に `DisplayScalePolicy.deriveScale(physicalWidth, physicalHeight, platformDensity)` と `EngineCanvas.density` を追加した。この段階は UI/表示スケールの結合であり、魔法陣固有の source raster を出力寸法から導出する変更ではない。証拠: `git show 30f0b34:.../EngineCanvas.kt` の `DisplayScalePolicy`。
3. **文字そのものが表示スケールの権威下に入った最初の変更は `df35674058fa66073dbe77138c4ee3602d32953a`。** `TextRasterScale` が `presentationScale` と `basePhysicalPixelScale` を保持し、`ScaledProceduralRenderer.measureTextWidth/Height`、glyph origin/clip、`drawPhysicalRect` を物理表示スケールから算出した。`NestedTimeboxInstrumentRenderer` の中央表示寸法もこの共通測定へ接続された。証拠: `git show df35674:.../EngineCanvas.kt` の `TextRasterScale` / `presentationScale` / `drawPhysicalRect`、同コミット `ScaledProceduralRenderer.kt` の `TextRasterScale.configure` と `drawGlyphRaw`。
4. **`5d246f9039db26ddb524fa2872f655a661b662ea` はプラットフォーム primitive 描画を共通 indexed framebuffer へ集約した。** `IndexedFramebuffer`、`SoftwareEngineCanvas`、`SoftwareGraphics`、`GlyphRasterizer`、`PresentationTransform` と Android/Win32 presenter が導入され、`TextRasterScale` の物理矩形経路は除去された。当時の `PrimitiveDisplayProfile` は端末の半解像度を優先しピクセル予算内で framebuffer を選んだが、まだ魔法陣専用の固定 source surface ではなかった。証拠: `git show --stat 5d246f9`、`git show 5d246f9:.../PrimitiveDisplayProfile.kt`。
5. **魔法陣の source raster/text/線幅を出力解像度へ従属させた決定的コミットは `1061ddc303370aeb6c919ad298462ee560a995c1`。**
   - `PrimitiveDisplayProfile` は primitive framebuffer を端末の実ピクセル寸法と同一に変更した（`primitiveWidth = outputWidth`、`primitiveHeight = outputHeight`）。
   - `EngineCanvas.kt` の `UiRasterGrid` は出力寸法から `pixelBlock` と UI 論理寸法を導出した。
   - `ScaledProceduralRenderer.kt` の `UiMappedEngineCanvas.graphicsDepth` / `beginGraphics` / `endGraphics` は、同じ renderer 内で UI 座標と出力ピクセル座標を切り替える第二の権威を作った。
   - `NestedTimeboxInstrumentRenderer.render` は `renderer.outputX/outputY` で viewport を出力座標へ変換し、`outputShortAxis` から `graphicsRadius`、`graphicsUnit`、`graphicsGlyphBlock`、`graphicsCell`、`thin` を導出した。`GRAPHICS_SOURCE_RADIUS = 162f` に対し `GRAPHICS_REFERENCE_RADIUS = 486f` とし、コメントも Android 基準の block 3 / radius 486 / glyph 48 を明記した。
   - その後 `renderer.beginGraphics()` 内で全ての線、円、文字、中央表示を直接メイン出力 framebuffer に描いた。よって、端末サイズが魔法陣の半径、線幅、文字の source block、サンプリング密度まで決めるようになった。
   証拠: `git show --unified=8 1061ddc -- .../EngineCanvas.kt .../PrimitiveDisplayProfile.kt .../ScaledProceduralRenderer.kt .../NestedTimeboxInstrumentRenderer.kt .../ActiveTimerScene.kt`。
6. **回転文字のエイリアス形状と回転支点を final scale 依存へ変えたのは `709e26757770f39865d1292ddad7681d79d5cd97`。** `rotatedGlyphBuffer` を削除し、`drawPolarGlyph` が文字ごとの ink centroid を計算、`emitRotatedGlyph` が `rasterSize = 16 * scale` の最終領域を `inverseScale` で逆サンプルする方式へ変更した。したがって `scale` が変われば最終ラスタの標本位置、太さ、エイリアスが変わる。証拠: `git show 709e267:.../ScaledProceduralRenderer.kt` の `inkPixelCount` / `sourceCenterX/Y` / `inverseScale` / `rasterSize`、および `git blame -L 402,563 709e267 -- .../ScaledProceduralRenderer.kt`。
7. **現在の到達経路は一続きで確認できる。** `ActiveTimerScene` の UI viewport → `NestedTimeboxInstrumentRenderer.render` → `renderer.outputX/outputY` → 出力寸法由来の `graphicsRadius/unit/glyphBlock` → `renderer.beginGraphics` → `UiMappedEngineCanvas` の出力直書き → `ScaledProceduralRenderer` / `AliasedVectorLayer` → 出力寸法と同じメイン `IndexedFramebuffer` → platform presenter。プラットフォームは既に完成 framebuffer の提示だけを行い、現在の欠陥は `commonMain` の graphics source authority にある。
8. **ベクターと色の現状。** `AliasedVectorLayer` は float 入力を `roundToInt` して整数ラスタへ落とし、色は palette index で渡す。外部画像資産はこの経路にない。一方、ラスタ対象と文字サンプル密度が出力依存なので、同じ魔法陣状態から同じ source pixel mask を得る要件には不合格。12-bit palette / 同時16色の境界自体を壊す証拠はない。
9. **保持対象の挙動。** 現行 `NestedTimeboxInstrumentRenderer` には6個の独立した相対回転速度 `4, 3, 12, -15, 20, 40 deg/s` があり、中央 readout も同 renderer の魔法陣内容である。証拠: 現行 `NestedTimeboxInstrumentRenderer.kt:39-44` と `renderCenterReadout` 呼び出し。

# Rejected

- 出力解像度、window size、orientation、DPI、density、`UiRasterGrid.pixelBlock`、`presentationScale` のいずれかを、魔法陣の source 半径・source glyph block・source stroke・文字測定・エイリアス標本位置の入力にする設計。
- `graphicsGlyphBlock` や per-call `rasterBlock` を追加して現行の出力直書きを補修する方法。これは同じ二重座標系を温存する。
- `1061ddc` や `709e267` の wholesale revert。両コミットには scene 所有権整理や不要 demoscene 削除も含まれ、履歴を戻すだけでは固定 graphics source 境界を作れない。
- platform wrapper に文字、線、円、layout、graphics scale の判断を戻すこと。現在の完成 indexed frame の提示契約は維持する。
- 現行 `ScaledProceduralRenderer` の手書き glyph loop、`GlyphRasterizer`、`SoftwareGraphics` の glyph loop に加え、さらに別の並行 rasterizer を足すこと。
- alpha/native color や中間 ARGB でエッジを作り、それを palette へ量子化すること。source は最初から palette index `0..15` のみとする。

# Unknown

- 履歴だけでは固定 local surface の正確な外形寸法を確定できない。`radius = 162` と現行リング配置は作業 STATE で保持対象だが、`162` 自体は出力基準 block 3 を導入した `1061ddc` で追加された。承認済み半径を中心に必要 pad を足した surface extent は、実装前に設計責任者の既存指定で確定する必要がある。
- 回転文字の固定支点を旧 `(7.5, 7.5)` とするか、別の承認済みセル支点とするかは履歴だけでは確定しない。ただし文字ごとの ink centroid と destination-scale 依存の支点・サンプルは禁止条件と両立しない。
- local surface からメイン indexed framebuffer への destination rectangle が整数倍率限定か、任意サイズの nearest-neighbor かは presentation/layout 契約の選択事項。どちらでも、その倍率を source raster へ逆流させてはならない。
- 未コミットの palette 復元内容は履歴監査の結論対象外。削除・上書きせず、実装時も保持する。

# Recommendation

**置換境界は魔法陣全体を一個の固定 graphics-local indexed surface とする。** 魔法陣 renderer が承認済み source 半径・リング配置・16×16 ROM glyph cell・6回転状態・中央 readout を所有し、出力とは無関係な固定整数座標で一回だけ `IndexedFramebuffer` にラスタする。完成した local surface を、scene が指定する配置矩形へ一回だけ nearest-neighbor で合成する。出力寸法は配置可能領域と提示サイズだけを決め、source pixel mask は決めない。

削除・置換範囲:

- `ScaledProceduralRenderer.kt`: 魔法陣用の `UiMappedEngineCanvas.graphicsDepth`、`beginGraphics/endGraphics`、`outputX/outputY` を削除する。UI 用 `UiRasterGrid` の是非とは分離し、少なくとも graphics path から完全に切る。
- `NestedTimeboxInstrumentRenderer.kt`: `outputX/outputY` 変換、`outputShortAxis → graphicsRadius → graphicsUnit → graphicsGlyphBlock`、`GRAPHICS_REFERENCE_RADIUS`、destination 由来の `graphicsCell/thin` を削除し、固定 source 座標へ置換する。scene は destination の placement のみ渡す。
- `ScaledProceduralRenderer.drawPolarGlyph/emitRotatedGlyph`: 現行の ink-centroid と final `scale` 領域の逆サンプルを置換する。固定16×16 source cell、固定支点、固定 source サンプリングで palette-index mask を決定し、destination scale を受け取らない。
- glyph 実装: `GlyphRasterizer` / `SoftwareGraphics` の既存固定 indexed raster を統合先として使い、魔法陣用に必要な固定回転処理だけを同じ所有境界へ置く。重複する三本目の glyph rasterizer は作らない。
- 維持: main `IndexedFramebuffer`、Android/Win32 presenter、palette expansion、`PresentationTransform`、scene/public API、承認済み半径・リング配置、現在復元中の palette、6速度。

受入不変条件:

1. 同じ semantic state と回転位相なら、出力解像度・window size・orientation に関係なく local framebuffer の寸法と全 byte が同一。
2. source pixel は常に palette index `0..15`。ARGB、alpha edge、中間色なし。
3. glyph source は固定16×16、支点と標本規則も固定。destination 由来の font metric、glyph block、stroke width なし。
4. 線・円・arc・Bezier・glyph は source 整数座標へスナップされ、aliased、決定論的、hot path allocation-free。
5. 魔法陣全体を source へ一回ラスタし、完成 surface を一回 nearest-neighbor 合成する。layer ごとの destination scaling や二重 raster は禁止。
6. 承認済み半径・リング配置、中央 readout、palette、6相対速度 `4, 3, 12, -15, 20, 40 deg/s` を保持する。
