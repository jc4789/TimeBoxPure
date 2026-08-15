# Scope

- 読み取り専用で、Android の表示情報から `logicalWidth` / `logicalHeight`、全体表示倍率、16×16 東雲字形、`Scenes.kt` と関連描画器の文字内倍率へ至る経路を追跡した。製品コード、テスト、ビルド生成物は変更していない。
- 照合対象は `UIscalefix.md`、コミット `30f0b34b6e5b23402659fb249da78f7540c818af`（件名 `shit`）、現行 `HEAD`。実際のシーンファイルは `UI/scenes.kt` ではなく `shared-engine/src/commonMain/kotlin/com/example/timeboxvibe/engine/core/Scenes.kt` だけである（コマンド: `rg --files | rg -i '(^|[\\/])scenes\.kt$'`）。
- 必須基準は `CANONICAL_UI_UNIT = 16` を正本 `U` とすること、DPIを助言値として検証すること、表示寸法から安全な整数倍率を導出すること、全縦横比を通常入力として扱うこと。ユーザー指定の `((U * 4) - (U / 4))`、`(U / 2 + U / 8)`、`(U + (U / 2) + (U / 8))` のような合成表現は受入条件であり、小数や別表現へ置換しない。
- 主な調査コマンド: `rg -n` による記号参照検索、`git show 30f0b34`、`git diff 30f0b34^ 30f0b34 -- <file>`、`git blame -L ...`、スクリーンショット寸法の読み取り。新規検証基盤は作っていない。

# Confirmed

- 表示経路は次で確定する。
  1. `Pc98SurfaceView.surfaceChanged` が Surface の `width` / `height` と `context.resources.displayMetrics.density` を取得する。ここで渡している値は生の `densityDpi` ではなく、Android の正規化された密度係数 `density` である。根拠: `app/src/main/java/com/example/timeboxvibe/ui/main/Pc98SurfaceView.kt:63-72`。
  2. `DisplayScalePolicy.deriveScale(physicalWidth, physicalHeight, platformDensity)` が整数 `scale` を返す。根拠: `shared-engine/src/commonMain/kotlin/com/example/timeboxvibe/engine/core/EngineCanvas.kt:7-49`。
  3. `logicalWidth = physicalWidth / scale`、`logicalHeight = physicalHeight / scale` を保存する。根拠: `Pc98SurfaceView.kt:70-77`。
  4. フレームごとにその論理寸法を `SceneManager.setLogicalBounds` へ渡し、`SceneManager` が `logicalWidth` / `logicalHeight` とHUDを除いた領域を確定する。根拠: `Pc98SurfaceView.kt:250-286`、`SceneManager.kt:181-187`。
  5. Android `Canvas` 全体へ `canvas.scale(displayScale, displayScale)` を一度掛ける一方、`AndroidEngineCanvas.width` / `height` は論理寸法、`density` は常に `LOGICAL_ENGINE_DENSITY = 1f` にする。根拠: `Pc98SurfaceView.kt:280-286,369-370`。
  6. `Scenes.kt` は `SceneManager.logicalWidth` / `logicalHeight` と論理 play area を読む。DPIまたは物理寸法を直接読むコードはない。コマンド: `rg -n 'DisplayScalePolicy|platformDensity|displayMetrics|densityDpi|xdpi|ydpi' shared-engine/src/commonMain/kotlin/com/example/timeboxvibe/engine/core`。

- 現行 `DisplayScalePolicy` は、物理寸法が非有限、ゼロ以下なら倍率1を返す。密度は有限、`0.5 < density < 8`、かつ `1.0±0.01` 外だけを「信頼」する。信頼時は `(platformDensity * 2f).toInt()` を開始倍率にし、不信時は `min(physicalWidth, physicalHeight) / (U * 20)` を開始倍率にする。その後、論理幅だけを `[320, 1200]` へ寄せる。根拠: `EngineCanvas.kt:8-17,19-49`。

- よって `UIscalefix.md` の「信頼済み密度から概ね `(platformDensity * 2f).toInt()` を開始点にする」という現状説明は正しい。さらに、密度1付近を棄却しても、不信時フォールバックが画面短辺から倍率3などを作るため、「偽密度を棄却したから倍率1になる」とは限らない。

