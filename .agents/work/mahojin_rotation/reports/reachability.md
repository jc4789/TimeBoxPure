# Scope

- 読み取り時点: 2026-08-18 の現行 worktree（`FrameClock.kt` は削除済み、`MagicCircleDemoscene` に回転位相が移された途中状態）。
- 対象: `SceneManager -> ActiveTimerScene -> MagicCircleDemoscene -> NestedTimeboxInstrumentRenderer` と、`Wave` / `IkChain2D` / `PerlinNoise` / `VisualsStateHolder` の全到達経路。
- 判定基準: 実際に raster へ到達するか、現在の実装が説明どおり動くか、同じ機能が二重実装されていないか。accepted な半径・中心・各リング配置・中央 readout は変更対象外。platform/UI layout は対象外。

# Confirmed

## 完全な到達経路

1. Scene lifecycle: `SceneManager.performSceneSwitch()` (`SceneManager.kt:82-96`) -> `ActiveTimerScene.onEnter()` (`ActiveTimerScene.kt:34-50`) -> `MagicCircleDemoscene` を一度生成して `reset()`。
2. Update: `SceneManager.update(dt, ...)` (`SceneManager.kt:109-133`) -> `ActiveTimerScene.update(dt)` (`ActiveTimerScene.kt:56-67`) -> `MagicCircleDemoscene.update(dt)` (`MagicCircleDemoscene.kt:95-112`)。
3. Render: `SceneManager.render()` (`SceneManager.kt:145-162`) -> `ActiveTimerScene.render()` (`ActiveTimerScene.kt:70-178`) -> `ScaledProceduralRenderer.nestedTimeboxRenderer`（生成点 `ScaledProceduralRenderer.kt:173`）-> `NestedTimeboxInstrumentRenderer.render()` (`NestedTimeboxInstrumentRenderer.kt:98-380`) -> aliased/palette-index renderer primitives。
4. Demoscene toggle: Settings touch dispatch -> `SettingsScene` の visual row (`SettingsScene.kt:264-279`) -> `VisualsStateHolder.demosceneEffectsEnabled` -> `MagicCircleDemoscene.update/solveTrail/runeDriftAngleOffset` (`MagicCircleDemoscene.kt:105,121,145`)。
5. Nebula toggle: Settings touch dispatch -> `SettingsScene.kt:275-279` -> `VisualsStateHolder.backgroundNebulaEnabled` -> `ActiveTimerScene.nebulaColorIndex()` (`ActiveTimerScene.kt:757-775`) -> `MagicCircleDemoscene.nebulaSample()` (`MagicCircleDemoscene.kt:154-160`) -> `PerlinNoise.fbm()` -> play area 全体を単色で塗る (`ActiveTimerScene.kt:101-120`)。

## Feature classification

### `ActiveTimerScene`

| Feature | 判定 | 根拠 |
|---|---|---|
| accepted viewport/radius/layout を renderer に渡す | **KEEP** | `153-178`; 現在の半径・中心・上下境界を成立させる入口。 |
| renderer が返した graphic bottom と control row の既存関係 | **KEEP** | `179-184`; accepted layout の一部なので無変更。 |
| task input / controls / calendar / alarm overlay | **KEEP（scope 外）** | 魔法陣 graphics の責任ではない。 |
| nullable `demoscene` の生成・reset・update・renderer への受渡し | **DELETE** | `30-32,47-49,64-66,177`; dead state と effect state の中継だけで、scene/manager/renderer の三者へ責任を分裂させる。 |
| background nebula | **DELETE** | `97-120,746-775`; 魔法陣ではなく play-area UI background。実際の出力は spatial nebula ではなく二点サンプルから選ぶ単色全面塗り。 |

### `MagicCircleDemoscene`

