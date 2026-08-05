# UI・グラフィックスシステム監査

監査日: 2026-07-30

## 1. 範囲と方法

監査対象は次のディレクトリだけである。

`shared-engine/src/commonMain/kotlin/com/example/timeboxvibe/engine/core`

対象30ファイル、合計7,877行を全文で読み、対象内の呼び出し関係を照合した。死んだコードの判定に必要な場合だけ、リポジトリ内Kotlinソースから外部参照の有無を確認した。プラットフォーム実装そのものは監査対象に含めていない。

本監査は、次の観点を扱う。

- 同じ概念に複数の実装規約が存在する箇所
- モジュール単位での差し替えや変更を難しくする結合
- 同一機能の重複、分断、旧経路との併存
- 現行経路から到達しない試作・デモ・診断コード
- レイアウト単位、論理表示寸法、パレット番号、aliased描画の扱い

「確認済み」はソース上で経路を確認できた事実、「候補」は用途や将来計画を確認してから扱うべき事項を示す。設計思想との衝突や製品上の不具合は、根拠が不足する限り断定していない。

テスト、ビルド、実行、画像比較は行っていない。本番コードも変更していない。

## 2. 全体所見

共通エンジン内には、パレット番号中心の描画、手続きアイコン、整数スナップするaliasedベクタ層、事前確保した回転グリフバッファ、表示寸法から組み立てるIMGUI行レイアウトなど、明確な独自方針がある。対象内にプラットフォームUIフレームワーク、Java API、外部画像・音声の読み込みは見つからなかった。プラットフォーム要求も小さなインターフェースまたは `expect` 関数に留まっている。

一方、機能追加の世代が重なった結果と思われる複数経路が残っている。特に次の四点は、現在のコードを理解・変更する際の負担が大きい。

1. 魔法陣の状態管理、更新、描画、設定トグルが同じ機能名の下で部分的に分断されている。
2. 描画矩形と当たり判定矩形、Settingsの行順、HUD配置などが複数箇所で手動同期されている。
3. 正準セル `U`、プレイ領域比率、円・線のラスタ化、アイコン色などに複数の所有者がいる。
4. 本番経路に常設診断ログがあり、その周囲に到達しないデバッグ状態や旧描画入口が残っている。

直ちに大規模な共通化を行うべきという結論ではない。独自IMGUIでは局所的な重複が明快さや無割り当て実行に寄与する場合もある。以下では、現行の重複が「意図的な専門化」なのか「同期が必要な複製」なのかを区別している。

## 3. 機能所有と描画経路

### 3.1 魔法陣のデモ効果が二つの実装へ分かれている

確度: 高

`MagicCircleDemoscene` は6個の `Wave`、6リンクの `IkChain2D`、尾のアルファ・サイズ配列を所有する。`ActiveTimerScene` はこれを生成・リセットし、毎フレーム更新する（`MagicCircleDemoscene.kt:16-112`、`Scenes.kt:80-120`）。

しかし、`NestedTimeboxInstrumentRenderer` がこの状態から実際に読むのは `runeDriftAngleOffset()` だけである（`NestedTimeboxInstrumentRenderer.kt:166-183`）。次の状態は生成・更新・リセットされるが、描画から読まれていない。

- `pentaBreath`
- `outerHeartbeat`
- `innerHeartbeat`
- `coreWobble`
- `sectorSwing`
- FABRIKの `trail`
- `trailAlpha`
- `trailSize`

現行のコメット尾は、FABRIKとは別にレンダラー内の4点ループで半径・角度・アルファを計算して描く（`NestedTimeboxInstrumentRenderer.kt:295-317`）。`MagicCircleDemoscene.solveTrail()` には呼び出し元がない（`MagicCircleDemoscene.kt:89-98`）。

また、設定の `demosceneEffectsEnabled` は `MagicCircleDemoscene.update()` とルーン揺らぎを停止するが、レンダラー内の4点尾は設定値を確認せず描画される。したがって、「全デモ効果」の状態所有者、更新元、描画元、無効化範囲が一致していない。

これは現行4点尾と旧または将来用FABRIK尾のどちらが正しいかを決める指摘ではない。まず各効果について、採用中・将来予約・撤去候補のどれかを確認する必要がある。

### 3.2 背景Perlin処理にも二つの所有場所がある

確度: 高

