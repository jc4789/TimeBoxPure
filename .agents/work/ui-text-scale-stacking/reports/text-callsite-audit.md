# Scope

- 読み取り専用監査。製品コード、既存報告、台帳は変更していない。本報告だけを追加した。
- 最新方針を「非文字UIと表示倍率は従来どおり、対象端末では表示倍率 `D = 3`、論理幅 `360` を維持し、東雲16×16ビットマップの物理倍率 `P` だけを分離する」として追跡した。
- `U = CANONICAL_UI_UNIT = 16` は引き続きUI構造の基準単位であり、`((U * 4) - (U / 4))`、`(U / 2 + U / 8)`、`(U + (U / 2) + (U / 8))` などの非文字UI幾何は変更対象にしない。分離対象は文字のラスタ寸法、文字計測、字送り、行高、折返し、文字カーソルである。
- 用語を次のように固定した。
  - `D`: 画面全体へ最後に掛かる表示倍率。今回 `3`。
  - `P`: 東雲フォントの原画像1ビットを何物理ピクセル角で出すか。候補 `1 / 2 / 3`。
  - `S`: 既存APIの意味倍率。通常 `TEXT_SCALE_IDENTITY = 1`、見出し `TEXT_SCALE_HEADER = 2`。
  - `R = S * P / D`: 文字原画像1ビット当たりの論理寸法。
  - `G = U * R = U * S * P / D`: 1字の論理セル幅・論理行高。
- 主な列挙コマンド:
  - `rg -n --glob '*.kt' "ShinonomeFont|drawGlyph|drawText|measureText|measureWrapped|drawWrapped|drawRaw|drawPresetId|locateWrappedCursor|cursorColumn|cursorLine|charW|charSize|cellWidth|textScale|glyphWidth|glyphHeight|clipWidth|clipHeight" shared-engine/src app/src`
  - `rg -n --glob '*.kt' "ScaledProceduralRenderer\\.(measureTextCells|measureTextWidth|measureTextHeight|measureButtonHeight)|ProceduralTextRenderer\\.(measureWrappedLineCount|measureWrappedHeight|measureHeadingHeight|measurePresetIdHeight|locateWrappedCursor|cursorLine|cursorColumn|drawRaw|drawWrapped|drawHeading|drawPresetIdWrapped)|renderer\\.drawText\\(|renderer\\.drawGlyph\\(|renderer\\.drawPolarGlyph\\(" shared-engine/src app/src`
  - `rg -n --glob '*.kt' "(?:renderer\\.)?drawButton\\(|measureButtonHeight\\(" shared-engine/src app/src`
  - `rg -n --glob '*.kt' "\\bU\\s*\\*\\s*scale|scale\\s*\\*\\s*U|cursor(Column|Line).*\\*\\s*U" shared-engine/src app/src`
  - `rg -n --glob '*.kt' "clipWidth|clipHeight|canvas\\.drawRect\\(|canvas\\.setPixel\\(|while \\(sx < scale\\)|while \\(sy < scale\\)|currentScaleFactor|scaleFactor|ScaledProceduralRenderer\\(" shared-engine/src app/src`
  - `rg -n --glob '*.kt' "class .*EngineCanvas|: EngineCanvas|EngineCanvas \\{" .`

# Confirmed

- 物理セルと論理セルの関係は次で一意に決まる。

| 意味倍率 `S` | 文字物理倍率 `P` | 原画像1ビットの論理寸法 `R` | 論理セル `G` | 表示後の物理セル |
|---:|---:|---:|---:|---:|
| 1 | 1 | `1/3` | `16/3 = 5.333...` | `16×16` |
| 1 | 2 | `2/3` | `32/3 = 10.666...` | `32×32` |
| 1 | 3 | `1` | `16` | `48×48`（現行） |
| 2 | 1 | `2/3` | `32/3 = 10.666...` | `32×32` |
| 2 | 2 | `4/3` | `64/3 = 21.333...` | `64×64` |
| 2 | 3 | `2` | `32` | `96×96`（現行） |

