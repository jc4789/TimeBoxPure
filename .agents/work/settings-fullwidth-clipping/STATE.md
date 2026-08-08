# Objective
設定画面の操作値依存レイアウト変更を戻し、全角1字＝Uの文字を16x16セル単位で安全にクリップする。

# Constraints
- 編集対象は ProceduralUiPrimitives.kt、ScaledProceduralRenderer.kt、Scenes.kt のみ。
- 半角字・ASCII対応を設計前提にしない。
- 東雲16x16フォント、DPI算出、プラットフォーム層、外部資源を変更しない。
- 既存のテンプレート画面修正は保持する。

# Plan
- [x] 対象差分を再確認する。既存検証手段は引き続き確認する。
- [x] SettingsScene の操作値依存レイアウト変更を基準版へ戻す。
- [x] 全角セル単位のクリップを描画経路へ実装する。
- [x] 差分、高頻度経路、既存ビルドを確認する。画素回帰基盤は存在しないため未実施として記録する。

# Confirmed
- ScaledProceduralRenderer は全ての文字を U 幅で測定・送出する。
- SettingsScene の追加差分は操作値の長さを横並び判定へ混入させている。
- drawText は現在グリフ内部を画素単位で切る。
- テンプレート画面は新しい整数倍率選択を使用している。
- SettingsScene の作業差分は消え、Scenes.kt にはテンプレート修正だけが残った。
- ProceduralTextRenderer.drawClipped は完全に収まる U セルだけを描く。
- SettingsScene.drawStepper は左右矢印と U/4 余白を除いた実文字領域を使用する。
- commonMain、Android、Win のコンパイルが成功した。
- Android アプリの assembleDebug が成功した。
- 追加行に禁止されたコレクション、割り当て、プラットフォームAPI、外部資源参照はない。
- SettingsScene の配置規則は基準版へ戻り、差分は drawStepper の内側文字領域だけになった。

# Rejected
- 全角ラテン字を U/2 で測定している、という仮説。対象経路は一律 U 幅。
- DPI変更による修正。問題は論理空間内の配置とクリップ。

# Unverified
- 画素ハッシュと変更画素数。既存の基準バッファ／画素差分テストがない。
- 実機画面。端末へのインストールと撮影は実施していない。

# Next
完了。最終結果をユーザーへ報告する。
