# Objective
`UIscaleOverhaul.md` を正本として、全 UI を一つの表示由来整数 `DisplayGrid`、`U = 16` の全角セル、単一 `S` に移行する。

# Constraints
- 設計・計算・問題認識を再検証せず、文書と最新のユーザー指示を実装する。
- Windows は各時点の `GetClientRect` を表示解像度として扱い、1900×983を固定しない。
- `Scenes.kt` の全シーン（サブシーン含む）を同じ UI 規則の対象にする。
- UI スコープは `RetroHudComponent.kt`、`ScaledProceduralRenderer.kt`、`Pc98GraphicsHardware.kt`、`ProceduralUiPrimitives.kt` を含むが、必要な変更だけ行う。
- 必要なら他の `commonMain` と Android/Win32 アダプターへ対象を広げてよい。既存設計・挙動を尊重する。
- Compose等のUIフレームワーク、外部アセット、依存関係、テスト基盤、診断機能を追加しない。
- DPIスキルの旧安全幅規則は使わず、`UIscaleOverhaul.md` の物理寸法だけによる `DisplayGridPolicy` を優先する。
- Phase 0〜5を連続実装し、文書の停止条件または解決不能な競合時のみ停止する。
- この作業台帳はコミット対象にしない。

# Plan
- [x] 現行差分と対象コードの利用経路を把握する。
- [x] Phase 0: commonMain の `DisplayGrid` 契約と共有状態を実装する。
- [x] Phase 1: テキスト/UIを単一の source-grid ラスタ経路へ移行する。
- [x] Phase 2: Android/Win32を不変グリッドsnapshot、整数S、入力逆変換へ移行する。
- [x] Phase 3: HUDをaspect-ratio法とcanonical cell寸法へ移行する。
- [x] Phase 4: `Scenes.kt` 全シーンと指定UI部品へ規則を一貫適用する。
- [x] Phase 5: 指定ビルド、禁止記号検索、差分・法則監査、可能な画面証拠を完了する。

# Confirmed
- 2026-08-17: 作業開始時の `git status --short` は空。
- 2026-08-17: ルート以下に追加の `AGENTS.md` は見つからなかった。
- 対象 source set は `commonMain`、Android、mingwX64/Win32。
- render/input/glyph hot path と Win32 C interop に触れる。新規 runtime resource は作らない。
- 2026-08-17: `DisplayGridPolicy`、source-grid glyph emission、Android/Win32 grid snapshot、固定cell HUDを実装。
- 2026-08-17: ガイド指定 common/Android compile は成功。
- 2026-08-17: ガイド指定 Win32 debug executable link は成功。
- 2026-08-17: ActiveTimer、TemplateCustomizer、TemplateForge、Settings、Entropy、BlockOverlay の canonical UI 式を描画・入力・スクロールへ一貫適用。
- 2026-08-17: Android debug APK assemble は成功。
- 2026-08-17: 最大化 Win32 ウィンドウの 1440×2512 capture で ActiveTimer、TemplateCustomizer、Entropy、Settings の日本語表示と bottom HUD を既存実行ファイルで確認。capture は非クライアント領域を含むため、この値を `GetClientRect` 値とは扱わない。
- 2026-08-17: 禁止シンボル検索は production match なし。残存 `U / 8` は cursor、border/bevel、header line、bar/timeline内部 detail のみ。
- 2026-08-17: Pixel 10 の物理 1080×2400 に対する実装式は `S=2`、33×75 cells、layout 528×1200、used 1056×2400、right remainder 24、bottom remainder 0。
- 2026-08-17: 最終 common/Android compile、Win32 link、Android debug APK assemble はすべて成功。`git diff --check` も成功。
- Win32の`memScoped`/`usePinned`所有権形は変更不要で、既存同期呼出し内に留まる。

# Rejected
- 1900×983を固定Windows解像度として扱う案: ユーザーが例示値と明言。
- Phase 4の4メインシーンだけに限定する案: ユーザーが `Scenes.kt` 全シーンを対象と明言。

# Unverified
- Android 接続端末がないため、Google Pixel 10 実機の4主要シーン screenshot と実機入力は未検証。
- 既存 indexed-framebuffer golden/pixel-diff 基盤がないため、hash・changed pixel count・off-grid draw count は未取得。指示に従い新規検証基盤は追加しない。
- Win32 の live landscape/left HUD と 1:1 tie はソース契約のみ確認し、実画面は未取得。
- Computer Use の画面は検査のみで永続保存していないため screenshot path はなし。

# Next
完了。実機が接続された時だけ、未検証の Android screenshot/input と live landscape/tie capture を追加確認する。