`MagicCircleDemoscene.nebulaSample()` は設定判定と2オクターブfbmを実装するが、呼び出し元がない（`MagicCircleDemoscene.kt:124-132`）。製品描画は `ActiveTimerScene.nebulaColorIndex()` が `PerlinNoise.fbm()` を直接呼び、別の係数と二地点平均を使ってパレット番号へ量子化する（`Scenes.kt:159-179,871-910`）。

背景ノイズの所有をデモ状態側に置く設計と、シーン側に置く設計が併存しており、現行では後者だけが有効である。設定、時間係数、サンプル位置、色量子化の変更点が一か所に揃っていない。

### 3.3 円・線の描画政策が二重に公開されている

確度: 高

`AliasedVectorLayer` は自身を円・線・弧・Bezierの共通整数ラスタライザとして説明する（`AliasedVectorLayer.kt:6-15`）。一方、`EngineCanvas` も線と円の高水準描画をプラットフォーム実装へ要求する（`EngineCanvas.kt:18-23`）。

`ScaledProceduralRenderer` は両方を公開している。

- `drawLine()`、`drawCircle()`、`drawCircleStroke()` は `EngineCanvas` へ直接転送する（`ScaledProceduralRenderer.kt:48-61,378-380`）。
- `drawAliasedLine()`、`drawAliasedCircle()`、`drawAliasedArc()` は `AliasedVectorLayer` を通す（同 `:575-634`）。
- `drawPolarStarLinks()`、`drawActivePolarTickLoop()` など一部の高水準図形は再び `canvas.drawLine()` を直接使う（同 `:427-455,487-510`）。

現行の主要な円装飾はaliased経路を使用しているが、APIとしては同じ図形に複数のラスタ化規約が残る。これはプラットフォームごとの差を許す意図なら有効な分離である。一方、共通側で画素結果を統一したい場合は、どの図形がキャンバス委譲で、どれが共通ラスタ化なのかを明示しないと、新しい部品が任意の経路を選べてしまう。

### 3.4 アイコンだけがARGB風トークンを中間表現にする

確度: 高

`getPixelColor()` は手続きアイコンの画素として多数の32ビットARGBリテラルを返す（`ProceduralMath.kt:13-240`）。`ProceduralIconRenderer` は同じ値を再宣言し、`mapIconColor()` でパレット番号へ戻してから描画する（`ProceduralUiPrimitives.kt:3-57`）。

他の描画系は最初からパレット番号を受け取るため、アイコンだけが次の二段契約を持つ。

`数式形状 → ARGB風トークン → 値一致によるパレット番号`

生成側と写像側が色リテラルの一致に依存し、色追加時には両方の変更が必要になる。さらに `getPixelColor()` の `onBackgroundColor` と `surfaceColor` は生成処理では読まれず、後段へ渡るだけである。意図的な色分類トークンなのか、移行途中の互換表現なのかを確認する価値がある。

### 3.5 ボタン描画が三系統に分かれている

確度: 高

- 一般テキストボタン: `ScaledProceduralRenderer.drawButton()`（`ScaledProceduralRenderer.kt:671-718`）
- タイマー用アイコンボタン: `ActiveTimerScene.drawIconButton()`（`Scenes.kt:698-728`）
- HUD用アイコンボタン: `RetroHudComponent.drawHudButton()`（`RetroHudComponent.kt:238-275`）

いずれも外枠、内側塗り、選択時の反転、中央配置を個別に実装する。アイコンボタンとテキストボタンを分けること自体には意味があるが、枠の太さと選択表現まで各実装が所有するため、共通の視覚規約を変える場合は三か所の同期が必要になる。

Active Timerは内枠を生の `2f` とし、他二系統は `U / 8` を別々に表現している（`Scenes.kt:708-717`、`RetroHudComponent.kt:18-20,252-260`、`ScaledProceduralRenderer.kt:18-20,686-690`）。現在値は一致している。

### 3.6 点・ビーズの `size` の意味が入口ごとに違う

確度: 高

- `drawPolarDot(size)` は `size` を円の半径として扱う（`ScaledProceduralRenderer.kt:173-185`）。
- `drawPolarBead(size)` は `size` を直径として半分を半径へ渡す（同 `:412-424`）。
- `drawActivePolarBeadLoop(beadSize)` は円ではなく一辺 `beadSize` の矩形を描く（同 `:462-485`）。

`drawPolarBead()` は現在未使用なので現行表示の不具合ではない。再利用時の取り違えを防ぐには、半径・直径・一辺のどれかが名前から分かる契約が望ましい。

## 4. レイアウトと入力の一貫性