- 論理幅は常に `360` のままであり、表示後の物理幅は `360 * 3 = 1080`。非文字UIの論理寸法を縮める必要はない。
- 一貫した文字契約は全経路で同じ `G` を使うことになる。
  - 文字列幅: `cellCount * G`。
  - 文字高さ・1行高: `G`。
  - 通常字送り: `G`。
  - `drawText` の追加字間を現行の「文字原画像ピクセル単位」と維持するなら `G + charSpacing * S * P / D`。
  - 1行の最大文字数: `max(1, floor(maxWidth / G))`。
  - 折返し高さ: `lineCount * G`。
  - カーソル位置: `x + column * G`, `y + line * G`。
  - 1原画像ピクセル分の影オフセット: `S * P / D` 論理ピクセル、表示後は `S * P` 物理ピクセル。
- 文字原点は表示後の物理ピクセル境界へ揃える必要がある。`snapD(x) = round(x * D) / D` とし、原点を一度だけ揃えた後、各ビットを `R` 間隔で配置すれば、表示後の座標とビット寸法は整数になる。各文字ごとに論理セル `G` を整数丸めすると累積ずれが生じるので不可。
- `ShinonomeFont.kt:8` のフォント源は16行×16ビットの固定ビットマップである。問題は源字形ではなく、その1ビットを現在何論理ピクセル角で描くかにある。
- 表示境界の現状:
  - `Pc98SurfaceView.kt:75` が `DisplayScalePolicy.deriveScale` の結果を `currentScaleFactor` に保持し、`281` でAndroid `Canvas` 全体へその倍率を掛ける。入力は `119-120` で同じ倍率により論理座標へ戻す。
  - `Pc98SurfaceView.kt:85` は `ScaledProceduralRenderer(engineCanvas)` を作るだけで、最終表示倍率 `D` を共通文字描画へ渡していない。
  - `AndroidEngineCanvas.kt:97-104` は通常の `drawRect` の `x/y/w/h` を全て論理整数へ丸める。`78-84` の `setPixel` は1論理ピクセル角を固定で描く。
  - 従って `D=3` のまま `P=1` または `P=2` に必要な `1/3` または `2/3` 論理ピクセル角は、現状のAndroid境界で失われる。共通側の計測式だけを変えても、字形は48×48物理相当のまま重なり、正確な16×16/32×32にはならない。
  - `EngineCanvas.density` は `Pc98SurfaceView.kt` から現在 `1` が渡り、さらに魔法陣・弾幕などの非文字描画でも利用される。これを文字倍率または表示倍率として流用すると非文字UIを変えるため不適合。
- 計測・折返し・描画の定義箇所は次で全件。
  - `ScaledProceduralRenderer.kt:21-41`: `measureTextCells`、`measureTextWidth`、`measureTextHeight`、`measureButtonHeight`。現在は全て `U * scale`、またはそれを使う折返し高さ。
  - `ScaledProceduralRenderer.kt:90-144`: `drawGlyph` / `drawGlyphRaw`。`104,108,124,134-137` が整数 `scale` をラスタ寸法、影、ビット位置へ直接使用。
  - `ScaledProceduralRenderer.kt:146-167`: `drawText`。`164` の字送りが `(U * fScale) + (charSpacing * fScale)`。
  - `ScaledProceduralRenderer.kt:197-260`: `drawPolarGlyph`。非接線時は通常 `drawGlyph`、接線時は回転バッファ経由。
  - `ScaledProceduralRenderer.kt:263-325`: `emitRotatedGlyph`。`scale` 回の二重ループと `canvas.setPixel` なので、分数 `R` を表現できない。
  - `ScaledProceduralRenderer.kt:681-746`: `drawButton`。非積上げは計測と `drawText`、積上げは `measureWrappedHeight` と `drawWrapped`、選択指標は `704-713` の直接 `drawGlyph`。
  - `ProceduralUiPrimitives.kt:83-97`: `drawRaw`、字送り `91`。
  - `ProceduralUiPrimitives.kt:99-125`: String折返し行数・高さ。高さ `121`。
  - `ProceduralUiPrimitives.kt:124-140`: 見出し計測・描画。
  - `ProceduralUiPrimitives.kt:142-179`: preset ID計測・描画。高さ `150`、字送り/行送り `165-176`。
  - `ProceduralUiPrimitives.kt:181-211`: IntArray折返し行数・高さ。高さ `210`。
  - `ProceduralUiPrimitives.kt:213-244`: 折返しカーソル位置。
  - `ProceduralUiPrimitives.kt:246-283`: String折返し描画。セル・行幅・字送り・行送り `260,265,272-273`。
  - `ProceduralUiPrimitives.kt:285-326`: IntArray折返し描画。セル・行幅・字送り・行送り `302,307,315-316`。
  - `ProceduralUiPrimitives.kt:328-331`: `cellsPerLine`。現在 `maxWidth / (U * safeScale)`。
  - `EngineCursorRenderer.kt:18-20,52-74`: 文字カーソル幅 `U/8`、高さ `U`。少なくとも高さと行位置は縮小後の文字セルと一致しない。
