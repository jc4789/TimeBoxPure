# Objective
設定画面以外の全場面で、表示用Latinを東雲BDF由来の全角グリフへ統一する。

# Constraints
- 内部ID、言語コード、モードキー、音源キーは変更しない。
- 半角字形の縮小・拡大や全描画共通のASCIIフォールバックは追加しない。
- 動的入力と内部IDは保存値を変えず、非設定画面の表示境界で実体の全角字形へ正規化する。
- U = glyphWidth = glyphHeight を維持する。
- ShinonomeGeneratedGlyphs.kt の内容は直接閲覧しない。

# Plan
- [x] 全描画経路の表示用ASCIIを分類する。
- [x] 静的な表示用Latinを全角文字へ置換する。
- [x] 動的表示を東雲対応の全角字形へ正規化する。
- [x] 東雲サブセットを欠落0件で再生成する。
- [x] 差分、ビルド、既存の画素検証手段を確認する。

# Confirmed
- 設定画面の表示英字は全角で、矢印のみASCII記号。
- エントロピー本文は倍率1、見出しのみ倍率2。
- 半角ASCII字形は手書きGLYPHS、全角Latinは東雲BDF由来。
- 東雲JIS経路では全角引用符・アポストロフィ・ハイフンにJIS対応字形が必要。
- 設定画面の範囲には Scenes.kt の差分がない。
- 東雲生成結果は direct=560、missing=0 で再生成しても同一SHA-256。
- 全角表示95字は全て東雲BDF上で DWIDTH 16、BBX 16x16。
- commonMainメタデータとAndroid debug APKのビルドが成功。

# Rejected
- 全画面共通DPI倍率が場面ごとにLatinだけを変形させる仮説。

# Unverified
- 対象UI用の既存画素ゴールデンは存在しないため、実機画面の見た目。

# Next
実機で非設定画面の全角Latin表示を確認する。
