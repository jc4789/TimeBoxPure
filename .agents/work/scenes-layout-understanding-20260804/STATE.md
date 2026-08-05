# Objective
`Scenes.kt` 全行と直接関連ファイルを読み、canonical-unit-layout、DPI、論理寸法、プラットフォーム境界を理解したうえで、修正が必要な箇所を根拠付きで特定する。

# Constraints
- 今回は読取り監査のみ。ソースコードを変更しない。
- `Scenes.kt` を先頭から末尾まで全行読む。
- 直接関連するファイルだけを、シンボルと呼出し経路を根拠に読む。
- `UIaudit.md` は開かず、変更もしない。
- `git` は使用しない。
- 固定 UI 幾何と、時間・割合・音量・容量・文字コード・表示寸法キャッシュを混同しない。
- 既存コードが LLM 生成であるため、既存の `U` 表記を正解として扱わない。
- 新しいテスト、診断コード、検証基盤を追加しない。
- 作業台帳と報告書はコミットしない。

# Plan
- [x] `Scenes.kt` 全行を読む
- [x] 直接関連シンボルと定義ファイルを特定する
- [x] 論理寸法・DPI・入力経路を確認する
- [x] UI 幾何の違反候補と対象外値を分類する
- [x] 根拠付き監査報告を完成する

# Confirmed
- 対象は `shared-engine/src/commonMain/kotlin/com/example/timeboxvibe/engine/core/Scenes.kt`。
- 指定スキル4件と engine-law-check を全て再読した。
- ユーザーの最新指示は、変更前に全体と直接関連ファイルを理解すること。
- `Scenes.kt` 1～3262 行を全て読んだ。
- 共通描画経路を全文確認した：`EngineCanvas.kt`、`SceneManager.kt`、`RetroHudComponent.kt`、`ScaledProceduralRenderer.kt`、`EngineCursorRenderer.kt`、`FixedInputContainer.kt`、`ProceduralUiPrimitives.kt`、`NestedTimeboxInstrumentRenderer.kt`。
- Android 経路を全文確認した：`Pc98SurfaceView.kt`、`AndroidEngineCanvas.kt`、`MainActivity.kt`、`BlockOverlayActivity.kt`、`AndroidHeadlessInputBridge.kt`。
- 入力・状態の境界を確認した：`TouchColliderManager.kt`、`InputPrimitives.kt`、`PlatformInputTrigger.kt`、`TimerActions.kt`、`SessionMacroDisplay.kt`、`EngineThemes.kt`、`VisualsStateHolder.kt`。
- 場面の描画寸法は `SceneManager.render` が `RetroHudComponent` から算出して渡す。
- Android の論理寸法は `Pc98SurfaceView` が生成し、同じ整数スケールを描画拡大とタッチ逆変換に使い、`AndroidEngineCanvas` と `SceneManager` に渡す。
- iOS／Win32 の列挙済みソースセットには `PlatformTime.kt` しかなく、Scene host／Canvas 実装は存在しない。
- UI の範囲は、ボタン、メニュー、テキストフィールド、HUD、サイドバー、タッチ当たり判定、縦積み、レスポンシブ行、グリフ描画、IMGUI レイアウトカーソルに限定する。
- シーン別の修正候補、変更対象外、未確定二点を `reports/layout-audit.md` に記録した。

# Rejected
- 過去の部分監査を、今回の全体理解の代用にすること。
- 数値の型だけを機械的に変更すること。

# Unverified
- Settings のバー内部 `maxOf(1f, ...)` を `U / 16` としてよいか。
- Active Timer の `ornamentCy` に含まれる `0.28f` は装飾とグリフ積み上げの双方へ影響するため、修正対象か。

# Next
ユーザーへ監査結果と未確定二点を報告し、ソース編集前の判断を求める。
