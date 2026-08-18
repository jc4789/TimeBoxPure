# Scope

現行の `PrimitiveDisplayProfile.kt`、`PresentationTransform.kt`、および対応する既存 `commonTest` を読み取り、integer-divisor cliff を比例的かつ pixel-budget 内の primitive framebuffer 解像度へ置き換えるための最小決定論テストを設計した。

対象は既存の次の2 test fileだけとする。

- `shared-engine/src/commonTest/kotlin/com/example/timeboxvibe/engine/core/PrimitiveDisplayProfileTest.kt`
- `shared-engine/src/commonTest/kotlin/com/example/timeboxvibe/engine/core/PresentationTransformTest.kt`

新しい test file、診断 class、screenshot、golden image、production test hook は不要。production code は変更していない。

# Confirmed

## 現在の cliff

`PrimitiveDisplayProfile` は `ceilDiv(width, divisor) * ceilDiv(height, divisor) <= 1 shl 20` になるまで整数 divisor を1ずつ増やす。

- budget: `1,048,576`
- `856 * 1224 = 1,047,744` なので現行結果は `856 x 1224`
- `857 * 1224 = 1,048,968` なので幅を1増やしただけで divisor が2になり、現行結果は `429 x 612`

したがって screenshot で見えた不連続は、現行 `PrimitiveDisplayProfile.kt:17-22` の整数 divisor 境界だけで決定論的に再現できる。

同じ cliff は次の divisor 境界にもある。

- `2048 x 2048` は divisor 2 で `1024 x 1024`
- `2049 x 2048` は divisor 2 では budget を超えるため divisor 3へ落ちる

## 現在のテスト範囲

`PrimitiveDisplayProfileTest` は現在3件だけである。

- budget 内の `800 x 600` が1:1
- `2560 x 1368` が現行 divisor 2の `1280 x 684`
- `1440 x 3120` の面積が budget 以下

これらは隣接する surface 寸法間の連続性、aspect drift、no-upscale、invalid input を検証しない。特に `1280 x 684` の exact assertion は比例導出後の正しい結果を旧 divisor 2へ固定してしまうので置換が必要。

`PresentationTransformTest` は現在3件で、full-client viewport、最初/最後の terminal pixel、viewport 外 rejection を検証している。これは基本 contract として残せるが、比例導出した非整数倍率の source dimensions、degenerate dimensions、全 edge の単調性は未検証。

`PresentationTransform.configure` は source/output の0以下を1へ正規化し、入力を次式で逆変換する。

```text
primitive = floor(terminal * source / output)
```

right/bottom は exclusive、terminal の最終有効 pixel は source の最終 pixel へ写る。

# Minimal deterministic tests

## `PrimitiveDisplayProfileTest` に残す1件

### 1. `clientWithinBudgetRemainsOneToOne`

既存 `800 x 600` testを一般化し、少なくとも次を table loop で確認する。

```text
1 x 1
800 x 600
856 x 1224
1024 x 1024
```

各 case で `primitiveWidth == outputWidth`、`primitiveHeight == outputHeight`。これは budget 内での no-upscale と不要な downscale がないことを同時に固定する。

## `PrimitiveDisplayProfileTest` に置換・追加する4件

### 2. `adjacentSurfaceSizesDoNotCrossAResolutionCliff`

次の隣接ペアを同じ test 内で比較する。

```text
856 x 1224  -> 857 x 1224
1224 x 856  -> 1224 x 857
2048 x 2048 -> 2049 x 2048
2048 x 2048 -> 2048 x 2049
```

各ペアで次を assertion にする。

```text
abs(afterPrimitiveWidth  - beforePrimitiveWidth)  <= 1
abs(afterPrimitiveHeight - beforePrimitiveHeight) <= 1
```

最初のペアは実際の screenshot cliff を直接固定し、portrait/landscape の転置と次の divisor 境界も同じ規則で守る。exact derived dimension は固定しないため、正しい比例丸めの実装選択を不必要に縛らない。

### 3. `derivedAreaStaysWithinBudgetAndUsesItProportionally`

table:

```text
857 x 1224
1224 x 857
2049 x 2048
2560 x 1368
1440 x 3120
7680 x 4320
Int.MAX_VALUE x Int.MAX_VALUE
```

各 case で `Long` を使って次を確認する。

```text
primitiveWidth >= 1
primitiveHeight >= 1
primitiveWidth.toLong() * primitiveHeight <= MAX_PRIMITIVE_PIXELS
```

通常寸法の over-budget case（`Int.MAX_VALUE` caseを除く）では、退化した `1 x 1` 実装でも通らないよう、丸め損失の上限も確認する。

```text
MAX_PRIMITIVE_PIXELS - primitiveArea <= primitiveWidth + primitiveHeight
```

この上限は共通 scale 後に各軸を最大1 pixel丸める比例導出に対応する。実装が別の明示的な最大利用 contract を採る場合は、同等以上に強い整数 boundへ置換してよい。

### 4. `derivedDimensionsPreserveAspectWithinIntegerRounding`

上記の通常寸法 caseについて、浮動小数点比ではなく `Long` の cross product で判定する。