- 提示された3枚のPNGはいずれも `1080×2424`。PNG内の96dpiメタデータは Android の `displayMetrics.density` ではない。この物理幅を現行式へ入れると、密度1.00（不信時フォールバック）、1.50、2.00、2.625、3.00、4.00、9.00（不信時フォールバック）では最終倍率3、論理寸法 `360×808`、文字内倍率1の字形セルは48×48物理画素になる。密度1.25なら倍率2で32×32、密度0.75なら倍率1で16×16になる。これはコード式をそのまま転記した読み取り専用 PowerShell 計算で確認した。実 Surface 寸法がスクリーンショット全寸法と同じなら、ユーザーの「16×16で表示できない」という観察を直接説明できる。

- 字形セルそのものは倍率1で16×16論理画素である。`CANONICAL_UI_UNIT = 16`、`TEXT_SCALE_IDENTITY = 1`、`measureTextWidth = text.length * U * scale`、`measureTextHeight = U * scale`。`drawGlyphRaw` は `y < U`、`x < U` の16×16ビットを走査し、各オン画素を `scale×scale` の論理矩形として描く。根拠: `EngineCanvas.kt:5`、`ScaledProceduralRenderer.kt:14-17,21-30,86-140`、`ShinonomeFont.kt:4,11-16`。

- 最終的な字形セルの物理寸法は次になる。

  ```text
  物理字形セル = U × 文字内倍率 × 全体表示倍率
  ```

  したがって、文字内倍率1でも表示倍率3なら48×48、見出しまたはタイマーの文字内倍率2なら96×96になる。文字だけでなく、同じ論理座標で描く枠・余白・図形も外側の表示倍率を受ける。

- 描画と入力の全体倍率は対称である。描画は `canvas.scale(currentScaleFactor, currentScaleFactor)`、入力は `event.x / currentScaleFactor` と `event.y / currentScaleFactor`。ただし入力は直後に `.toInt()` で切り捨てるので、連続座標として完全同一ではなく1論理画素未満の量子化がある。根拠: `Pc98SurfaceView.kt:113-122,280-286`。

- 通常文字の既定文字内倍率は既に1である。`ScaledProceduralRenderer.drawGlyph`、`drawText`、`ProceduralTextRenderer.drawRaw`、`measureWrappedHeight`、`drawWrapped` はいずれも既定が `TEXT_SCALE_IDENTITY`。ボタン文字も `textScale = TEXT_SCALE_IDENTITY`。根拠: `ScaledProceduralRenderer.kt:16,25-36,86-96,142-153,677-720`、`ProceduralUiPrimitives.kt:83-121,253-333`。

- 文字だけを画面条件で変える現行経路は `ProceduralTextRenderer.headingScale(text, maxWidth)` に存在する。倍率2で全文が `maxWidth` に収まれば2、そうでなければ1を返し、`measureHeadingHeight` と `drawHeading` が再計算する。`maxWidth` は縮小後の論理画面とシーン配置から来ており、`text.length` は言語で変わるため、解像度・全体表示倍率・文言・言語により同じ意味の見出しが1または2へ変化する。根拠: `ProceduralUiPrimitives.kt:124-147`。現在 `Scenes.kt` の見出し呼出しは文字内倍率を指定できない。例: `Scenes.kt:946,1033,1336,1396,2770-2771,2911-2912,3335-3337`。

- 一方、すべての拡大文字が自動倍率というわけではない。メイン画面の中央タイマーは `NestedTimeboxInstrumentRenderer` が意味上の明示倍率2、補助タイマーは1で描く。根拠: `NestedTimeboxInstrumentRenderer.kt:417-468,491-529`。この倍率2はDPIから直接決まらないが、外側の表示倍率3と合成されると96物理画素セルになる。

- 画面寸法が文字以外の固有寸法を増やす箇所も実在する。例は設定行高 `rowH = maxOf(playAreaH * 3f / 25f, (U * 2).toFloat())`（`Scenes.kt:2481`）、テンプレートカード高 `maxOf(playAreaH * 3f / 20f, ((U * 4) - (U / 4)).toFloat())`（`Scenes.kt:939,1105,1169`）、ボタン幅、入力高、デトネータ高など（検索コマンド: `rg -n '(maxOf|minOf).*playArea.*U|(?:maxOf|minOf).*U.*playArea' Scenes.kt`）。このため「全体表示倍率」以外にも、画面寸法で一部部品だけが論理的に大きくなる経路がある。