### 4.1 正準セル `U` の値が複数ファイルに分散している

確度: 高

`U = 16` は少なくとも次の場所で個別に定義される。

- `Scenes.kt:9`
- `AliasedVectorLayer.kt:17`
- `ScaledProceduralRenderer.kt:14`
- `EngineCursorRenderer.kt:16`
- `ProceduralUiPrimitives.kt:61`
- `RetroHudComponent.kt:18`
- `NestedTimeboxInstrumentRenderer.kt:40`

現在値はすべて一致している。ただし、UI単位としての `U` とROMグリフ形式としての16が別概念なのか、同じ正準値を共有すべきなのかが型や所有場所からは分からない。`ScaledProceduralRenderer` にはさらに `GLYPH_CELL_COUNT = 16`、回転中心 `7.5f`、補正 `+8`、範囲 `0..15`、ビットマスク `0x8000` がある（`ScaledProceduralRenderer.kt:14-15,222-243`）。

値を単純に一つへまとめる提案ではない。少なくとも「レイアウト単位」と「ROMデータ形式」を別々の名前付き契約として整理すると、変更時の同期範囲が明確になる。

### 4.2 同じUI層に二つの寸法体系がある

確度: 高

Template ForgeとSettingsは、主に `U`、文字幅、表示領域比率から行高・余白・横並び／積み上げを求める（`Scenes.kt:1327-1352,1951-2055,2379-2441`）。

一方、次のシーンには固定論理値が多い。

- Active Timer: `20f`, `36f`, `8f`, `42f`, `10f`, `16f`, `32f`（`Scenes.kt:45-55`）
- Template Customizer: `60f`, `6f`, `30f`, `24f`, `20f`, `40f`, `90f`, `26f`, `92f`, `28f` など（同 `:960-1053`）
- Block Overlay: 最小幅 `200f`、最小高 `32f`（同 `:3166-3184`）

これらにはアイコン固有寸法、既存の見た目を保つ下限、アニメーション固有値が混在している可能性がある。固定値を一括で問題視するのではなく、文字セル由来・部品固有・表示下限・時間表現に分類できると、どこまでが意図された例外か判断しやすい。

### 4.3 論理画面高の復元則が複数シーンへ複製されている

確度: 高

下HUD時のプレイ領域は `RetroHudComponent` の `17/20` で求められる（`RetroHudComponent.kt:24-55`）。入力側では元の論理高を復元するため、`playH * 20f / 17f` がActive、Template、Forge、Settings、Block Overlayへ直接複製されている（`Scenes.kt:460,493,1064,1120,1419,1476,2192,2222,3179`）。

HUD側にも同等の `logicalHeightFromPlay()` がある（`RetroHudComponent.kt:281-286`）。HUD比率を変更する場合、名前付き比率と各シーンの生リテラルを同時に更新する必要がある。プレイ領域矩形または論理表示寸法を一つの小さな値として渡す、あるいは復元関数の所有を一か所にする余地がある。

変数名 `isPortrait` は多くの場所で使われるが、実際の判定は画面の縦横ではなく `playX <= 0`、すなわちHUDが下側に配置されたかを示す。向き依存の不具合とは判定しないが、`usesBottomHud` のような名前の方が実際の意味に近い。

### 4.4 描画と当たり判定が同じ幾何を別々に計算する

確度: 高

代表例は次のとおり。

- HUDの4ボタン: 描画 `RetroHudComponent.kt:76-120`、判定 `:193-235`
- Template Customizerのカード、編集・削除ボタン: 描画 `Scenes.kt:959-1053`、判定 `:1122-1174`
- Entropyのポップアップ: 描画 `:2798-2825`、判定 `:2854-2875`
- Entropyの入力欄、ページャー、行、起爆ボタン: 描画 `:2687-2792`、判定 `:2888-2967`
- Active Timerの入力欄: 描画 `:239-284`、判定 `:514-529`

現時点で主要式は対応しており、明確な座標ずれは確認しなかった。問題は、視覚変更時に当たり判定を別途更新する必要があることである。汎用UIフレームワークではなく、割り当てを発生させない小さな矩形計算関数や固定レイアウト結果を共有するだけでも同期点を減らせる。

### 4.5 Settingsの行定義が描画・測定・入力の三か所に分かれている

確度: 高

Settingsは `layoutRow()` によりラベル幅を測り、横並びまたは縦積みを選ぶ（`Scenes.kt:2414-2441`）。個々の幾何計算は一貫しているが、行の順序と存在は次の三経路で手動同期される。