- `Scenes.kt` の文字計測・折返し・カーソル呼出は次で全件。これらは中央契約を参照すれば式を各場面へ複製する必要はないが、静的計測値と描画値を同時に切り替える必要がある。
  - Active Timer: `266,281,288-289,327,342,344,346-347,394,630,637-638,660,673,778,780-781,800,802-803,834,836-837`。
  - Template: `947,988,995,1112,1177,1206,1208,1210-1211`。
  - Forge: `1337-1338,1387-1388,1401,1408,1496-1497,1629-1630,1647,1676-1677,1721-1722,1726,1745,2029,2051,2109,2125,2146,2152`。
  - Settings: `2506,2522,2570,2580,2642`。
  - Entropy: `2771,2777,2787,2789,2794,2813-2814,2912,2920,2972,2993,2995,3003,3005,3010,3033-3034,3222,3240,3244,3294`。
  - Blocked: `3336,3368,3373`。
- `Scenes.kt` の文字描画呼出は次で全件。
  - Active Timer: `269`（折返し）、`333,385`（直接字形＋明示クリップ）、`352`（見出し）、`377,395,661,675`（折返し）、`783-787,805-821,842-854`（直接字形）。
  - Template: `989`（折返し）、`996,1010`（preset ID）、`1005`（折返し）、`1019-1020,1035`（ボタン）、`1034`（見出し）。
  - Forge: `1354`（折返し）、`1390-1391,1398,1404`（積上げ可能ボタン）、`1397`（見出し）、`1409,2031,2033,2053,2056`（折返し）、`2054-2055`（矢印ボタン）。
  - Settings: `2516,2525,2571,2581,2644`（折返し）、`2549,2552,2555,2558,2573,2584,2591,2636-2637,2649-2650`（ボタン）。
  - Entropy: `2772,2913`（見出し）、`2778,2800,2802,3274,3295`（折返し）、`2806,2863,2870,2880,2908,2923`（ボタン）、`2846,2852`（raw）、`2917`（helper経由のtask buffer）、`3261-3264,3281-3282,3287`（直接字形）。
  - Blocked: `3338`（見出し）、`3340`（折返し）、`3347`（ボタン）。
- `Scenes.kt` に残る手動文字セル計算:
  - `288-289`: 折返しカーソルの列・行を固定 `U` 倍。折返し判定だけ `G` にしてここを残すと、カーソルが別の文字または別行へずれる。
  - `768-787` `drawTimeCentered`、`790-821` `drawStepCentered`、`824-854` `drawAlarmTimeCentered`: 中央寄せと字送りは `measureTextHeight(scale)` を共用している。中央計測APIが `G` を返せば整合するが、描画側も同じ契約でなければ重なる。
  - `3259-3266` `drawTaskRow`: `U * scale` による4回の字送りと5セル分の残幅控除。
  - `3277-3288` `drawTwoDigits` / `drawPageIndicator`: `U * scale` による直接配置。
