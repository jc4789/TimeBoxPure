# UI倍率境界修正計画

## 1. 修正目的

この修正の目的は、現在混ざっている次の二つの倍率を分離することである。

1. **UI内倍率**
   - UI座標系の中で、文字・枠・余白・行高・当たり判定の関係を決める倍率。
   - 正本は `U = 16`。
   - 通常文字の既定倍率は `1`。
   - 意味上必要な拡大だけを呼び出し側が明示する。

2. **表示倍率**
   - 完成したUI全体を表示装置へ提示する倍率。
   - 文字だけでなく、枠、余白、HUD、ボタン、当たり判定を同じ倍率で扱う。
   - 描画へ一度だけ掛け、入力座標には同じ倍率の逆変換を一度だけ掛ける。

文字だけをDPI、解像度、空き幅、言語によって拡大・縮小してはならない。反対に、表示倍率そのものを廃止して全画面を常に倍率 `1` に固定してもならない。

---

## 2. 正本となる式

```kotlin
const val U: Int = 16
const val DEFAULT_TEXT_SCALE: Int = 1
```

各寸法は次の順序で決まる。

```text
UI論理寸法 = U × 意味上のUI内倍率
最終表示寸法 = UI論理寸法 × 表示倍率
UI入力座標 = 物理入力座標 ÷ 表示倍率
```

通常文字の場合は次のとおりである。

```text
字形の元寸法       = 16×16
既定UI内文字倍率   = 1
UI論理寸法         = 16×16
表示倍率1の最終寸法 = 16×16
表示倍率2の最終寸法 = 32×32
```

表示倍率が `2` なら、文字だけでなく同じUIに属する枠、余白、行高、アイコン、当たり判定も同率で `2` 倍になる。これが「UIに対する文字の大きさ」を維持する条件である。

### 受入条件

- 16×16字形を、UI内倍率 `1`・表示倍率 `1` で16×16として表示できる。
- 通常文字は、全シーンでUI内倍率 `1` を既定値にする。
- DPI、解像度、空き幅、言語は、文字だけのUI内倍率を変更しない。
- 表示倍率を上げた場合、UI全体が同率で変化する。
- 表示倍率変更後も、描画位置と入力位置が一致する。
- 画面比率は領域配分には使えるが、個々の部品を別々に拡大する根拠には使わない。

---

## 3. 現在の破綻点

### 3.1 表示倍率が過大になり得る

現在の `DisplayScalePolicy` は、信頼済み密度から概ね次の値を開始点としている。

```kotlin
(platformDensity * 2f).toInt()
```

これは密度情報だけで表示倍率を強く引き上げる。通常文字のUI内倍率が `1` でも、表示倍率が `3` なら最終字形は48×48になる。

問題は表示倍率の存在ではない。問題は、倍率 `1` を基準にせず、密度をUIの強制拡大率として扱っていることである。

### 3.2 文字だけの自動倍率がある

`headingScale(text, maxWidth)` は、空き幅へ収まるかどうかによって文字倍率を `2` または `1` に変える。

```kotlin
private fun headingScale(text: CharSequence, maxWidth: Float): Int =
    if (ProceduralTextRenderer.measureWidth(text, HEADER_TEXT_SCALE) <= maxWidth) {
        HEADER_TEXT_SCALE
    } else {
        TEXT_SCALE_IDENTITY
    }
```

これは表示倍率ではなく、文字だけのUI内倍率を画面条件から自動変更している。同じ役割の文字でも、画面、文言、言語によって文字と周辺UIの比率が変わる。

### 3.3 UI固有寸法へ画面比率が混ざっている

現在は次の形の式が複数シーンにある。

```kotlin
val rowH = maxOf(playAreaH * 3f / 25f, U * 2f)
```

`U * 2f` はUI固有寸法だが、`playAreaH * 3f / 25f` は画面から直接作られた寸法である。大きい方を選ぶと、その部品だけが画面寸法に応じて別の倍率へ移行する。

一方、次の用途は正当なレスポンシブ配置であり、撤去しない。

- 残り表示領域の算出
- 左右列へ割り当てる幅の算出
- 中央寄せ位置の算出
- 表示できる行数やスクロール範囲の算出
- 左HUDと下HUDの候補領域の比較

修正対象は「画面比率の使用」そのものではなく、画面比率を文字、余白、行高、ボタン内部寸法、アイコンなどの**固有寸法**へ転用している箇所である。

### 3.4 表示層は原因ではない

現在のAndroid表示層は、全体描画へ表示倍率を掛け、入力を同じ倍率で割っている。この対称性は正しい。

```kotlin
canvas.scale(displayScale, displayScale)

val logicalX = physicalX / displayScale
val logicalY = physicalY / displayScale
```