- 入力処理: `Scenes.kt:2229-2376`
- 描画: `drawSettingsRows()`、同 `:2443-2502`
- スクロール高測定: `measureSettingsRows()`、同 `:2512-2529`

設定行を追加・削除・並べ替える場合、三か所が同じ順序でなければ入力座標と表示がずれる。

また、`visualsHeader` は `layoutRow(renderer, ...)` が一度描画した直後、`drawTextRaw()` でも再描画される（`Scenes.kt:2486-2487`）。通常行か専用見出しかの扱いが混在している、確認可能な二重描画である。

### 4.6 シーンごとにスクロール入力状態機械が複製されている

確度: 高

Active Timer、Template Customizer、Template Forge、Settingsは、それぞれ次の状態と処理を持つ。

- `scrollY`
- `lastTouchY`
- `isDragging`
- `initialTouchX/Y`
- `hasDragged`
- DOWN/MOVE/UP/CANCEL処理
- タップとドラッグを分ける距離判定

基本構造は同じだが、Forgeだけしきい値に生の `2f` と `8f` を使い、他は `U / 4` と `U / 2` を使う（`Scenes.kt:442-479,1072-1108,1427-1459,2172-2211`）。シーン固有のスクロール範囲計算は分離する必要がある一方、ジェスチャ状態機械まで複製する必要があるかは検討対象になる。

### 4.7 `Scene` の入力APIが二つの意味を持つ

確度: 高

`Scene` は文字・コマンド用の `onInput(inputCode)` に加え、座標付きの `onInput(x,y,action,...)` と `onTouch(x,y,action,...)` を持つ（`Scenes.kt:18-26`）。多くのシーンは `onTouch()` でドラッグを処理し、タップ確定時に座標付き `onInput()` を呼ぶ。

動作は追跡可能だが、「入力」が文字入力とタップ処理の両方を指し、各シーンがHUD転送まで担当するため、新しい入力種別や共通ジェスチャを追加する際の入口が分かりにくい。名前と責務を明確化するだけでも理解負担を減らせる。

### 4.8 HUD入力で同じ判定が二度実行される

確度: 高

`SceneManager.drainTouchBuffer()` は全タッチで `RetroHudComponent.onTouch()` を呼ぶが、戻り値を使用しない（`SceneManager.kt:247-264`）。その後、HUD-only分岐では `onTouchEvent()` が再び `onTouch()` を呼ぶ。通常のfull分岐でも各シーンが `onTouchEvent()` を呼び、同じ再判定が起きる（`RetroHudComponent.kt:124-140`、`Scenes.kt:482-489,1111-1114,1462-1465,2214-2217,2841-2844`）。

先行呼び出しに状態変更はないため、現状では結果を捨てる重複計算である。過去のログ用途または入力切り分けの残存経路か確認できる。

### 4.9 HUDとシーン遷移に二つの方式がある

確度: 高

HUDは `SceneCommand` を保留し、`SceneManager.update()` が後で消費する（`RetroHudComponent.kt:29-34,144-171`、`SceneManager.kt:121-124`）。一方、TemplateやEntropyのシーンは `SceneManager.switchScene()` を直接呼ぶ（`Scenes.kt:1137-1173,1488,1618,2883`）。

入力排出中の `switchScene()` は保留されるため、どちらも現行では機能する。ただし、遷移の追加時に「識別子経由」と「具象シングルトン経由」の二つを選べる。HUDも選択タブ判定で具象シーン型を列挙する（`RetroHudComponent.kt:64-71`）。同一タブ配下のシーン追加時にはHUD側も変更が必要になる。

## 5. 描画・状態契約の一貫性

### 5.1 `EngineCanvas.density` の契約が利用側から一意に読めない

確度: 中

`EngineCanvas` は論理 `width/height` とともに `density` を公開し、「1.0 means 1 dp = 1 px」と説明する（`EngineCanvas.kt:12-15`）。一部の描画入口は線幅、半径差、弾サイズへ `density` を再乗算する（`ScaledProceduralRenderer.kt:317-365,513-574`）。`NestedTimeboxInstrumentRenderer` も細線幅として `density` を読む（`NestedTimeboxInstrumentRenderer.kt:130-132`）。

現在のキャンバスモデルで全実装が `density == 1f` を保証するなら結果は揃う。しかしインターフェース自体は他値を許すため、実装者が画面密度を渡した場合、論理座標だけを使う経路と密度を再適用する経路が混在する。

