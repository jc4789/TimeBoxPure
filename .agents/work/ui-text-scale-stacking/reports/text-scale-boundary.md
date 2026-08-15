# Scope

- 最新方針「全体表示倍率を一切変更せず、文字だけ表示倍率との関係を変える」を正本とした。先行差分にある `DisplayScalePolicy` の倍率1化は候補から除外し、基準コミット `30f0b34` の Pixel 10 における全体表示倍率 `D = 3`、論理寸法 `360×808`、Android描画 `canvas.scale(D, D)`、入力 `physical / D` を維持する前提で調査した。
- 作業ツリーには親作業の5ファイル差分があるが、製品コードは一切変更していない。今回追加したのはこの報告だけである。
- `EngineCanvas` の全実装、全 `ScaledProceduralRenderer` 生成箇所、Android提示変換、東雲文字の計測・字送り・折返し・描画、手動文字配置を検索した。コマンド: `rg -n --glob '*.kt' '(:\s*EngineCanvas\b|EngineCanvas\s*\{|ScaledProceduralRenderer\(|AndroidEngineCanvas\()' .`、`rg -n --glob '*.kt' '\.drawGlyph\(|\bdrawGlyph\(' app shared-engine`、`rg -n --glob '*.kt' '(U\s*\*\s*scale|measureText|measureWrapped|cellsPerLine|cursorColumn.*U)' shared-engine/src/commonMain/kotlin/com/example/timeboxvibe/engine/core`。
- 必須条件は、プラットフォームを表示端末に限定すること、共通コアが文字専用倍率を一元所有すること、計測・字送り・折返し・描画で同じ倍率を使うこと、最終物理座標と矩形辺を整数にすること、文字ホット経路へ割当てを入れないこと、色をパレット添字 `0..15` のまま保つこと、全UI固有寸法と文字寸法を `U`、表示事実、実測文字寸法、名前付き定数から導出することである。

# Confirmed

- 現リポジトリの `EngineCanvas` 実装は `app/src/main/java/com/example/timeboxvibe/platform/android/AndroidEngineCanvas.kt` の `AndroidEngineCanvas` 1個だけである。`shared-engine/src` には `commonMain`、`androidMain`、`iosMain`、`winMain` があるが、後3者の共有エンジン側プラットフォームファイルは各 `PlatformTime.kt` だけで、Win32/iOSのCanvas実装はない。`ScaledProceduralRenderer` の生成も `Pc98SurfaceView.surfaceChanged` の1箇所だけである。根拠: 上記 `rg`、`shared-engine/build.gradle.kts:9-35`。

- 維持対象のAndroid変換は次である。
  - `Pc98SurfaceView.surfaceChanged` が共通 `DisplayScalePolicy.deriveScale` で `D` を決め、`logicalWidth = physicalWidth / D`、`logicalHeight = physicalHeight / D` とする。`Pc98SurfaceView.kt:63-92`。
  - 描画は `drawFrame` 内で `canvas.scale(D.toFloat(), D.toFloat())` を一度掛けてから、全シーンを論理座標で描く。`Pc98SurfaceView.kt:250-287`。
  - 入力は `event.x / D`、`event.y / D`。`Pc98SurfaceView.kt:104-122`。
  - 最新方針では、この `D`、論理寸法、全体Canvas変換、入力逆変換を変更しない。

- 現行の通常字形では、意味上の文字倍率を `K` とすると、`drawGlyphRaw` が各オンビットを `K×K` 論理矩形として `EngineCanvas.drawRect` へ出し、外側の全体表示倍率 `D` がさらに掛かる。現在の式は次である。根拠: `ScaledProceduralRenderer.drawGlyph` / `drawGlyphRaw`（現行およそ `ScaledProceduralRenderer.kt:90-144`）。

  ```text
  現行の物理ラスタ画素辺 = K × D
  現行の物理字形セル     = U × K × D
  ```

  Pixel 10では `D=3` なので、本文 `K=1` は48×48物理画素セル、見出し・主タイマー `K=2` は96×96物理画素セルになる。

