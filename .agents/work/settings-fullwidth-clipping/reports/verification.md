# Scope
対象3ファイルの差分、既存検証手段、最終監査。

# Confirmed
- SettingsScene の操作値依存 layoutRow 差分はすべて基準版へ戻った。
- drawClipped は U * scale の完全セルが整数化済みクリップ矩形へ収まる場合だけ drawGlyph を呼ぶ。
- drawStepper の文字開始座標は従来と同じで、左右 U/4 の内側領域をクリップ境界として使う。
- commonMain、Android、Win コンパイル成功。
- app:assembleDebug 成功。
- git diff --check 成功。
- 追加行のエンジン禁止記号なし。

# Rejected
- DPI、画面向き、端末名、フォントデータの変更。
- 操作値の長さによる Settings 行の縦積み。
- 半角幅または非整数文字倍率。

# Unknown
- 画素ハッシュ、変更画素数、実機スクリーンショット。既存の画素差分基盤がない。

# Recommendation
- 実機では集中音・休憩音の末尾が半グリフにならず、最後の完全セルで止まることを確認する。