プラットフォーム側のDPI検証やscale導出は今回の範囲外であり、適否は判定していない。ここでの指摘は、共通インターフェースの契約と利用方法の表現が曖昧な点に限る。

### 5.2 通常グリフと回転グリフでクリップ粒度が異なる

確度: 高

`drawGlyphRaw()` は拡大画素矩形の原点がクリップ内かを判定してから、幅・高さ `scale` の矩形を描く（`ScaledProceduralRenderer.kt:103-134`）。`scale > 1` では、原点が右端または下端直前にある矩形の残りがコンポーネントのクリップ外へ出る可能性がある。

一方、回転グリフは拡大後の各画素をループし、キャンバス境界を確認して `setPixel()` する（同 `:263-315`）。`drawUpperClipped()` は文字単位の右端確認も持つ（`ProceduralUiPrimitives.kt:81-112`）。

これはキャンバス最終境界の安全性とは別に、「指定した部品矩形へ文字を切り抜く」という契約が入口ごとに異なるという観察である。

### 5.3 パレット状態の不変条件を外部から迂回できる

確度: 高

`Pc98GraphicsHardware.setupPalette()` は変更時に `paletteRevision` を増やす（`Pc98GraphicsHardware.kt:37-54`）。しかし `onScreenPalette` と `paletteRevision` は公開可変であり、配列の直接変更によってrevision更新を迂回できる（同 `:33-35`）。対象内に直接書き換えはなく、現行不具合ではない。

また、`EngineThemes.getColors()` は名前上は取得関数に見えるが、戻り値を返さずグローバルパレットを変更する（`EngineThemes.kt:115-123`）。テーマ選択とハードウェア状態変更が一つの入口に結合しているため、純粋なテーマ参照が将来必要になった場合は分離が難しい。

### 5.4 `setDrawAlpha()` は任意実装になっている

確度: 中

`EngineCanvas.setDrawAlpha()` には既定の空実装がある（`EngineCanvas.kt:17`）。魔法陣レンダラーはレイヤーごとに頻繁にアルファ値を変更する（`NestedTimeboxInstrumentRenderer.kt:158-353`）。

各プラットフォームが上書きしているかは監査範囲外なので、表示差は断定できない。ただし、新しいキャンバス実装が上書きを忘れてもコンパイル上は成功し、レイヤー表現だけが無効になる契約である。アルファ非対応を正式に許すのか、実装必須なのかを明記する余地がある。

### 5.5 文字列の単純描画入口が三層にある

確度: 高

- `ScaledProceduralRenderer.drawText()`（`ScaledProceduralRenderer.kt:136-157`）
- `ProceduralTextRenderer.drawRaw()`（`ProceduralUiPrimitives.kt:64-79`、呼び出しなし）
- `Scenes.kt` 内の同等な `drawTextRaw()` 群（`Scenes.kt:862-869,1203-1210,2085-2092,2578-2585`）

大文字化、プリセットID短縮、切り抜きには `ProceduralTextRenderer` 固有の役割がある。一方、単純なグリフ列描画は重複しており、共通入口側が未使用である。高水準レンダラーへ置くか、UI用補助へ置くか、シーン内へ明示的に残すかの所有方針が揃っていない。

### 5.6 文字入力の32ビット保証がシーンごとに異なる

確度: 高

`FixedInputContainer` は32ビットコードポイントを保持する（`FixedInputContainer.kt:3-27`）。しかし利用側は次のように異なる。

- Active Timerは `> 0xFFFF` をサロゲートペアへ戻す（`Scenes.kt:410-425`）。
- Template Forgeの `inputToString()` は `> 0xFFFF` を読み飛ばす（同 `:2072-2082`）。
- Entropyは描画では `?` に置換し、保存用文字列化では読み飛ばす（同 `:3090-3100,3125-3137`）。
- 既存文字列を入力バッファへ設定する箇所はUTF-16の `Char` 単位で投入する（同 `:95-98,2063-2069`）。

同じ入力プリミティブが、保持・表示・保存で異なる保証を持つ。同プリミティブをUnicode scalar対応として使うのか、BMP限定として使うのかを決めると、シーンごとの差を整理できる。

## 6. 高頻度経路に関する観察

### 6.1 Active Timer描画に割り当て候補がある

確度: 中

`ActiveTimerScene.render()` は毎フレーム、次を実行する。