| Feature | 判定 | 根拠 |
|---|---|---|
| 6 rotation phase fields + `advanceAngle()` | **DELETE** | `18-29,95-103,163-183`; 現在 raster に到達するが `dt * degrees/sec` の time-based animation。ユーザーが明示的に不採用。renderer の geometry owner と別 object に描画位相だけを置く fractured ownership。 |
| `runeDriftPhase` + `runeDriftAngleOffset()` | **DELETE** | `31,102,144-146`; raster 到達は rune band の ±0.5°だけ (`Nested...:191-192`)。time-based Perlin gimmick。 |
| `nebulaPhase` + `nebulaSample()` | **DELETE** | `32,103,154-160`; 魔法陣外の background 責任をこの class が所有。 |
| 6 `Wave` instances | **DELETE** | `37-71`; update/reset されるだけ (`106-111,130-135`) で、全 repo に `value()` / `valueNorm()` の reader がゼロ。描画結果ゼロ。 |
| FABRIK `trail` / `trailAlpha` / `trailSize` | **DELETE** | `80,86-87`; `solveTrail()` の caller がゼロ、`points`/fade arrays の reader もゼロ。renderer は別の4-dot trailを独自計算 (`Nested...:316-337`)。 |
| `reset()` | **DELETE with class** | `129-137`; dead Waves/chain だけ reset し、現行で追加された6 angles、`runeDriftPhase`、`nebulaPhase` は reset しない。コメントの deterministic reset 契約を満たさない。 |

### `NestedTimeboxInstrumentRenderer`

| Feature | 判定 | 根拠 |
|---|---|---|
| graphics-local radius/cell/center clamp | **KEEP** | `38-44,128-178`; accepted size/layout の authoritative math。 |
| 1 outer ring | **KEEP** | `180-184`; raster 到達。 |
| 2 rune band geometry | **KEEP** | `186-204`; raster 到達。Perlin offset だけ DELETE。 |
| 3 outer ticks | **KEEP static** | `206-213`; raster 到達。time-based angle inputだけ DELETE。 |
| 4 decoration dots | **KEEP** | `215-227`; static、raster 到達。 |
| 5 outer timer beads | **KEEP** | `229-237`; actual timer progress の表示であり gimmick ではない。 |
| 6 scripture ring | **KEEP static** | `239-255`; raster 到達。現在 rune band と同じ `scriptureAngle` を共有しており「slightly faster」というコメントは偽。 |
| 7 pentagram | **KEEP static** | `257-263`; accepted graphic layer。time-based angle inputだけ DELETE。 |
| 8 sector kanji | **KEEP static** | `265-281`; pentagram と同じ固定 orientation を使う。 |
| 9 octagram | **KEEP static** | `283-295`; accepted graphic layer。二つの固定角を直接所有すれば足りる。 |
| 10 inner timer beads | **KEEP** | `297-307`; nested timer progress 表示。 |
| 11 yin-yang core | **KEEP static** | `309-314,383-451`; procedural raster 自体は到達し、graphics 内部に閉じている。time-based orientationだけ DELETE。 |
| 12 direct 4-dot comet trail | **CONSOLIDATE** | `316-337`; 実際に raster される唯一の trail。FABRIK版は完全 dead。visual を維持するならこの直接計算だけを static ornament として残し、FABRIK一式を削除。不要な gimmick と判断するならこの block も同時削除できるが、accepted screenshot での要否はコードだけでは確定不能。 |
| 13 inner cardinals | **KEEP** | `340-356`; static、raster 到達。 |
| 14 center timer readout | **KEEP** | `358-376,456-605`; actual timer state 表示。 |
| nullable `demoscene` parameter + 6 phase reads | **DELETE** | `122,155-160`; renderer の静的 geometry に別 owner を注入する唯一の理由。 |
| unused `playAreaW` parameter | **DELETE** | declaration `121`; function bodyの readerゼロ。 |
| identical `SECTOR_KANJI` / `INNER_CARDINAL_KANJI` arrays | **CONSOLIDATE** | `70-71`; 同じ5文字を同じ順で二重保持。単一定数で足りる。 |

### `Wave`

- class 全体 **DELETE**。`MagicCircleDemoscene` 以外の constructor/ref はゼロで、その唯一の owner でも値を一度も読まない。
- さらに standalone としても `update()` は `phase += dt * frequency` (`Wave.kt:24-27`) なのに `value()` は `sin(2π * phase)` (`34-35,53-55`) としており phase は cycles。wrap を `TAU` で行うため (`26-27,58`) 1 cycle ではなく約6.283 cycles後に不連続 wrap する。説明と実装が不一致。

### `IkChain2D`

