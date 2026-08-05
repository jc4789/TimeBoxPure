# Scope
`Scenes.kt` 全 3262 行と、そこへ論理寸法・入力・HUD・描画を渡す直接経路。

UI として判定するのは、ユーザー指定どおり次だけ：ボタン、メニュー、テキストフィールド、HUD レイアウト、サイドバー、タッチ当たり判定、縦積み、レスポンシブ行、グリフ描画、IMGUI レイアウトカーソルコード。

主な全文確認ファイル：

- `Scenes.kt`
- `SceneManager.kt`
- `RetroHudComponent.kt`
- `EngineCanvas.kt`
- `ScaledProceduralRenderer.kt`
- `EngineCursorRenderer.kt`
- `FixedInputContainer.kt`
- `ProceduralUiPrimitives.kt`
- `NestedTimeboxInstrumentRenderer.kt`
- `Pc98SurfaceView.kt`
- `AndroidEngineCanvas.kt`
- `MainActivity.kt`
- `BlockOverlayActivity.kt`
- `AndroidHeadlessInputBridge.kt`
- `TouchColliderManager.kt`
- `InputPrimitives.kt`
- `PlatformInputTrigger.kt`
- `TimerActions.kt`
- `SessionMacroDisplay.kt`
- `EngineThemes.kt`
- `VisualsStateHolder.kt`

# Confirmed

## 寸法・入力経路

- Android の物理表示寸法は `Pc98SurfaceView.surfaceChanged` で論理寸法へ変換される（`Pc98SurfaceView.kt:62-75`）。
- 同じ整数 `currentScaleFactor` を描画の `Canvas.scale` とタッチ座標の逆変換に使う（`Pc98SurfaceView.kt:114-120`, `248-284`）。
- `SceneManager.render` とタッチ配送は、いずれも `RetroHudComponent` から同じ `playX/playW/playH` を得る（`SceneManager.kt:148-166`, `207-220`, `247-265`）。
- `TouchColliderManager` は渡された矩形・円を判定するだけで、独自レイアウトを持たない（`TouchColliderManager.kt:3-21`）。
- `Scenes.kt` は `commonMain` にあり、Android 型を参照しない。

## Scenes.kt の U 修正対象

以下は、指定された UI 種別の位置・寸法・当たり判定・レイアウトカーソルへ直接入る数値である。編集時は値と式の流れを変えず、新規定数も作らず、同じ値をインラインの U 算術で表す必要がある。

### ACTIVE TIMER SCENE

- テキストフィールドの基準 Y を決める `logicalHeight / 12f`（`timerInputY`, `Scenes.kt:600-602`）。
- 鳴動画面のタイトル・下側マーキーのグリフ Y 比率 `0.15f`, `0.68f`（`Scenes.kt:343-350`, `377-384`）。
- DISMISS ボタンの幅・Y 比率 `0.3f`, `0.78f`（`Scenes.kt:389-403`）。
- 描画側と当たり判定側は `timerInputY`、コントロール幅・位置、ボタン行 Y を共有しているため、片側だけの変更は禁止（`Scenes.kt:433-585`）。

### TEMPLATE CUSTOMIZER SCENE

- カードメニューのレスポンシブ高・間隔・安全上端：`3f / 20f`, `3f / 100f`, `0.08f`（`Scenes.kt:959-966`, `1122-1127`, `1192-1200`）。
- カード内グリフ・タイムライン配置：`0.12f`, `0.4f`, `0.65f`, `0.25f`, `0.2f`, `0.6f`, `2f / 5f`, `0.45f`（`Scenes.kt:1000-1023`）。
- FORGE ボタン幅比率 `0.24f`（`Scenes.kt:1043-1052`, `1127-1135`）。
- 同じカード高・間隔・安全上端は描画、スクロール下限、当たり判定の三箇所に複製されているため、三箇所を同じ表記にする必要がある。

### TEMPLATE FORGE SCENE

- UI の安全上端と内容左右余白を決める `SAFE_TOP_RATIO_DEN = 12f` と `CONTENT_PAD_RATIO_DEN = 20f`（`Scenes.kt:1223-1224`, `1327-1330`, `1473-1480`, `1630-1636`）。
- 描画、タッチ、`contentBottomY` の三経路で同じ値を共有しているため、定数の型だけを変えるのではなく、元の 12 と 20 を正確に保つ U 式へ置き換える必要がある。
- `LABEL_COLUMN_RATIO_NUM/DEN = 2f/5f` は、スキルが明示的に許可する名前付きレスポンシブ列比率と同じ形であり、現時点では変更対象としない（`Scenes.kt:1221-1222`, `2049-2054`）。

### SETTINGS SCENE

- `beginSettingsLayout` の UI 余白、安全上端、レスポンシブ行高：`playAreaW / 20f`, `logicalHeight / 12f`, `playAreaH * 3f / 25f`（`Scenes.kt:2379-2395`）。
- この一つの関数が描画、当たり判定、スクロール計測の共通レイアウト状態を作るため、ここだけを正確に直せば三経路が揃う（`Scenes.kt:2139-2157`, `2163-2230`, `2504-2529`）。
- `LABEL_RATIO_NUM/DEN = 2f/5f` は名前付きレスポンシブ列比率であり、変更対象としない（`Scenes.kt:2100-2101`, `2414-2440`）。
- `U / 8` のバー内ギャップは component internals に当たり、許可範囲（`Scenes.kt:2554-2573`）。

### ENTROPY SCENE