- `NestedTimeboxInstrumentRenderer.kt` の全文字経路:
  - `173,225,250,324`: `drawPolarGlyph`。特に接線回転経路は整数 `scale` と `setPixel` 依存。
  - `473,487`: 中央折返し描画、`485` はその高さ計測。
  - `494-501`: 5字時刻の `cellWidth = U * scale` と直接配置。
  - `514,518-524`: アラーム接頭辞・時刻の直接字形。`518-523` は固定 `U` 字送り。
  - `527-528`: ローカル `drawGlyph` から共通描画器への委譲。
- クリップ経路:
  - `ScaledProceduralRenderer.drawGlyph` は `startX/startY/clipWidth/clipHeight` を持つが、`drawGlyphRaw:136-138` はビット矩形の左上点だけを判定する。`R` が分数になった場合も右端・下端の矩形全体はクリップされず、境界を最大1ビット幅越え得る。
  - 明示クリップを渡す直接場面は `Scenes.kt:333,385` のマーキーと、`ScaledProceduralRenderer.kt:704-713,734-743` のボタン内非積上げ文字・選択指標。
  - `ProceduralTextRenderer.drawWrapped` にはクリップ引数がないため、`drawButton` の積上げ経路 `ScaledProceduralRenderer.kt:716-730` はボタン矩形で実クリップされない。正しい計測とボタン高により収める設計だが、短い高さを渡す呼出では描画流出を防がない。
  - 文字専用矩形を実装する場合、`left=max(drawX,clipLeft)`、`top=max(drawY,clipTop)`、`right=min(drawX+R,clipRight)`、`bottom=min(drawY+R,clipBottom)` の交差矩形を非丸めで出せば、非文字矩形の丸めを変えずに正確な物理クリップを保てる。
- 積上げの選択性は現作業木の `drawButton(... allowTextStacking: Boolean = false)` と呼出側明示により分離されている。文字倍率分離後も削除せず、`measureButtonHeight` と `drawButton` が同じ `G`・同じ `allowTextStacking` を使う必要がある。
- `canonical-unit-layout` の行更新必須形状について、文字倍率分離で変わるのは `requiredLabelW` と `labelHeight` の計測だけである。`padding = U / 2` などUI幾何は `U` のまま、分岐で `maxRowHeight` を決め、最後に一度だけ `currentY += maxRowHeight + padding` とする。
- 現行 `Scenes.kt:2505-2531` のSettings `layoutRow` は、side-by-sideで `2518`、stackedで `2526/2528` と `2531` に分けて `currentY` を進めており、指定された正確な更新形状ではない。文字セル変更時にここを放置すると、描画あり/なし経路の高さ契約を再び別々に維持することになる。
- 静的計測は描画中だけでなく入力・スクロール計算でも呼ばれる。例: Templateのスクロール高 `Scenes.kt:1112,1177,1206-1211`、Forgeのタップ/全高 `1496-1745,2109-2153`、Settingsの `layoutRow(null, ...)` 呼出 `2320-2462,2607-2622`、Entropyの高さ `2972-3244`。従って `D/P` を `drawGlyph` のローカル変数にだけ置く設計は不整合になる。

# Rejected

