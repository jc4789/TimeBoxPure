# Objective
v3 で飾りを縮小しすぎたのを戻す。隙間はクロム帯を塗って消し、ボタンを再び装飾する。パレットを 12bit/16 色の朱雀・翡翠方向へ分離する。青海波は残す。

# Constraints
- commonMain。魔法陣の幾何は触らない（パレット差し替えで色は変わる）。
- U、16 色、整数スナップ、物理ピクセル禁止。
- ホットパス確保禁止。Bézier は De Casteljau。

# Plan
- [x] VectorOrnament: クロム帯 + 内側塗り + 角スクロール復活
- [x] 全 fill+stroke を paintRectFrame に置換
- [x] EngineThemes を 和色でコントラスト強化
- [x] テスト更新・追加

# Confirmed
- ユーザー: 簡略化が問題。隙間は飾りが背景の上に浮いていたこと。青海波は成功。
- 朱雀ターゲットは金の「面」の枠で、1px 線ではない。
- テキストは明、HUD は PANEL_DARK。クリーム反転はしない。

# Rejected
- 飾りを小さくして隙間を消す（v3）
- Alice をクリーム地＋暗文字にする（HUD/TEXT が崩れる）

# Unverified
- 実機 4 画面

# Next
ユーザーが APK を入れ直して四画面を見る。