- コミット `30f0b34` は `DisplayScalePolicy` を共通側へ追加し、汎用折返し、見出し自動倍率、ボタン文字の常時折返しを導入した。`git blame` では `EngineCanvas.kt:5-49`、`ProceduralUiPrimitives.kt:99-146`、`ScaledProceduralRenderer.kt:698-719` が同コミットに帰属する。ただし `density * 2` という開始倍率自体は同コミット以前から `Pc98SurfaceView.deriveScale` に存在し、このコミットはそれを `DisplayScalePolicy` へ移して不信密度フォールバック等を追加した。根拠コマンド: `git diff 30f0b34^ 30f0b34 -- Pc98SurfaceView.kt EngineCanvas.kt ScaledProceduralRenderer.kt ProceduralUiPrimitives.kt`。

- `canonical-unit-layout` による現状確認は次のとおり。

  ```text
  LAYOUT LAW CHECK:
  U source: CANONICAL_UI_UNIT = 16
  Display variables used: SceneManager.logicalWidth / logicalHeight と play area
  Unexplained constants: 画面比率から固有寸法を選ぶ複数式が残る
  Stacking behavior: 汎用 drawButton / drawWrapped で既定かつ呼出側から無効化不能
  Result: FAIL
  ```

- `dpi-scale-derive` による現状確認は次のとおり。

  ```text
  DPI SCALE CHECK:
  Platform DPI: Android displayMetrics.density（正規化密度係数）
  DPI accepted/rejected: 有限性・範囲・1付近を検査
  Fallback used: 不信密度時は短辺 / (U * 20)
  Scale: 信頼密度 * 2、または短辺フォールバックから開始
  logicalWidth: physicalWidth / scale
  logicalHeight: physicalHeight / scale
  Constants used: MIN_SAFE_LOGICAL_WIDTH=320f, MAX_SAFE_LOGICAL_WIDTH=1200f, MIN_SCALE=1
  Result: FAIL（密度が倍率開始値を強制し、最終調整は高さを検証しない）
  ```

# Rejected

- 「`Scenes.kt` がDPIを直接読み、通常文字の既定倍率を上げている」という説明はコード上は否定される。`Scenes.kt` が受け取るのは既に縮小済みの論理寸法であり、通常文字既定は1。正確な原因は、外側の表示倍率が物理セルを一律拡大する経路と、縮小済み論理幅を通じて見出し倍率・折返し・部品寸法が二次的に変わる経路の合成である。

- `ScaledProceduralRenderer.drawGlyph` のコメント「canvas density に応じて自動拡大」は現行実装と一致しない。`drawGlyph` / `drawGlyphRaw` は `canvas.density` を読まない。`canvas.density` の参照は同ファイル内ではデモ用装飾の線幅等（`ScaledProceduralRenderer.kt:519-538`）だけで、Androidでは1へ固定される。このコメントを倍率経路の根拠にはできない。

- `UIscalefix.md` の「Android表示層は原因ではない」は、全体変換と入力逆変換の対称性だけを指すなら正しいが、表示倍率の選択まで無関係という意味なら否定される。`surfaceChanged` が選んだ倍率をAndroid `Canvas` へ掛けるため、倍率3なら倍率1の16論理画素字形も48物理画素になる。変換機構は維持対象だが、倍率選択方針は主要修正対象である。

- 「全テキストにDPI由来の文字内倍率がある」は否定される。DPI由来なのはUI全体の外側倍率であり、通常文字内倍率は1。文字固有の変動は見出し自動倍率1/2と、中央タイマー等の明示倍率2に限られる。

- `UIscalefix.md` の提案コードをそのまま採用することはできない。提案は信頼密度時に `densityLimit = platformDensity.toInt()` を倍率上限にするため、表示幾何から必要な倍率へ到達できない場合がある。例として物理幅2400、密度1.5では `densityLimit=1` となり、候補2を拒否して倍率1、論理幅2400のままになり、提案自身の最大論理幅1200を破る。DPIを助言値ではなく安全調整の妨害要因にしている。

- 提案の `baseCardH = U * 3.75f` は、数値として同じでもユーザー指定表現を置換するため不採用。現行の `((U * 4) - (U / 4)).toFloat()` を正本として残す必要がある。同様に、指定された `U` の和・差・分割表現を小数倍率へ正規化してはならない。

- `maxOf(画面寸法, U)` を一律撤去する方針は採用できない。`canonical-unit-layout` は `logicalWidth` / `logicalHeight` と実測文字寸法によるレスポンシブ領域配分を許可している。設定行高のような固有行高と、残り幅・中央位置・収容数のような領域計算を記号ごとに分類する必要がある。