- 最新方針を満たす文字専用境界には、全体表示倍率 `D` と別の整数物理ラスタ倍率 `P` が必要である。意味倍率 `K` を残す場合、正本式は次になる。

  ```text
  文字物理ラスタ画素辺 Q(K) = P × K                 // 常に正の整数
  文字論理ラスタ画素辺       = Q(K) / D
  文字論理字形セル           = U × Q(K) / D
  文字物理字形セル           = U × Q(K)
  文字物理字送り             = U × Q(K)
  ```

  この式なら全体倍率 `D` を3のまま保持し、候補 `P=2` では本文セル32物理画素、見出し `K=2` は64物理画素になる。対応する論理セルは本文 `32/3`、見出し `64/3`。文字以外の `U` 基準形状は従来どおり `U×D=48` 物理画素単位を維持する。

- `P/D` をFloatとして現行 `drawRect` へ渡すだけでは実現できない。`AndroidEngineCanvas.drawRect` はネイティブCanvasへ渡す前に `x`、`y`、`w`、`h` を個別に `roundToInt()` する。`D=3、P=2、K=1` の論理ラスタ幅 `2/3` は1へ丸められ、外側の `D=3` で結局3物理画素になる。`setPixel` も論理座標を丸めて1×1論理矩形を描くため同じ。根拠: `AndroidEngineCanvas.kt:78-104`。

- したがって、共通側へ `D` の事実を渡す経路と、既存の論理整数丸めを通らない汎用提示プリミティブの両方が必要である。文字専用のAndroid APIは不要であり、推奨する最小契約は概念上次である。

  ```kotlin
  interface EngineCanvas {
      val presentationScale: Int

      // 座標・辺は共通コアが確定した物理整数。色はパレット添字。
      fun drawPhysicalRect(
          x: Int,
          y: Int,
          width: Int,
          height: Int,
          colorIndex: Int,
      )
  }
  ```

  Android実装は `presentationScale` を選ばず、`Pc98SurfaceView` が既に選んだ `D` を事実として受け取るだけである。`drawPhysicalRect` も文字を知らず、物理整数矩形を現在の `D` 倍Canvas上へ逆換算して即時提示し、既存のキャッシュ済み16色パレットで `colorIndex` を解決するだけにする。UI判断、文字倍率、言語、折返し判断をAndroidへ置かない。

- 共通側の物理整数スナップは、各行の論理原点を一度だけ物理整数へ変換し、その後をIntで進めれば保証できる。

  ```text
  linePhysicalX = round(lineLogicalX × D)
  linePhysicalY = round(lineLogicalY × D)
  glyphAdvancePhysical = U × Q(K)
  glyphPhysicalX(i) = linePhysicalX + i × glyphAdvancePhysical
  sourcePixelPhysicalX(x) = glyphPhysicalX + x × Q(K)
  sourcePixelPhysicalWidth = Q(K)
  ```

  原点、字送り、各オンビットの左上・幅・高さがすべてIntになる。各字ごとにFloatを再丸めする方式より、長文での累積誤差と1物理画素の漂流を避けられる。

- 共通コアで一元化が必要な既存記号は次である。
  - `ScaledProceduralRenderer.measureTextWidth`、`measureTextHeight`、`drawGlyph`、`drawGlyphRaw`、`drawText`、ボタン内の測定・描画。現行は `U * K` を正本にする。`ScaledProceduralRenderer.kt:21-41,90-167,685-749`。
  - `ProceduralTextRenderer.drawRaw`、文字列/バッファ両方の `measureWrappedLineCount`、`measureWrappedHeight`、`measurePresetIdHeight`、`drawPresetIdWrapped`、`locateWrappedCursor`、`drawWrapped`、`cellsPerLine`。現行は各所で `U * scale` を再計算する。`ProceduralUiPrimitives.kt:83-333`。
  - `NestedTimeboxInstrumentRenderer.drawTimeCentered` と `drawAlarmTimeCentered` は `cellWidth = U * scale` または `drawX += U` を手動使用する。`NestedTimeboxInstrumentRenderer.kt:491-529`。
  - `Scenes.kt` はタスクカーソルを `cursorColumn * U` / `cursorLine * U` で置く（`Scenes.kt:281-289`）ほか、手動時刻・ステップ・タスク行に `U * scale` の字送りが残る（`Scenes.kt:767-854,3258-3288`）。`drawStepCentered` は現行メイン画面から実使用されている。
  - `ScaledProceduralRenderer.drawButton` のアクティブ記号Yは `(h-U)/2` であり、文字セルが `U×Q/D` に変わる場合は共通測定値で中央化しなければならない。
  - `EngineCursorRenderer` の既定高は `U`、呼出側の位置は `U` 字送りである。文字カーソルを字形へ合わせるなら、位置・高を同じ文字メトリクスへ接続する必要がある。`EngineCursorRenderer.kt:11-72`。