表示層は修正済みの表示倍率を消費するだけに保つ。Android側へUI寸法規則や文字倍率規則を移さない。

---

## 4. 実装方針

## 4.1 表示倍率を「倍率1優先」で導出する

`DisplayScalePolicy` は残す。ただし、密度から表示倍率を直接開始する処理と `PHYSICAL_SCALE_PER_DENSITY = 2f` を撤去する。

表示倍率の選択規則は次のとおりとする。

1. 有効な物理寸法がなければ `1`。
2. 候補は必ず `1` から開始する。
3. DPIは検証済みでも、単独では候補を増やさない。
4. 未拡大の論理幅が名前付きエンジン上限を超える場合だけ、整数倍率を一段ずつ検討する。
5. 次の倍率で最小論理幅または最小論理高を割る場合は増やさない。
6. 信頼済みDPIは倍率の上限にだけ使い、強制拡大には使わない。
7. 非有限値、負数、`0.5` 以下、`8` 以上、`1.0±0.01` の偽密度は棄却する。

名前付き範囲は装置解像度の決め打ちではなく、`U` で表したエンジンの可用領域で定義する。

```kotlin
object DisplayScalePolicy {
    private const val MIN_SCALE = 1
    private const val MIN_LOGICAL_SPAN_CELLS = 20
    private const val MAX_LOGICAL_WIDTH_CELLS = 75

    private const val MIN_TRUSTED_DENSITY = 0.5f
    private const val MAX_TRUSTED_DENSITY = 8f
    private const val FAKE_DENSITY = 1f
    private const val FAKE_DENSITY_EPSILON = 0.01f

    private val minLogicalSpan =
        CANONICAL_UI_UNIT * MIN_LOGICAL_SPAN_CELLS.toFloat()
    private val maxLogicalWidth =
        CANONICAL_UI_UNIT * MAX_LOGICAL_WIDTH_CELLS.toFloat()

    fun deriveScale(
        physicalWidth: Float,
        physicalHeight: Float,
        platformDensity: Float,
    ): Int {
        if (!physicalWidth.isFinite() || !physicalHeight.isFinite() ||
            physicalWidth <= 0f || physicalHeight <= 0f
        ) {
            return MIN_SCALE
        }

        val trustedDensity =
            platformDensity.isFinite() &&
                platformDensity > MIN_TRUSTED_DENSITY &&
                platformDensity < MAX_TRUSTED_DENSITY &&
                kotlin.math.abs(platformDensity - FAKE_DENSITY) >
                    FAKE_DENSITY_EPSILON

        val densityLimit = if (trustedDensity) {
            maxOf(MIN_SCALE, platformDensity.toInt())
        } else {
            Int.MAX_VALUE
        }

        var scale = MIN_SCALE
        while (physicalWidth / scale > maxLogicalWidth) {
            val candidate = scale + 1
            if (candidate > densityLimit ||
                physicalWidth / candidate < minLogicalSpan ||
                physicalHeight / candidate < minLogicalSpan
            ) {
                break
            }
            scale = candidate
        }
        return scale
    }
}
```

この規則では、DPIが高いという理由だけで `1` から `2` や `3` へ上げない。表示倍率 `1` を実際に選べる基準値として保ちつつ、名前付き論理範囲を超える表示装置ではUI全体だけを整数倍する。

`physicalHeight` は固定の縦横比を仮定するためではなく、候補倍率で短い側の可用UIセル数が不足しないことを確認するために使う。

### 表示層の契約

表示層の処理は次の一対を維持する。

```kotlin
val displayScale = DisplayScalePolicy.deriveScale(
    physicalWidth,
    physicalHeight,
    rawPlatformDensity,
)

val logicalWidth = physicalWidth / displayScale
val logicalHeight = physicalHeight / displayScale

canvas.scale(displayScale.toFloat(), displayScale.toFloat())

val logicalPointerX = physicalPointerX / displayScale
val logicalPointerY = physicalPointerY / displayScale
```

- `EngineCanvas.density` は引き続き `1f`。
- 描画器やシーンが生のDPIを読み直さない。
- 表示倍率を個別部品へ再度掛けない。
- 入力側で別の丸め規則を作らない。

---

## 4.2 UI固有寸法を `U` へ戻す

各シーンでは、画面比率と `U` の `maxOf` によって固有寸法を切り替える処理を除去する。

たとえば設定画面の行高が次の場合、

```kotlin
val rowH = maxOf(playAreaH * 3f / 25f, U * 2f)
```

既に存在する `U` 側の設計値を正本にする。

```kotlin
val rowH = U * 2f
```

テンプレートカードも同じ規則で、既存式の `U` 側を固有寸法として残す。

```kotlin
val baseCardH = U * 3.75f
```