```text
drift = abs(primitiveWidth * outputHeight - primitiveHeight * outputWidth)
drift <= outputWidth + outputHeight
```

これは両軸それぞれ最大1 primitive pixelの整数丸めを許容しつつ、縦横比を大きく歪める結果を拒否する。portrait/landscape を同じ test tableへ入れる。`Int.MAX_VALUE` 同士の積も正の `Int` 最大値同士なら `Long` 内だが、差の計算は各 product を `Long` にしてから行う。右辺も `outputWidth.toLong() + outputHeight.toLong()` とし、`Int` 加算を先に行って overflow させない。

### 5. `neverUpscalesAndNormalizesInvalidInputs`

positive case:

```text
1 x 1
320 x 200
857 x 1224
2560 x 1368
1 x Int.MAX_VALUE
Int.MAX_VALUE x 1
```

各 case で次を確認する。

```text
1 <= primitiveWidth  <= outputWidth
1 <= primitiveHeight <= outputHeight
```

invalid caseは現行と同じ軸ごとの `coerceAtLeast(1)` をexact resultとして固定する。

```text
(0, 0)                         -> 1 x 1
(-1, 480)                      -> 1 x 480
(640, -1)                      -> 640 x 1
(Int.MIN_VALUE, Int.MIN_VALUE) -> 1 x 1
```

これにより divide-by-zero、sqrt/ratio の非有限値、負の framebuffer dimension を拒否しつつ、有効な反対軸を勝手に失わない。

# `PresentationTransformTest` の最小追加

既存3件は維持し、次の2件だけを追加する。

### 6. `mapsEdgesForProportionallyDerivedSource`

`857 x 1224` から `PrimitiveDisplayProfile` で source dimensions を得て、その値で `PresentationTransform.configure` する。exact source dimensionをtestに複製しない。

確認事項:

```text
primitiveX(0) == 0
primitiveY(0) == 0
primitiveX(outputWidth - 1) == sourceWidth - 1
primitiveY(outputHeight - 1) == sourceHeight - 1
primitiveX(-1) == OUTSIDE
primitiveY(-1) == OUTSIDE
primitiveX(outputWidth) == OUTSIDE
primitiveY(outputHeight) == OUTSIDE
containsTerminalPoint(0, 0) == true
containsTerminalPoint(outputWidth - 1, outputHeight - 1) == true
containsTerminalPoint(outputWidth, outputHeight - 1) == false
containsTerminalPoint(outputWidth - 1, outputHeight) == false
```

さらに全 terminal x/yを単純な test loop で走査し、mapped coordinate が範囲内かつ単調非減少であることを確認する。source は no-upscale contract により output 以下なので、隣接 terminal pixel間の増分は `0..1` でなければならない。

これは新しい診断基盤ではなく、既存 test 内の局所 loopだけである。

### 7. `degenerateConfigurationHasStableOnePixelMapping`

```text
configure(0, -10, 0, -20)
```

期待値:

```text
sourceWidth/sourceHeight/outputWidth/outputHeight == 1
viewport == (0, 0, 1, 1)
primitiveX(0) == 0
primitiveY(0) == 0
primitiveX(-1) and primitiveX(1) == OUTSIDE
primitiveY(-1) and primitiveY(1) == OUTSIDE
```

# Rejected

- screenshot/golden-image比較。問題は整数 dimension derivationだけで完全に再現できる。
- 全 window size の exhaustive scan。必要な cliff 境界、転置、extreme、invalidを選んだ table testで十分。
- platform test。authority は commonMain の `PrimitiveDisplayProfile` と `PresentationTransform` にあり、Android/Win32固有テストを増やす必要はない。
- exactな `2560 x 1368 -> 1280 x 684` の維持。これは廃止対象の divisor 2を仕様化してしまう。
- floating-point epsilonだけの aspect test。cross product の整数判定の方がKotlin/JVMとKotlin/Nativeで決定論的。
- test用 profile implementation、diagnostic logger、resize recorder、PNG artifact。

# Recommendation

最小変更は既存2 test file内の合計7 test responsibilityである。

- `PrimitiveDisplayProfileTest`: 既存3件を整理して5件
- `PresentationTransformTest`: 既存3件 + 2件
- 新規 test file数: 0
- 新規 test dependency: 0
- 新規 production hook: 0

実装後の最小実行 gate:

```powershell
$env:JAVA_HOME="D:\Programes\Android Studio\jbr"; .\gradlew.bat :shared-engine:testDebugUnitTest
```

同じ整数演算が Kotlin/Native でも一致する最終 gate:

```powershell
$env:JAVA_HOME="D:\Programes\Android Studio\jbr"; .\gradlew.bat :shared-engine:winTest
```

合否の核心は次の5条件である。

1. budget直前/直後の隣接 surface で primitive各軸の差が最大1。
2. primitive areaが常に `<= MAX_PRIMITIVE_PIXELS`。
3. aspect cross-product driftが整数丸め範囲内。
4. positive inputをupscaleせず、0/negative inputは軸ごとに安定して最低1へ正規化。
5. full-clientの最初/最後/exclusive外側が同じ transformで正しく入力変換される。