- `drawPolarGlyph` は同じ東雲字形を使うが、IMGUI文字ではなく魔法陣の装飾でも使用される。非回転経路は `drawGlyph`、回転経路は事前確保済み `rotatedGlyphBuffer` と `canvas.setPixel` を使う。根拠: `ScaledProceduralRenderer.kt:197-325`、呼出しは `NestedTimeboxInstrumentRenderer.kt:173,225,250,324`。通常 `drawGlyph` を一律で物理文字倍率へ変えると、魔法陣ルーンも48物理画素セルから32へ変わる。

- 推奨経路はホットループ無割当てで構成できる。`TextRasterScale` は共通側にInt/Floatの事前設定値だけを保持し、`drawGlyphRaw` は既存のキャッシュ済み `IntArray` 字形と `while` ループを使い、物理原点・`Q`・clip辺をプリミティブIntで計算する。Android側は既存 `Paint` と `cachedNativePalette` を再利用して即時 `drawPhysicalRect` する。文字ごとの配列、`TextLayout`、リスト、ラムダ、文字列構築、Bitmap、コマンドオブジェクトは不要である。

- 現行Android Canvasには、破線円の `floatArrayOf` / `DashPathEffect`、ディザーパターンキー文字列等の既存割当てがあるが、推奨する文字物理矩形経路はそれらを通らない。今回の範囲で既存の非文字経路を変更する根拠はない。

- `U` の扱いは、ROMソースセル `U=16` とマクロUI正本を維持し、文字の表示セルを必ず `U × 名前付き意味倍率K × 名前付き物理倍率P / 表示事実D` から導出すれば、任意ピクセル値を導入せずに済む。ボタン、余白、当たり判定、魔法陣等の既存 `U` 寸法は変更しない。文字で変動する行高・折返し・カーソルだけは同じ実測文字セルを使用する。ユーザー指定の `U` 合成表現は変更しない。

- 技能基準による推奨案の確認は次である。

  ```text
  PLATFORM FIREWALL CHECK:
  Platform: AndroidEngineCanvas / Pc98SurfaceView
  Allowed responsibility: Dと表示Surfaceの提供、物理整数矩形の即時提示、パレット変換
  Core responsibility preserved: P/K、計測、字送り、折返し、物理スナップ、字形ラスタ判断
  Leakage found: 推奨案ではなし。文字専用Android APIは不採用
  Result: PASS

  HOT LOOP AUDIT:
  Function: ScaledProceduralRenderer.drawGlyphRaw / AndroidEngineCanvas.drawPhysicalRect
  Status: PASS候補
  Violations: 実装前のため未検証。設計上の新規割当てなし
  Required rewrite: 物理Int原点とwhileループを使用し、TextLayout/配列/ラムダ/保存変換を作らない

  COLOR LAW CHECK:
  Core color representation: colorIndex 0..15
  Native color conversion location: AndroidEngineCanvasだけ
  Palette cache: 既存cachedNativePalette[16]を再利用
  Platform leakage: なし
  Result: PASS候補

  LAYOUT LAW CHECK:
  U source: CANONICAL_UI_UNIT = 16
  Display variables used: 既存logicalWidth/logicalHeight、事実D、実測文字セル
  Unexplained constants: Pの値と導出規則は承認が必要
  Stacking behavior: 既存の明示allowTextStackingを維持し、新しい実測文字幅・高を共有
  Result: P確定前は未完
  ```

# Rejected

- 全体表示倍率 `D` の変更、`DisplayScalePolicy` の倍率1化・倍率2化は最新方針により不採用。作業ツリーの `EngineCanvas.kt` にある未コミットの倍率選択変更は、文字専用境界の一部として保持できない。

- `canvas.density` を `D` または文字倍率として再利用する案は不採用。現在Androidは `density=1f` を論理描画契約として使い、`ScaledProceduralRenderer.drawProgressTracks` / `drawProceduralPolarDemo` や `AndroidEngineCanvas.drawCircle` の線幅・ダッシュにも参照される。意味を変えると非文字の魔法陣・装飾まで変化し、既存プロパティの意味も置換する。

- 現行 `drawRect` へ `P/D` の小数幅を渡すだけの案は不採用。Android側の `roundToInt` により `2/3` が1論理画素、最終3物理画素へ戻るため、目的を満たさない。