ここで行うのはレイアウト再設計ではない。画面寸法から部品ごとの暗黙倍率を作っていた部分だけを外す。

### 置換規則

| 現在の用途 | 修正後 |
|---|---|
| 文字セル寸法 | `U × 明示UI内倍率` |
| 行高 | `U × 既存の意味上の行倍率` |
| 内側余白 | `U × 既存の余白倍率` |
| ボタン内部寸法 | `U` 基準 |
| アイコン寸法 | `U` 基準 |
| 枠線幅 | 現在のUI座標値を維持 |
| 利用可能な本文幅・高 | 実際の論理表示領域から算出 |
| 列幅・中央位置・スクロール容量 | 実際の論理表示領域から算出 |

### HUD

HUDの左配置・下配置に使う候補領域は、実際の論理幅と論理高から計算してよい。ただし、HUD内部の次の寸法は `U` 基準に統一する。

- アイコン
- ボタンの内側余白
- ボタン間隔
- 枠から内容までの余白
- 文字の行高
- 当たり判定の内部部品寸法

候補領域がこれらの `U` 基準部品を収容できない場合、その候補を採用しない。領域比率を使って内部部品そのものを縮小・拡大しない。

描画と入力は、同じHUD座標計算結果を使う。表示倍率は両者の外側で一度だけ処理する。

---

## 4.3 通常文字倍率を全シーンで `1` にする

`ScaledProceduralRenderer.TEXT_SCALE_IDENTITY` を通常文字の唯一の既定値にする。

```kotlin
fun drawText(
    text: CharSequence,
    x: Float,
    y: Float,
    colorIndex: Int,
    textScale: Int = ScaledProceduralRenderer.TEXT_SCALE_IDENTITY,
)
```

見出しなどに意味上の拡大が必要なら、呼び出し側が明示する。

```kotlin
drawText(
    text = title,
    x = titleX,
    y = titleY,
    colorIndex = PaletteIndices.PRIMARY,
    textScale = ScaledProceduralRenderer.HEADER_TEXT_SCALE,
)
```

ただし、次のような自動選択は禁止する。

```kotlin
val textScale = if (measureWidth(text, 2) <= availableWidth) 2 else 1
```

必要なAPIは、空き幅から倍率を決めるAPIではなく、呼び出し側が決めた倍率を測定と描画の両方へ渡すAPIである。

```kotlin
fun measureHeading(
    text: CharSequence,
    maxWidth: Float,
    textScale: Int = ScaledProceduralRenderer.TEXT_SCALE_IDENTITY,
): TextLayout

fun drawHeading(
    renderer: ScaledProceduralRenderer,
    text: CharSequence,
    x: Float,
    y: Float,
    maxWidth: Float,
    colorIndex: Int,
    textScale: Int = ScaledProceduralRenderer.TEXT_SCALE_IDENTITY,
)
```

`maxWidth` は配置可能幅の測定にだけ使い、`textScale` を変える材料にはしない。測定と描画には必ず同じ `textScale` を渡す。

既存の明示的な意味上の倍率は、その役割を確認して維持する。今回の規則は「すべての明示倍率を削除する」ことではなく、「既定値を `1` にし、表示条件による文字だけの自動倍率をなくす」ことである。

---

## 4.4 折り返し・文字積み上げ・クリップを変更しない

この修正は倍率境界の修正であり、折り返しの修正ではない。

- 折り返し規則を追加しない。
- 折り返し規則を倍率調整の代用品にしない。
- 文字積み上げの規則を変更しない。
- クリップ処理を倍率問題の回避策として増やさない。
- 字形データや字形生成器を変更しない。

倍率修正後に残る折り返し、積み上げ、クリップの問題は、別の作業として実測結果から扱う。

---

## 5. ファイル単位の変更範囲

### `EngineCanvas.kt`

- `CANONICAL_UI_UNIT = 16` を維持する。
- `DisplayScalePolicy` を倍率 `1` 優先の規則へ変更する。
- `PHYSICAL_SCALE_PER_DENSITY = 2f` を撤去する。
- DPIを表示倍率の開始値にしない。

### `ScaledProceduralRenderer.kt`

- 通常文字の既定UI内倍率 `1` を維持する。
- DPIや表示寸法から文字倍率を導出しないことを明文化する。
- 測定と描画が同じ明示UI内倍率を使うようにする。
- 表示倍率をここで再適用しない。

### `ProceduralUiPrimitives.kt`

- `headingScale(text, maxWidth)` の自動倍率選択を撤去する。
- 見出し測定と描画へ、同じ明示UI内倍率を渡す。
- 既存の折り返し、文字積み上げ、クリップ規則は変更しない。

### `Scenes.kt`