- 表示倍率を `3` から `1` へ下げて48物理セルを16物理セルに見せる案。論理幅360と非文字UIの表示寸法まで変わり、最新方針に反する。現作業木 `EngineCanvas.kt:41-43` の「最小安全倍率まで下げる」追加ループは、1080物理幅では倍率1を選び得るため、この最新方針とは両立しない。
- `U` 自体を `16/3` に置換する案。ボタン、余白、当たり判定、装飾まで縮み、ユーザーが明示したU式を破壊する。`U=16` は維持し、文字セルだけを `G=U*S*P/D` としてUから導出する。
- `drawGlyphRaw` だけを縮め、`measureTextWidth`、`measureWrappedHeight`、`cellsPerLine`、手動字送りを残す案。見た目の字形とレイアウト占有幅が分離し、余分な折返し・不正な中央寄せ・カーソルずれが残る。
- 計測だけを縮め、Androidの整数丸めを残す案。計測上は小さいのに、実字形は1論理ピクセル以上＝3物理ピクセル/bitとなり、文字同士が重なる。
- 通常の `EngineCanvas.drawRect` から丸めを一括削除する案。非文字UI、装飾、ベベル、弾幕までラスタ規則を変える。文字専用の正確な矩形プリミティブの方が範囲が小さい。
- `EngineCanvas.setPixel` をP=1/P=2の文字にもそのまま使う案。現実装の1回は1論理ピクセル＝3物理ピクセルであり、16/32物理セルを表現できない。
- `EngineCanvas.density` をDまたはPとして流用する案。現在値と既存の非文字利用契約が異なる。
- `G` を5または11などの論理整数へ丸める案。表示後は15または33物理セルになり、字形の16/32物理セル要件を満たさない。長い文字列ほど字送り誤差が累積する。
- 折返し行の描画後に固定 `U` または別の補正値を加える案。行高は正確に `G`、行番号 `n` のYは `baseY + n * G` とし、計測高さも `lineCount * G` で同一にする。
- 積上げ機能の削除または再度の全ボタン強制。最新契約は既定非積上げ、必要場面だけ明示許可である。
- Settingsのstacked分岐でラベル分とコントロール分を別々に `currentY` 加算する現形状。必須形状どおり、分岐内で `maxRowHeight` を算出して共通末尾で1回だけ更新する。

# Unknown

- 通常文字の最終既定値が `P=1`、`P=2`、ユーザー設定可能な1/2/3のいずれか。ユーザーの「16×16で表示したい」という症状記述から `P=1` が直接の目標だが、本監査依頼は3候補の式検討までであり、設定UI追加は指示されていない。
- `drawPolarGlyph` の魔法陣ルーンを通常本文文字と同じPで縮めるか、非文字の装飾字形として従来P=3を維持するか。後者なら通常文字倍率から暗黙に外さず、名前付きの装飾字形倍率として明示する必要がある。
- `drawText.charSpacing` の単位を「字形原画像ピクセル」と維持するか、「論理UIピクセル」に再定義するか。外部呼出は見つからず既定0なので現在の画面差はないが、API契約として決定が必要。
- `D/P` の所有場所。計測はレンダラなしの入力・スクロール経路でも必要なため、レンダラのインスタンスだけに持たせられない。単一の不変 `TextGeometry` をエンジン実行時契約としてSceneManager/Scenesへ渡すか、既存の静的計測APIが参照する設定済みポリシーにするかは設計判断が必要。
- 分数クリップ境界の丸め方向。ボタン/マーキーの物理境界へ厳密に合わせるなら、文字原点だけでなくクリップ矩形もDの物理グリッドへ揃える契約が必要。
- 現在見つかった `EngineCanvas` 実装はAndroidだけである。今後別プラットフォーム実装が追加される場合、文字専用矩形プリミティブにも同じ「非補間・物理ピクセル境界」契約が必要。

# Recommendation

- 最小の一貫した実装単位は、まず単一の文字幾何契約を作り、計測・描画・入力が全てそこから `R/G` を取ること。
  - `rasterStep(S) = S * P / D`
  - `cellSize(S) = U * rasterStep(S)`
  - `measureWidth(text,S) = cells(text) * cellSize(S)`
  - `measureHeight(S) = cellSize(S)`
  - `wrappedCells(width,S) = max(1,floor(width/cellSize(S)))`
  - `wrappedHeight(lines,S) = lines * cellSize(S)`
  - `snapOrigin(x) = round(x*D)/D`