- Android側へ `drawTextPixel`、`textScale`、言語・折返し引数を追加する案は不採用。プラットフォームが文字描画方針を所有し、`platform-firewall-port` に違反する。追加するなら文字を知らない `drawPhysicalRect` と表示倍率事実だけに限定する。

- 各字形ごとに `Canvas.save`、逆scale、文字scale、`restore` を行う汎用変換案は第2候補に留める。文字専用方針を共通に置ける点は正しいが、字形ごとのプラットフォーム状態変更がホット経路へ入り、直接 `drawGlyph` する経路と `drawWrapped` を一括変換する経路の入れ子管理も必要になる。文字列単位にまとめると既存の即時描画APIを大きく組み替える。

- 文字Bitmap、文字アトラス、オフスクリーン物理フレームバッファ、遅延コマンド列は不採用。新規バッファ・キャッシュ・コマンド管理が必要で、即時描画のZ順を壊すか全描画の表示リスト化を要求し、最小変更でも無割当てでもない。

- Androidの全体 `canvas.scale` を外し、全プリミティブをAndroid実装で個別に `D` 倍する案は不採用。表示結果を維持できる可能性はあるが、線幅、円、ディザー、clip、入力との整合まで提示層全体を書き換えるため、文字だけの修正ではない。

- 文字を32物理画素で描きながら字送り・計測だけ従来の `U=16` 論理、すなわち48物理画素のままにする案は不採用。字間に16物理画素の空白が残り、計測・折返し・描画が別倍率になる。今回の「同一倍率所有」条件にも `canonical-unit-layout` の文字とUI構造一致にも反する。

- `Scenes.kt` と `NestedTimeboxInstrumentRenderer.kt` に残る個別 `U * scale` をそのままにして、`drawGlyphRaw` だけ縮小する案は不採用。ラスタだけ32物理画素、字送りは48物理画素となり、間隔、中央寄せ、折返し、カーソルが破綻する。

- 汎用UI文字と魔法陣ルーンを無検討で一括変更する案は不採用。後者は文字データを使う装飾であり、非文字UI・魔法陣を維持する最新範囲との分類を先に決める必要がある。

# Unknown

- 基礎文字物理倍率 `P` の最終値は未承認。Pixel 10の既知結果では `P=1` の16物理画素セルは約0.96mmで読みにくく、現行 `P=3相当` の48物理画素セルは過大、`P=2` の32物理画素セルは約1.93mmの中間候補である。しかし `P=2` を設計定数とするか、`P = minOf(D, 2)` として `D=1` では1に抑えるかはユーザー判断が必要。生DPI、端末名、固定解像度から決めてはならない。

- `P/D` により既定本文の論理字形セルが `U` そのものではなく `U×P/D` になることは、最新方針が要求する意図的な相対比変更である。一方、`canonical-unit-layout` の最も厳密な文言 `U = glyphWidth = glyphHeight` を「表示後の既定論理セルも常にU」と解釈すると、非文字UIを `D=3` のまま、文字だけ `P=2` にする要件とは数学的に両立しない。推奨案は `U` をROMソースセルとマクロUI正本として維持し、表示字形をUから導出した名前付き縮尺として扱う解釈である。

- `drawPolarGlyph` の魔法陣ルーンを新しいUI文字物理倍率へ含めるかは未確定。魔法陣外形と装飾の従来比率維持を優先するなら、`drawPolarGlyph` / `emitRotatedGlyph` は従来の論理ラスタ経路を保持し、UI文字APIだけを物理文字経路へ通すべきである。

- 文字カーソルの高さを32物理画素へ合わせるか、UIカーソルとして従来の `U×D=48` 物理画素高を保つかは未確定。ただし列・行位置は新しい字送りへ合わせないと入力表示が確実にずれる。

- `drawPhysicalRect` を外側でscale済みのAndroid Canvasへ `physical / D` のFloat座標として出したとき、数学上の整数辺が実機ラスタライザで完全に継ぎ目なく描かれることは、コード静読だけでは確定できない。`Paint.isAntiAlias=false` と整数目標辺により成立する見込みだが、一時数式オラクルと実機ピクセル測定で確認が必要である。

- 現在承認済みの製品コード5ファイルには `Pc98SurfaceView.kt`、`AndroidEngineCanvas.kt`、`EngineCursorRenderer.kt` が含まれない。推奨案を実装するには少なくとも前2ファイルへの追加承認が必要で、カーソルを同期するなら3番目も必要になる。

