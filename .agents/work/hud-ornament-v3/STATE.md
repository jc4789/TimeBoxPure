# Objective
Android v2 の HUD/枠を直す。ボタンの飾りが大きすぎる・塗りと枠の隙間・下部 HUD の点格子を消し、シーン背景だけ薄い手続き模様にする。

# Constraints
- commonMain のみ。魔法陣は触らない。
- U セル、16 色、整数スナップ、物理ピクセル直描き禁止。
- 外部 asset / コレクション / ホットパス確保禁止。
- Bézier は De Casteljau。

# Plan
- [x] VectorOrnament: SMALL フック縮小、PANEL 角は短辺でクランプ、内側二重枠を削除、ストロークを塗り inclusive に載せる
- [x] ボタン塗り込み（U/8 inset）をやめる
- [x] HUD の drawLattice を削除
- [x] プレイエリアに U 整列の青海波（BG_ALT）
- [x] テスト更新・追加、:shared-engine:testDebugUnitTest

# Confirmed
- 隙間の主因は U/8 の塗り inset。枠を exclusive 端から 1px 内側へ。
- SMALL hook は min(U/4, shortest/6)。細いステッパー中央は空。
- PANEL 角は min(U, shortest/5)。二重内側枠は削除。
- HUD は PANEL_DARK のベタ + 区切り線のみ。
- 青海波は 4U タイル、半円 cubic、世界 U グリッドにスナップ。ヘッダ再塗りでズレない。
- VectorOrnamentTest + testDebugUnitTest 成功。

# Rejected
- HUD に別の点/格子を残す
- 物理ピクセル dither を背景にする
- 画面全体を再フレームする

# Unverified
- 実機 4 画面の見え方（APK 入れ直し待ち）

# Next
ユーザーが APK を入れ直して四画面を見る。