- ユーザーが指定した `INPUT_HEIGHT_DEN`, `TASK_ROW_HEIGHT_DEN`, `DETONATOR_HEIGHT_DEN` は現在それぞれ 14、14、12を正確に保つ U 式になっている（`Scenes.kt:2599-2601`, 使用箇所 `2698`, `2717-2719`, `2894`, `2917-2919`, `3058-3060`）。
- なお残る UI 係数：左右余白 `/ 24f`、安全上端 `/ 12f`、追加ボタン幅 `/ 4f`、ページ切替ボタン `/ 18f`（描画 `Scenes.kt:2687-2722`、当たり判定 `2888-2923`）。
- 指示ポップアップのメニュー・グリフ・ボタン係数：`0.1f`, `0.8f`, `0.2f`, `0.55f`, `0.12f`, `0.4f`, `0.75f`, `0.18f`（描画 `Scenes.kt:2798-2824`、当たり判定 `2854-2875`）。描画と当たり判定を必ず対で直す必要がある。

### BLOCK OVERLAY SCENE

- タイトルとサブタイトルのグリフ Y 比率 `0.3f`, `0.45f`（`Scenes.kt:3159-3164`）。
- RETURN ボタンの幅・高さ・Y 比率 `0.4f`, `0.1f`, `0.7f`（描画 `Scenes.kt:3166-3171`、当たり判定 `3180-3187`）。
- ボタン最小幅 200 と最小高 32 は、現在すでに元値を保つ U 式である。

## DPI SCALE CHECK

- Platform DPI: `displayMetrics.density`（`Pc98SurfaceView.kt:67`）。
- DPI accepted/rejected: finite かつ `0.1 < density < 10` を受理するため、スキルが拒否対象に挙げる `1` も現在は受理する（`Pc98SurfaceView.kt:396-405`）。
- Fallback used: 無効値は `1.0f` へ置換される。
- Scale: `floor(density * 2.0)` を開始点に、名前付き論理幅 320～1200へ収める（`Pc98SurfaceView.kt:367-416`）。
- logicalWidth/logicalHeight: 物理幅・高を最終整数 scale で割る。
- Result: `dpi-scale-derive` の厳密条件では FAIL。現在の Scenes.kt 修正範囲外なので変更しない。

## PLATFORM FIREWALL CHECK

- Allowed responsibility: Surface、Canvas 提示、入力転送、時刻差分、OS 密度、ハプティクス、キーボード橋渡しは Android 側にある。
- Core responsibility preserved: シーン本体、UI 配置、当たり判定、パレット意味は主に `commonMain` にある。
- Leakage found: Android の `Pc98SurfaceView` が scale 方針とスキャンライン描画方針を持つ（`Pc98SurfaceView.kt:248-297`, `367-416`）。`MainActivity` と `BlockOverlayActivity` が開始シーン／遷移を直接選ぶ（`MainActivity.kt:80-82`, `BlockOverlayActivity.kt:50-63`）。
- Result: 厳密な `platform-firewall-port` では FAIL。現在の Scenes.kt 修正範囲外なので変更しない。

# Rejected

- `TIMER_RADIUS_WIDTH_NUM/DEN` と `TIMER_RADIUS_HEIGHT_NUM/DEN` はタイマー円の装飾半径であり、ユーザーが列挙した UI 要素ではない。`9f/20f` の型だけを変えない（`Scenes.kt:56-59`, `594-598`）。
- `logicalWidth`, `logicalHeight`, `playX`, `playY`, `playW`, `playH` 自体は表示事実／プレイ領域であり、この作業では変更しない。
- `cachedLogicalWidth = 640f`, `cachedLogicalHeight = 400f` は表示寸法キャッシュの初期値であり、UI 要素寸法として U 化しない。
- 時間、タイマー秒数、進捗、音量、振動強度、ランダム処理、回転角、アニメーション周期・速度、Perlin 値は非 UI 値として触らない。
- 魔法陣、タイマー円、アラーム装飾円、弾幕／ビーズ／目盛りなどの手続き描画形状を、この UI 修正へ含めない。
- `inputW - TASK_INPUT_INNER_PAD * 2f` のような左右二辺を表す演算係数は、その型だけを変えない。これは U 化ではなく、ユーザーが禁止した単純な `f` 除去になる。
- 既存の U 表記を正解の根拠にしない。用途と元の数値を個別に確認する。

# Unknown

- Settings のバー内部にある `maxOf(1f, ...)` は UI component internal だが、1 は `U / 16` になる。スキルが明示許可する最小 micro-detail は `U / 8` であり、元値 1 の維持と両立する表記が明示されていない（`Scenes.kt:2564`）。変更前にユーザー確認が必要。
- Active Timer の鳴動画面で、サブタイトル位置が装飾円の `ornamentCy` に積まれている（`Scenes.kt:354-373`）。`ornamentCy` の `0.28f` は装飾形状とグリフ配置の両方へ影響するため、非 UI を触らない条件の下では変更前に確認が必要。
- iOS／Win32 の列挙済みソースセットには `PlatformTime.kt` しかなく、Scene host／EngineCanvas 実装は確認できない。

# Recommendation

- ソース変更はまだ行わない。
- 実装する場合はユーザー指定どおり一シーンずつ、ACTIVE TIMER から BLOCK OVERLAY まで順番に進める。
- 各値は元の数値を正確に維持する U 算術へ置換し、単純な `f`／`.toFloat()` 除去はしない。
- 新規 `private const val`、新規関数、命名変更、レイアウト方式変更、ロジック書換えは行わない。
- 描画、当たり判定、スクロール境界／IMGUI カーソルに同じ式が複製されている箇所は、同じシーン内で同時に同じ表記へ直す。
- 最初の編集前に、`maxOf(1f, ...)` と `ornamentCy * 0.28f` の二点だけユーザー判断を確認する。