- 見出しAPIの既定を1に変えるだけでは、ユーザーが英語スクリーンショットで指摘した「文字サイズがすべて同じ」を残す。見出しを意味上2とするなら、言語や空き幅ではなく呼出側が明示し、測定と描画へ同じ値を渡す必要がある。`UIscalefix.md` も意味上の拡大を許しているが、現行の各 `drawHeading` 呼出しにどの倍率を残すかを確定していない。

- `UIscalefix.md` 4.4 の「文字積み上げを変更しない」は今回の明示依頼（積み上げを削除せず、任意化して改善する）と両立しない。この節を完了条件として扱ってはならない。

# Unknown

- 3枚のPNGから実端末の `displayMetrics.density` と実際の Surface 有効寸法は確定できない。PNG寸法は1080×2424、メタデータは96dpiだが、どちらも `platformDensity` の証拠ではない。したがってスクリーンショット時の最終倍率が厳密に3だったことは未確定。ただし実 Surface 幅1080かつ上記の広い密度範囲なら3になることは式から確定する。

- 各見出しの意味上の望ましい倍率が1か2かはコードだけでは決められない。英語と日本語で同じ意味階層を維持すべきことは明確だが、最終指定は設計判断である。ユーザーの「英語だけ全テキスト同サイズは問題」という記述からは、少なくとも全見出しを機械的に1へ落とす案は支持されない。

- 中央タイマーの明示倍率2を維持するかは意味上の設計判断である。これは自動倍率ではないため、DPI境界修正の機械的対象には含められない。

- 現行リポジトリで `DisplayScalePolicy` の実呼出しは Android `Pc98SurfaceView` だけだった。Win32/iOS側に同等の提示層が別リポジトリまたは未実装で存在するかは不明。

- どの画面比率式が固有寸法で、どれが許可された領域配分かは全記号の用途確認が必要。確認済みの設定 `rowH` とテンプレート `baseCardH` は固有寸法候補だが、幅、中央位置、スクロール容量まで同じ置換を広げる根拠はない。

# Recommendation

- 最小の倍率修正は `DisplayScalePolicy` に限定して先に行う。
  - `MIN_SCALE = 1` から開始する。
  - `MIN_SAFE_LOGICAL_WIDTH`、`MAX_SAFE_LOGICAL_WIDTH`、`MIN_SCALE`、`CANONICAL_UI_UNIT` の名前付き正本を維持する。
  - 生密度は有限性・範囲・偽値を検査しても、開始倍率や幾何学的に必要な倍率の上限には使わない。倍率は物理表示寸法と `U`、名前付き論理範囲から決める。
  - 倍率変更のたびに論理幅・論理高を再計算し、短辺を破壊する候補を採用しない。端末名、OS版、縦横、固定端末解像度では分岐しない。
  - 1080幅は倍率1で論理幅1080となり既存 `[320,1200]` 内なので、文字内倍率1の字形を16×16物理画素で表示できる。

- Android提示層の `canvas.scale(finalScale, finalScale)`、入力の同倍率逆変換、`EngineCanvas.density = 1f`、`logicalWidth/Height = physical/finalScale` という境界は維持する。別の文字倍率やシーン倍率をAndroidへ追加しない。

- 見出しは `headingScale(text, maxWidth)` を廃止し、測定・描画の双方が同じ明示 `textScale` を受け取るAPIにする。通常文字は1。意味上の見出しを2とする場合は、言語や空き幅で変えず各見出し呼出し側で2を明示する。これにより英語だけ1、日本語だけ2という差をなくしつつ、見出しと本文の視覚階層を保持できる。

- 中央タイマー等の既存明示倍率2は、自動DPI倍率と混同して機械的に削除しない。意味上必要かを個別に確認し、維持する場合も式 `U × 2` と周辺レイアウトを同じ論理座標で扱う。

- 画面比率由来の固有寸法修正は、倍率境界の後に記号単位で限定する。設定行高なら `(U * 2).toFloat()`、テンプレートカード高なら現行の `((U * 4) - (U / 4)).toFloat()` のように、ユーザー指定の `U` 合成表現をそのまま使う。領域幅、中央寄せ、スクロール容量、収容可否の計算は論理表示寸法を使い続ける。

- 積み上げは別経路として任意化し、今回のユーザー依頼どおり削除しない。少なくとも汎用 `drawButton` が全ボタンを無条件に折り返す現状は、呼出側が一行表示または積み上げを明示できる契約へ変える必要がある。倍率修正で折返し問題を隠さず、測定高と描画高は同じ選択結果を共有する。