- `state.presets.firstOrNull { ... }`（`Scenes.kt:188`）
- プリセットが見つからない場合の `IntArray(0)`（同 `:191`）
- `SessionMacroDisplay.resolveMacro()` の `Pair<Int, Int>` 生成と分解（`SessionMacroDisplay.kt:47-64`、`Scenes.kt:189-196`）

同じオブジェクト内の `activePreset()` は `while` ループで検索する（`Scenes.kt:666-674`）ため、同一シーン内でも高頻度経路の書き方が揃っていない。

Kotlin/Nativeでの実際の割り当てや費用は計測していないので、性能障害とは断定しない。無割り当て方針を描画経路へ厳密に適用する場合の確認候補である。

### 6.2 FABRIKの説明と割り当て特性が一致しない

確度: 高

`IkChain2D` はallocation-freeと説明するが、`points` は `Array<Point2D>` で、`Point2D` はdata classである（`IkChain2D.kt:5-19`、`ProceduralMath.kt:5-10`）。`solve()`、`reset()`、`placeLinearly()` は各点へ新しい `Point2D` を反復代入する（`IkChain2D.kt:31-108`）。

現在 `solveTrail()` は未接続なので、現行フレームでこの割り当ては発生していない。再接続する場合に、コメント上の期待と実装が一致しない点が表面化する。

### 6.3 FrameClockの更新入口で時間意味が異なる

確度: 高

`SceneManager.update(dt)` は `FrameClock.tick()` 後に3引数版 `update()` を呼ぶ（`SceneManager.kt:94-100`）。3引数版も冒頭で `FrameClock.tick()` する（同 `:103-108`）。したがって単引数版は一回の更新で二度進み、3引数版は一度進む。

現行Android経路は3引数版を直接呼んでおり、単引数版の呼び出しはリポジトリ内Kotlinソースに見つからなかった。このため、現行Android表示の障害とは扱わず、未使用オーバーロードに残る潜在的不整合とする。

`FrameClock` の読み取りAPIでは `seconds()` だけが実使用され、`reset()`、`frame()`、`phase()`、`rotation()`、`elapsed()` は参照がない（`FrameClock.kt:19-57`、`Scenes.kt:149`）。`elapsed()` は「前回呼び出し以降」と説明するが、実装は起動以来の `seconds()` をそのまま返す。API説明と実装も揃っていない。

## 7. 死んだコード、デモ、診断経路

### 7.1 SceneManagerに常時有効な診断ログがある

確度: 高

シーン切替では毎回、開始、`onExit` 前後、active設定、`onEnter` 前後、終了を `println` する（`SceneManager.kt:71-92`）。タッチがあるフレーム、切替後描画、言語変更、入力排出上限、タッチ例外にも個別ログがある（同 `:109-169,196-205,241-244,266-268`）。

防御用の例外・上限ログと、一時的な順序追跡ログが同じ場所に混在している。少なくとも次は現行経路上で役割を持っていない。

- `debugNextTouchDispatch`: 宣言後に使用なし（`SceneManager.kt:32`）
- `debugNextTouchUpdate`: 読み取りはあるがtrueに設定されない（同 `:33,133-141`）
- `debugLanguageChangedRecently`: 設定・解除されるが条件に使われない（同 `:31,140,196-199`）
- `sceneBefore`: 値を作るが読み取らない（同 `:255`）

診断をすべて不要とする結論ではない。製品防御ログ、現在使用中の調査スイッチ、完了済み調査の残骸に分類することが先になる。

### 7.2 HUDに旧アクションと思われる列挙値が残る

確度: 高

`HudAction.TIMER_START_STOP`、`TIMER_RESET`、`TIMER_SKIP`、`TOGGLE_TICKS`、`TOGGLE_VIBE`、`FOCUS_INPUT` は宣言と `actionName()` の分岐以外に参照がない（`RetroHudComponent.kt:3-15,177-191`）。`actionName()` 自体にも呼び出しがない。

現在のHUDが4タブ専用になった後の旧APIか、将来のHUD拡張用予約かは不明である。

### 7.3 未到達の描画APIとデモ入口が残る

確度: 高

リポジトリ内Kotlin呼び出しを照合した結果、次は宣言または内部転送以外から呼ばれていない。

- `AliasedVectorLayer.drawAliasedPolyline()`
- `ScaledProceduralRenderer.drawProgressTracks()`
- `drawCircleStroke()`（未使用デモ内からだけ使用）
- `drawSegmentedArc()`
- `drawPolarBead()`
- `drawProceduralPolarDemo()`
- `drawBulletPattern()`
- `drawRadialProgressTickMarks()` の公開転送と下層実装
- 二次・三次Bezierの公開転送と下層実装