# Recommendation

- 候補比較は次のとおり。

  | 候補 | 共通所有 | 最終物理整数 | 即時Z順 | ホット割当て | プラットフォーム方針 | 変更量 | 判定 |
  |---|---|---:|---:|---:|---:|---:|---|
  | 共通 `TextRasterScale` + 汎用 `drawPhysicalRect` | 可 | 明示Int | 維持 | なしで可 | なし | 中 | 推奨 |
  | 共通倍率 + 字形ごとの汎用save/scale/restore | 可 | 可 | 維持 | 不明、状態操作多 | なし | 中～大 | 第2候補 |
  | 小数 `P/D` を現行 `drawRect` へ渡す | 可 | 不可 | 維持 | なし | なし | 小 | 不成立 |
  | Android文字専用描画 | 不可 | 可 | 維持 | 可 | あり | 小 | 禁止 |
  | Bitmap/オフスクリーン/遅延列 | 可 | 可 | 要追加設計 | バッファ次第 | なし | 大 | 過剰 |

- 推奨する最小アーキテクチャは次である。
  1. `D` の選択、論理寸法、Android全体scale、入力逆変換を基準コミット相当へ維持する。
  2. `EngineCanvas.kt` の共通側に、表示倍率事実 `D` と基礎文字物理倍率 `P` から `Q(K)`、論理ラスタ幅、論理字形セルを返す、プリミティブ値だけの `TextRasterScale` を置く。`P` の選択規則も共通側だけに置く。
  3. `EngineCanvas` に文字を知らない `presentationScale` と `drawPhysicalRect(Int...)` 契約を追加する。色引数はパレット添字のままにする。
  4. `Pc98SurfaceView` は既存 `D` を `AndroidEngineCanvas` / 共通コアへ事実として渡すだけにし、倍率選択・文字判断・折返し判断を追加しない。既存 `canvas.scale(D,D)` と入力除算は変えない。
  5. `AndroidEngineCanvas` は既存 `Paint` とパレットキャッシュで物理整数矩形を即時提示するだけにする。逆数 `1/D` はSurface生成時に一度計算し、文字ホット経路で除算・割当てを繰り返さない。
  6. `ScaledProceduralRenderer` の通常UI字形経路は行物理原点を一度丸め、Int物理カーソルと `Q(K)` で各オンビットを `drawPhysicalRect` へ出す。影offsetも同じ `Q(K)` から導出する。回転ルーンは分類決定まで従来経路を保つ。
  7. `measureTextWidth` / `measureTextHeight`、`ProceduralTextRenderer` の両オーバーロード、`cellsPerLine`、プリセットID、カーソル位置、ボタン測定、全手動字送りを `TextRasterScale.glyphCellLogical(K)` へ集約する。計測用の `TextLayout` オブジェクトは作らない。
  8. 親作業で既に行った、見出し倍率の明示、`allowTextStacking`、FORGE非積み上げ、中央ラベル上端配置は維持する。ただしそれらの測定値は新しい共通文字セルへ接続する。

- 最小変更候補ファイルは次である。
  - 既承認: `shared-engine/.../EngineCanvas.kt`、`ScaledProceduralRenderer.kt`、`ProceduralUiPrimitives.kt`、`NestedTimeboxInstrumentRenderer.kt`、`Scenes.kt`。
  - 追加承認必須: `app/.../ui/main/Pc98SurfaceView.kt`、`app/.../platform/android/AndroidEngineCanvas.kt`。
  - カーソル同期を含める場合の追加候補: `shared-engine/.../EngineCursorRenderer.kt`。
  - `SceneManager.kt`、`RetroHudComponent.kt`、Win/iOSファイル、新規資産・新規製品ファイルは推奨最小案では不要。

- 実装前にユーザーへ確認すべき設計値は `P` と、魔法陣ルーン・文字カーソルをUI文字物理倍率へ含めるかの2点である。これらはコードから機械的に決められず、結果の見た目と変更範囲を変える。

- 承認後の検証は、新規常設基盤を追加せず、許可済みの一時数式オラクルで `D>=1`、候補 `P`、`K=1/2`、複数原点・行長に対し全物理辺が整数、字送りと測定が一致、折返しセル数が描画と一致することを確認する。続いてPixel 10実機で本文セル、見出しセル、FORGE、SESSION LIMIT、カーソル、魔法陣ルーンを物理ピクセル測定し、一時検証物は同じ作業内で削除する。