- class 全体 **DELETE**。到達可能な `solve()` caller がゼロ。
- 仮に caller を復活させても、`solve()` は毎回 `placeLinearly()` で履歴を消す (`31-37,93-107`) ので説明の「lag」にならない。
- hot path で immutable `Point2D` を反復ごとに代入生成 (`41,51-54,60,70-73,102-105`)。`Point2D` は data class (`ProceduralMath.kt:10`) なので1 solveあたり多数の heap allocation。hot-loop law 違反。
- `pointCount == 1` branch (`93-97`) は constructor の `pointCount >= 2` (`21-23`) により到達不能。
- 削除後は唯一の利用者を失う `Point2D` とその demoscene コメント (`ProceduralMath.kt:8-10`) も dead。

### `PerlinNoise`

- **DELETE with rune drift/nebula**。repo内 caller は `MagicCircleDemoscene.kt:146,156` の2箇所だけ。両 gimmick 削除後は全APIが dead。
- `noise1D`, `noise2D`, `fbm` 自体は raster へ直接描かず、上記二効果だけへ到達する。
- `noise1D` / `noise2D` は同一座標の `floor()` を各軸二度計算 (`PerlinNoise.kt:29-30,46-49`)。残す理由がある場合のみ局所値へ統合対象。
- 併せて `GeneratedPermLut.kt` も唯一の consumer を失う。generator script は他 LUT kind も担うため丸ごと削除対象とは断定しない。

### `VisualsStateHolder`

- `demosceneEffectsEnabled` **DELETE**: Settings から到達するが、visible result は rune ±0.5° drift だけ。6 WavesとFABRIKを有効化するというコメント (`VisualsStateHolder.kt:20-25`) は実態と違う。基本rotationは toggle を無視して継続 (`MagicCircleDemoscene.kt:95-105`)。
- `backgroundNebulaEnabled` **DELETE**: visible だが magic circle owner ではなく ActiveTimer background を変える別 feature。default false (`VisualsStateHolder.kt:18`)。
- holder はこの2 field しかないため、両 feature 削除なら class 全体が dead。

# Rejected

- `FrameClock` を戻す、新しい clock API、scene-private elapsed seconds、frame counter、想定 fps: すべて不採用。現行 magic-circle animation のためだけの時間責任を別場所へ移すだけで、one clean owner を作らない。
- `MagicCircleDemoscene` を「animation state owner」として温存: renderer が geometry を所有し、scene が lifecycle を所有する現状へ第三の owner を残す。
- dead FABRIKを direct trail と統合して復活: 描画されていない機能を直すために範囲を増やす。現 direct trail のほうが小さく allocation-free。
- radius/center/viewport/control-row math の変更: accepted appearance と no UI edit に反する。

# Unknown

- direct 4-dot comet trail (`Nested...:316-337`) が accepted screenshot の必須要素かは静止画・コードだけでは確定できない。安全側は static ornament として KEEPし、dead FABRIKだけ削除。
- `VisualsStateHolder` と二つの Settings rows を完全削除するには `SettingsScene` と文字列定義の機械的削除が必要。ただし本監査の「no UI edits」と衝突する。production edit前に親タスク側で scope 判断が必要。holderだけ残すのは dead setting を作るため不可。
- time/frame based 禁止が alarm overlay (`ActiveTimerScene.kt:268-328`) にも及ぶかは今回の magic-circle 指示からは確定しない。これは ringing UI であり本refactorでは触らない。

# Recommendation

1. `NestedTimeboxInstrumentRenderer` を魔法陣 graphics の唯一の owner にする。accepted radius/layout と14層の静的 geometry/readout/progress はそのまま保持。
2. renderer から `demoscene` と `playAreaW` 引数、phase reads、Perlin rune offset を除去。各回転層は現在の初期 orientation（scripture/outer/pentagram/squares `-90°`, core `0°`）を固定値として renderer 内だけで描く。
3. `ActiveTimerScene` から demoscene field/create/reset/update/pass と nebula draw/helper を除去。renderer call と returned-bottom layout は維持。
4. `MagicCircleDemoscene.kt`, `Wave.kt`, `IkChain2D.kt`, `PerlinNoise.kt`, `GeneratedPermLut.kt`、孤立する `Point2D` を削除。direct 4-dot trail はまず static ornament として維持（FABRIK duplicateのみ排除）。
5. visual settings削除が許可範囲なら `VisualsStateHolder` と Settings の2 rows/strings を機械的に同時削除。許可されないなら production 変更を止め、dead toggleを残さないようscopeを確認する。

この形なら update path に魔法陣固有状態はゼロ、render path は単一 owner・primitive loops・no allocation のまま、platform と UI layout を変更せず accepted radius/layout を保持できる。