`drawProceduralPolarDemo()` は名称と固定演目から、製品レンダラー内に残るデモ入口と判断できる（`ScaledProceduralRenderer.kt:513-533`）。その他は、予約済みプリミティブ、旧描画経路、未完成機能のどれかを履歴なしには決められない。

未使用の `drawProgressTracks()` は `canvas.density`、直接 `canvas.drawLine()`、固定セグメント数を使い、現行aliased tick経路とは別の描画政策を保持する（`ScaledProceduralRenderer.kt:317-366`）。再接続時に旧規約も同時に復活する点には注意が必要である。

### 7.4 シーン内に未使用ヘルパーと状態が残る

確度: 高

- `ActiveTimerScene` の旧中央表示ヘルパー群: `drawStaticTextCentered()`、`drawStageLabelCentered()`、`drawTimeCentered()`、`drawAlarmTimeCentered()`、`drawTextRaw()`（`Scenes.kt:746-793,829-869`）。実使用版は `NestedTimeboxInstrumentRenderer.kt:417-539` にある。
- `TemplateCustomizerScene.drawTextRaw()`（`Scenes.kt:1203-1210`）
- `BlockOverlayScene.drawHollowRect()`（同 `:3192-3197`）
- `stripeCount`（同 `:326`）
- Active Timer、Template Customizer、Settingsの `cachedLogicalWidth/cachedLogicalHeight` は代入後に読まれない（同 `:70-71,126-127,917-918,942-943,2098-2099,2142-2143`）。
- Entropyの `cachedLogicalWidth` は代入後に読まれない（同 `:2606,2669`）。

`TemplateForgeScene.cachedLogicalWidth` と `EntropyScene.cachedLogicalHeight` は入力レイアウトで使用されるため、この一覧には含めていない。

`MainMenuScene` は全メソッドが空でレジストリにもなく、リポジトリ内参照は `sceneName()` の型判定だけである（`Scenes.kt:31-39`、`SceneManager.kt:39-45,292-303`）。一方、`BlockOverlayScene` 本体はAndroidの `BlockOverlayActivity` から使われているため、未使用とは判定していない。

### 7.5 未接続の汎用メモリ・描画補助がある

確度: 高

`Poolable`、`ObjectPool`、`ParticleSystem` は定義以外のKotlin参照がない（`MemoryPrimitives.kt:3-78`）。UI効果・粒子用の先置き基盤なのか、過去の未撤去コードなのかは不明である。

`TouchColliderManager.checkCircle()` も呼び出しがなく、AABBだけがHUDで使用される（`TouchColliderManager.kt:3-20`、`RetroHudComponent.kt:208-233`）。円形入力プリミティブを将来使用する可能性があるため、未使用という事実だけで削除対象とはしない。

### 7.6 恒久的な検証・診断に見えるコードがタイマー境界にもある

確度: 高

`TimerPreset.validate(logFailures)` は検証失敗を `println` する（`TimerEngine.kt:108-147`）。`TimerEngine` の生成とpreset変更は `normalized(logFailures = true)` を常に使い、Template Forgeからもtrue指定がある（`TimerEngine.kt:226-230,360-363`、`Scenes.kt:1302,1732`）。

これは無効入力に対する製品ログか、一時的な検証機構かで評価が変わる。監査では削除を決めず、常時有効な診断経路として用途確認を求める。

`TimerEngine.isDirty` は複数箇所でtrueに設定されるが、読み取りやfalseへの復帰が見つからない（`TimerEngine.kt:269-270,390-392,517-518,532-533,668-669`）。状態変化通知としては現在接続されていない候補である。

## 8. コメントと実装のずれ

次は動作判断よりも、将来の読解時に誤った前提を作る可能性がある。