- 通常文字呼び出しはUI内倍率 `1` を既定とする。
- 明示的な意味上の倍率だけを残す。
- `maxOf(画面比率, U基準値)` で固有寸法を切り替える箇所は、既存の `U` 側設計値へ戻す。
- 画面幅・高は利用可能領域、配置位置、列配分、スクロール容量の計算には引き続き使う。
- 折り返し、文字積み上げ、クリップの動作は変更しない。

### `RetroHudComponent.kt`

- HUD候補領域の計算は論理幅・論理高を使う。
- HUD内部のアイコン、余白、間隔、文字行高、ボタン内部寸法は `U` 基準にする。
- 描画と入力で同じ座標計算を共有する。

### Android表示層

- 原則として構造変更しない。
- 共通側で決まった整数表示倍率をUI全体へ一度だけ適用する。
- 入力へ同じ倍率の逆変換を適用する。
- `EngineCanvas.density = 1f` を維持する。
- Android側にシーン別・文字別の倍率条件を追加しない。

---

## 6. 実装順序

部分的な状態ではUI部品同士の倍率がさらにずれるため、次の順序で実装し、一つの倍率境界修正として完成させる。

1. `DisplayScalePolicy` を倍率 `1` 優先へ変更する。
2. 表示層の全体倍率と入力逆変換が対称なままであることを確認する。
3. `ProceduralUiPrimitives` の文字だけの自動倍率選択を撤去する。
4. 測定と描画へ同じ明示UI内倍率を通す。
5. 各シーンの固有寸法から画面比率による個別倍率を除去する。
6. HUD内部寸法を `U` 基準へ統一し、候補領域の計算とは分離する。
7. 全シーンの通常文字がUI内倍率 `1` であることを確認する。
8. 全体表示倍率と入力座標の一致を確認する。

各段階で新しい文字縮小経路、シーン専用描画経路、別系統のボタン描画を追加しない。

---

## 7. 確認方法

新しい検証基盤は追加しない。既存ビルドと実画面確認を使う。

### 7.1 倍率境界

- 表示倍率 `1` で通常字形が16×16になる。
- 表示倍率 `1` で `U` が16論理画素になる。
- 表示倍率 `2` では文字、枠、余白、行高、アイコン、当たり判定がすべて同率で2倍になる。
- 文字だけ、または枠だけが別倍率にならない。
- 生DPIを変えても、DPIだけを理由に表示倍率が増えない。
- 無効DPIでも倍率導出が有限な整数値を返す。

### 7.2 論理寸法と入力

- `logicalWidth = physicalWidth / displayScale`。
- `logicalHeight = physicalHeight / displayScale`。
- 物理入力座標を表示倍率で割ると、描画に使った論理座標へ戻る。
- 表示倍率を変更しても全ボタンの描画位置と押下範囲が一致する。

### 7.3 全シーン

- 設定画面を基準として通常文字の相対寸法を比較する。
- タイマー、テンプレート、作成、設定、エントロピー、結果、ブロックの通常文字が同じUI内倍率 `1` になる。
- 英語、日本語、繁体字で、言語の違いによって文字倍率が変わらない。
- 行高、余白、枠、ボタンと文字の比率が表示寸法によって変わらない。
- 縦長、横長、正方形でも、領域配分だけが変わり、部品固有寸法は `U` 基準を維持する。

### 7.4 静的確認

次の状態を確認する。

- `headingScale(text, maxWidth)` のような、空き幅から文字倍率を選ぶ処理がない。
- シーンがDPIや物理寸法を直接読んでいない。
- 表示倍率を個別の文字、ボタン、HUDへ掛け直していない。
- 固有寸法に `maxOf(画面比率, U基準値)` が残っていない。
- 画面比率は領域配分と配置計算に限って残っている。

---

## 8. 完了条件

次をすべて満たしたときに完了とする。

1. `U = 16` がUI寸法の唯一の正本である。
2. 通常文字の既定UI内倍率が全シーンで `1` である。
3. 16×16字形を表示倍率 `1` で16×16表示できる。
4. DPI、解像度、空き幅、言語が文字だけの倍率を変更しない。
5. 表示倍率は倍率 `1` から導出され、密度だけでは増えない。
6. 表示倍率は完成したUI全体へ一度だけ掛かる。
7. 入力は同じ表示倍率で一度だけ逆変換される。
8. UI固有寸法は `U` 基準、画面寸法は領域配分と配置に限定される。
9. 折り返し、文字積み上げ、クリップ、字形データへ副次的な変更がない。
10. Android表示層へUI設計判断が流入していない。

この計画の中心は、文字を単独で小さくすることでも、表示倍率を全面廃止することでもない。**UI内倍率と表示倍率の境界を守り、既定16×16文字を含むUI全体を一つの表示倍率で提示すること**である。
