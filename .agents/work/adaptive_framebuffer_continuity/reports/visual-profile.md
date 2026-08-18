# Scope

2枚のWindows画像についてPNG寸法、推定client寸法、UIの実pixel寸法を比較し、現行 `PrimitiveDisplayProfile` の整数 `reductionDivisor` が約2倍の見た目差を説明するか監査した。

制約は次の通り。

- primitive framebuffer導出のauthorityはcommonMainに維持する。
- `U`、scene、HUD、layout codeは変更対象にしない。
- `MAX_PRIMITIVE_PIXELS = 1 shl 20` を超えない。
- production codeは変更しない。

参照した実装：

- `shared-engine/src/commonMain/kotlin/com/example/timeboxvibe/engine/core/PrimitiveDisplayProfile.kt:3-27`
- `app/src/main/java/com/example/timeboxvibe/ui/main/Pc98SurfaceView.kt:51-60`
- `shared-engine/src/winMain/kotlin/com/example/timeboxvibe/engine/win/Win32Host.kt:150-183`
- `shared-engine/src/commonMain/kotlin/com/example/timeboxvibe/engine/core/PresentationTransform.kt:28-68`

# Confirmed

## 1. 画像寸法とclient寸法

PNG IHDRの実寸法：

| 画像 | PNG寸法 |
|---|---:|
| image-1.png | 859 x 1262 |
| image-2.png | 866 x 1261 |

window frame/title barと、連続するapp background色 `(34,34,34)` のpixel runから得られるclient領域：

| 画像 | client領域 | client pixel数 | budgetとの差 |
|---|---:|---:|---:|
| image-1 | 855 x 1226 | 1,048,230 | budgetより346少ない |
| image-2 | 857 x 1225 | 1,049,825 | budgetより1,249多い |

image-1のclientは概ね `x=1..855, y=32..1257`、image-2は `x=4..860, y=34..1258`。window chromeを含むPNG寸法そのものではなく、このclient寸法がWin32の `GetClientRect` からprofileへ入る値である（`Win32Host.kt:340-348`）。

## 2. 現行profileの出力

現行式は次の最小整数divisorを選ぶ。

```text
ceil(width / divisor) * ceil(height / divisor) <= 1,048,576
```

根拠：`PrimitiveDisplayProfile.kt:17-22`。

画像寸法へ適用すると：

| 画像 | divisor | primitive寸法 | primitive pixels | clientへのpresent倍率 |
|---|---:|---:|---:|---:|
| image-1 | 1 | 855 x 1226 | 1,048,230 | 1.000x |
| image-2 | 2 | 429 x 613 | 262,977 | 横1.998x、縦1.997x |

image-2はbudgetを僅か0.119%超えただけで、primitive budget使用量が約25.08%まで落ちる。`divisor=1 -> 2` は連続的な縮小ではなく、primitiveの各軸をほぼ半分にするstepである。

`U`とglyph/icon geometryはprimitive座標のままなので、presentation後の物理寸法は同じstepでほぼ2倍になる。platform wrapperが独自scaleを選んでいるのではない。Android/Win32はcommonMain profileの返値をcanvas logical boundsとPresentationTransformへそのまま渡している（`Pc98SurfaceView.kt:55-60`、`Win32Host.kt:166-183`）。

## 3. 画像上の約2倍差

palette色 `(102,136,136)` の上部入力欄をpixel測定した。

| 対象 | image-1 | image-2 | 比率 |
|---|---:|---:|---:|
| 上部入力欄の塗り高さ | 36 px (`y=134..169`) | 72 px (`y=136..207`) | 2.000x |
| 右badgeの塗り領域 | 36 x 36 px | 72 x 72 px | 各軸2.000x |
| 中央赤diskの主要連結領域 | 約97 x 97 px | 約195 x 204 px | 約2x |

一方、play area比率から作られる外周円の物理横幅はimage-1で約768px（`x=45..812`）、image-2で約771px（`x=48..818`）であり、ほぼ同じである。これは比例geometryがprimitiveで半分になった後、presentで約2倍へ戻るためである。

したがって見た目は次の混合状態になる。

- `U`由来のtext、icon、border、固定cell padding: image-2で約2倍。
- client/play-area比率由来の大きな円・panel: 物理占有率は概ね不変。
- 固定cell paddingが物理的に倍になるため、image-2の入力欄は横幅も `771px -> 689px` と狭くなる。

これは2枚の画像差と完全に整合する。現行の `1 -> 2` divisor境界は、約2倍のUI差を説明する。

## 4. 境界の鋭さ

高さ1225pxの場合：

```text
855 * 1225 = 1,047,375  -> divisor 1
856 * 1225 = 1,048,600  -> budgetを24 pixelsだけ超える -> divisor 2
```

client幅が1px増えただけで：

```text
855 x 1225 -> 855 x 1225 primitive
856 x 1225 -> 428 x 613 primitive
```

`U=16` の物理表示幅も約16pxから約32pxへstepする。この境界は画像で観察された差よりさらに極端な1px境界例である。

## 5. color / authority / hot path

COLOR LAW CHECK:

- Core color representation: palette index 0..15のindexed framebufferのまま。
- Native color conversion location: Android/Win32 presenterのまま。
- Palette cache: 変更不要。
- Platform leakage: なし。
- Result: PASS。

PLATFORM FIREWALL CHECK:

- Platform: client/surface寸法をcommonMain profileへ渡すだけ。
- Core responsibility preserved: primitive寸法導出、scene、HUD、layoutはcommonMain。
- Leakage found: なし。
- Result: PASS。

HOT LOOP AUDIT:

- 対象導出はsurface/client resize時だけで、render/raster loop内ではない。
- 提案する整数演算、整数sqrt、while loopはいずれもallocation-freeにできる。
- Result: PASS。

# Rejected

## 現行の整数divisorを維持する

budgetは守るが、境界直後に両軸を半分にし、使用pixel数を約25%へ落とす。実画像で36pxから72pxへの不連続が確認されたため、continuity要件に反する。

## platformごとの補正値を追加する

画像差の原因はcommonMain `reductionDivisor` で完全に説明できる。Win32/Android側へscale、DPI、density、例外的な閾値を追加する必要はなく、authority境界にも反する。

## U、scene、HUDを境界対応させる

問題は同じlogical geometryを約2倍でpresentするprofileのstepである。各UI geometryへ逆補正を入れると原因を残したまま二重scaleになる。今回の制約にも反する。

# Unknown

- 画像取得時の正確な `GetClientRect` logは添付されていない。ただしpixelから推定した `855x1226` と `857x1225` は、budgetの両側に位置し、観測された正確な2倍の入力欄高さと現行式の出力を同時に説明する。単なる見た目推測より強い一致である。
- 連続導出へ変えた後の全sceneのindexed framebuffer hashはまだ存在しない。この監査はread-onlyであり、期待hashを捏造しない。

# Recommendation

## 案1（推奨）: Q16 uniform factor + integer sqrt

client面積がbudget内なら1:1を維持する。超えた場合だけ、面積比の平方根をQ16固定小数で求め、width/heightの両方へ同じ係数を掛ける。

```text
area = width * height                       // Long

if area <= MAX_PRIMITIVE_PIXELS:
    primitiveWidth  = width
    primitiveHeight = height
else:
    ratioQ32 = (MAX_PRIMITIVE_PIXELS << 32) / area
    factorQ16 = integerSqrt(ratioQ32)
    primitiveWidth  = max(1, (width  * factorQ16) >> 16)
    primitiveHeight = max(1, (height * factorQ16) >> 16)
```

`integerSqrt` はLongとwhile loopだけのbinary/restoring sqrtにできる。heap allocation、Float、platform APIは不要。floorされた同一係数を使うため、積はbudgetを超えず、client比率も保つ。

数値例：

| client | 現行 | 案1 | 案1 pixels |
|---|---:|---:|---:|
| 855 x 1226 | 855 x 1226 | 855 x 1226 | 1,048,230 |
| 856 x 1225 | 428 x 613 | 855 x 1224 | 1,046,520 |
| 857 x 1225 | 429 x 613 | 856 x 1224 | 1,047,744 |
| 2560 x 1368 | 1280 x 684 | 1400 x 748 | 1,047,200 |
| 1440 x 3120 | 480 x 1040 | 695 x 1507 | 1,047,365 |
| 3840 x 2160 | 1280 x 720 | 1365 x 767 | 1,046,955 |

画像境界では `U=16` の物理表示が約16pxから約16.02pxになる。現行の約32pxへのjumpは消える。

実装範囲は `PrimitiveDisplayProfile.reductionDivisor` の置換だけで足りる。public API、platform call、`U`、scene、HUD、present/input pathを変える必要はない。

## 案2: aspect-directed budget rectangle

integer sqrtで短辺を先に求め、長辺は残budgetから求める。案1よりbudgetを僅かに多く使える場合がある。

```text
if area <= budget:
    return width,height

if width >= height:
    primitiveHeight = integerSqrt(budget * height / width)
    primitiveWidth  = min(width, budget / primitiveHeight)
else:
    primitiveWidth  = integerSqrt(budget * width / height)
    primitiveHeight = min(height, budget / primitiveWidth)
```

数値例：

| client | 案2 | pixels | client比率からの誤差 |
|---|---:|---:|---:|
| 856 x 1225 | 855 x 1225 | 1,047,375 | -0.117% |
| 857 x 1225 | 856 x 1224 | 1,047,744 | -0.035% |
| 2560 x 1368 | 1401 x 748 | 1,047,948 | +0.088% |
| 1440 x 3120 | 695 x 1508 | 1,048,060 | -0.144% |
| 3840 x 2160 | 1365 x 768 | 1,048,320 | -0.024% |

案2もstep-halvingを除去しbudgetを守るが、width/heightへ完全に同一係数を掛けないため、最大でも小さいとはいえaspect誤差が案1より大きい。`PresentationTransform` がaspectを保持する結果、端に1pixel程度のunused outputが出る場合がある。

よって推奨は案1。案2はbudget使用率を最優先する場合の代替に留める。

## 最小verification gate

実装時に追加・更新すべき直接証拠は以下だけでよい。

1. `855x1226 -> 855x1226`。
2. 境界 `856x1225` が約半分へ落ちず、面積がbudget以下。
3. `857x1225` と `2560x1368` の縦横比誤差がinteger rounding範囲。
4. Android/Win32が同じprofile結果をpresentationとinput inverse mappingへ使用する既存経路を維持。
5. indexed framebuffer内の全pixelが0..15である既存color契約を維持。

scene screenshotの新しいgoldenを先に固定するべきではない。profileの直接数値testと、実装後の既存scene framebuffer比較を分ける。