- 非文字UIを維持する最小境界変更は、通常の `drawRect` を触らず、`EngineCanvas` に文字ビット用の非丸め矩形プリミティブを1個追加し、Android実装でその座標・寸法を `roundToInt` せず `nativeCanvas.drawRect` へ渡すこと。`D` は `Pc98SurfaceView` が既に確定しているので、同じ値を文字幾何契約へ渡す。
- `drawGlyphRaw` はその専用プリミティブで `R×R` の矩形を描き、原点スナップ・1ビット影オフセット・矩形交差クリップを共通化する。`drawText`、通常極座標字形、ボタン文字は必ず同じ経路を通す。
- `ProceduralTextRenderer` のString/IntArray両方について、`drawRaw`、preset ID、折返し計測、`cellsPerLine`、字送り、行送りを全て同じ `cellSize(S)` に置換する。行高更新は `line * cellSize(S)`、高さは `lineCount * cellSize(S)` の同形にする。
- `Scenes.kt` は中央計測APIを使う時計/STEP/ALARMをそのまま中央契約へ接続し、手動箇所だけを直す。必須の直接対象は `288-289`、`3259-3288`、およびSettings `2505-2531`。後者は次の形を保つ。
  - side-by-side: `maxRowHeight = max(labelHeight, controlHeight)`。
  - stacked: `maxRowHeight = labelHeight + U/2 + controlHeight`。
  - 共通末尾: `currentY += maxRowHeight + U/2`（実際の既存名前付きpadding/spacing定数が同じU由来ならそれを使用）。
- `NestedTimeboxInstrumentRenderer.kt:494-524` の時刻/接頭辞は共通 `cellSize(S)` で中央寄せと字送りを行う。`drawPolarGlyph` は通常文字扱いなら回転後バッファも文字専用矩形でR倍出力へ変更し、装飾扱いなら明示した固定装飾倍率経路へ分ける。
- `EngineCursorRenderer` はカーソルの意味を分ける。幅 `U/8` はスキル上許可されたUIカーソルのマイクロディテールとして維持可能だが、高さとScenesの列/行位置は現在描いている文字セル `G` に一致させる。P=1時に高さを固定Uのままにしない。
- `drawButton` は `allowTextStacking=false` 既定を維持する。積上げ可能時の計測・描画は同じGを使い、非積上げ時の文字幅/高さも同じGを使う。選択指標の垂直位置 `ScaledProceduralRenderer.kt:707` も固定Uではなく指標字形の実測Gで中央寄せする一方、左右パディング `U/2` とボタン外形はUのまま維持する。
- 最小変更対象ファイル:
  1. `shared-engine/.../core/EngineCanvas.kt`: 表示倍率を文字幾何へ伝える契約、文字専用の非丸め矩形プリミティブ。最新方針に反する表示倍率低下ロジックは維持しない。
  2. `app/.../platform/android/AndroidEngineCanvas.kt`: 文字専用矩形だけを非丸めで実装。
  3. `app/.../ui/main/Pc98SurfaceView.kt`: 最終表示倍率Dを同じ文字幾何契約へ渡す。
  4. `shared-engine/.../core/ScaledProceduralRenderer.kt`: 中央計測、通常字形、文字列、極座標字形、クリップ、ボタン文字を統一。
  5. `shared-engine/.../core/ProceduralUiPrimitives.kt`: raw/preset/折返し/カーソル計測の全 `U*scale` を中央セルへ統一。
  6. `shared-engine/.../core/Scenes.kt`: 手動字送り、カーソル、Settingsの正確な単一行高更新形状。
  7. `shared-engine/.../core/NestedTimeboxInstrumentRenderer.kt`: 手動時刻字送りと極座標字形の扱い。
  8. `shared-engine/.../core/EngineCursorRenderer.kt`: 文字セルに一致するカーソル高さ。
  9. 計測ポリシーをSceneManager所有で共有する設計を選ぶ場合のみ `shared-engine/.../core/SceneManager.kt`。静的ポリシーで済ませる場合はファイル追加不要だが、描画開始前の入力経路でもD/Pが必ず設定済みであることが条件。
- 上記より少ない変更、特にAndroid境界3ファイルを除いた共通5ファイルだけの変更では、D=3のまま物理16/32セルを正確に描く要件は満たせない。