- `NestedTimeboxInstrumentRenderer` は「9-band」と説明しながら14層を列挙する（`NestedTimeboxInstrumentRenderer.kt:7-22`）。
- 同レンダラーの説明はWaveとFABRIK尾が接続されているように読めるが、実使用はPerlinルーン揺らぎと独立4点尾である（同 `:30-36,166-183,295-317`）。
- `MagicCircleDemoscene` はレンダラーが `Wave.value()` と `trail.points` を読むと説明するが、現在は読まない（`MagicCircleDemoscene.kt:3-14`）。
- `FrameClock` は回転層が `phase()` または `rotation()` を読むと説明するが、実使用は `seconds()` である（`FrameClock.kt:4-10`、`Scenes.kt:149`）。
- `FrameClock.elapsed()` は前回呼び出し以降の値と説明するが、起動以来の秒数を返す（`FrameClock.kt:53-57`）。
- `PerlinNoise.fbm()` は周波数を半減すると説明するが、実装は周波数を倍化し振幅を半減する（`PerlinNoise.kt:63-82`）。
- `Wave` は周期単位の位相として計算する説明と、`TAU` でラップする実装が混在する（`Wave.kt:20-35,53-58`）。
- `EngineThemes.getColors()` は取得名だが、実際にはグローバルパレットを変更する（`EngineThemes.kt:115-123`）。

コメントだけを実装へ合わせるべきか、未接続機能を完成させるべきかは個別判断になる。少なくとも、現在採用している経路を説明から識別できる状態が望ましい。

## 9. 判断を保留した事項

### 9.1 DPI導出

対象ディレクトリには、プラットフォームDPIの受理・棄却、scale factor導出、`MIN_SAFE_LOGICAL_WIDTH` / `MAX_SAFE_LOGICAL_WIDTH` 相当の処理がない。渡された論理幅・高を利用する側だけである。したがって、DPI検証の正否は本監査では判定できない。

### 9.2 `playX <= 0` 分岐

変数名は `isPortrait` だが、物理的な縦横判定ではなく、左HUDを使用していないことを表す。端末向き依存のレイアウトとは判定しなかった。

### 9.3 `U / 8`

カーソル幅、ボタン内枠、バー内部の隙間、タイムライン内部のブロック間隔などはマイクロディテール用途である。すべてをマクロレイアウトの不整合として扱っていない。

### 9.4 音声試聴ボタン

Settingsの `testFocusLabel` / `testRelaxLabel` は `previewSound()` へ接続された製品機能である（`Scenes.kt:2295-2306,2456-2460`）。名称に「test」が含まれるだけで、死んだテストコードとは分類していない。

### 9.5 プラットフォーム境界

`PlatformAlarmScheduler`、`PlatformInputTrigger`、`getEpochMillis()` は共通側にOS型を持ち込まず、要求または時刻取得だけを公開している。対象範囲だけでは、薄いプラットフォーム端末という境界を崩しているとは判断しなかった。

## 10. 整理を検討する順序

これは実装計画ではなく、監査結果を安全に判断するための順序である。

1. 魔法陣の各効果について「状態所有者・更新元・描画元・設定OFF時の挙動」を一覧化し、現行採用経路と将来予約を区別する。
2. 常設ログ、現役の防御ログ、到達しないデバッグ状態を分類する。特にSceneManagerの切替追跡ログと未設定フラグは分離して考える。
3. 未使用描画API、旧中央表示ヘルパー、MemoryPrimitives、HUD旧アクションを「外部互換・将来予約・撤去候補」に分類する。
4. HUD、Template、Entropy、Active Timerについて、描画と当たり判定が共有できる最小の幾何計算単位を決める。汎用UI層の導入を前提にしない。
5. Settingsの行順を描画・測定・入力の一か所から追える形にできるか検討し、まず `visualsHeader` の二重描画意図を確認する。
6. `U`、グリフ形式16、HUD比率、logicalHeight復元、densityの所有契約を明記する。値の変更より先に、どこが正本かを決める。 **user note「Uが神」** the screen size or aspect ratio is never to never be assumed, that is why the canonical unit U is a thing.
7. アイコンのARGB風中間値、円・線のキャンバス委譲、aliased共通ラスタ化について、意図した使い分けを定義する。
8. 無割り当てが必要な経路を明確にし、Active Timer描画の `Pair` / 空配列候補と、再接続前のFABRIK実装をその範囲に照らして確認する。

## 11. 結論

現行システムは、独自IMGUI、手続き描画、パレット番号、共通エンジン主導という大枠を保っている。主な課題は、その大枠から外れた別方式が全面的に存在することよりも、同じ大枠の中で複数世代の入口・状態・説明が併存していることである。

特に魔法陣、HUD、Settings、描画プリミティブは、実際に動いている経路と未接続の経路を区別するだけでも理解負担を大きく減らせる。共通化を進める場合も、既存の見た目と無割り当て特性を保持できる小さな幾何・契約単位から検討するのが安全である。
