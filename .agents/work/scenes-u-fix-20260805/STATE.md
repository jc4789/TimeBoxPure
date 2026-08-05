# Objective
`Scenes.kt` の U 違反を、`TemplateCustomizerScene` から `BlockOverlayScene` まで一シーンずつ修正する。

# Constraints
- `ActiveTimerScene` はグラフィックスであり完全に対象外。一文字も変更しない。
- UI 対象はボタン、メニュー、テキストフィールド、HUD、サイドバー、タッチ当たり判定、縦積み、レスポンシブ行、グリフ描画、IMGUI レイアウトカーソル。
- 元の整数値・計算結果を正確に維持する。
- 単純な `f`／`.toFloat()` 除去を修正として扱わない。
- 非 UI 値、表示寸法キャッシュ、時間、割合、音量、進捗、グラフィックスを変更しない。
- 新規 `private const val`、新規関数、リファクタリング、命名変更、ロジック変更を行わない。
- 描画とタッチ当たり判定の対応式は同じシーン内で揃える。
- `UIaudit.md` を見ない。
- git を使わない。

# Plan
- [x] TemplateCustomizerScene
- [x] TemplateForgeScene
- [x] SettingsScene
- [x] EntropyScene
- [x] BlockOverlayScene
- [x] `Scenes.kt` の対象範囲を再確認する

# Confirmed
- `Scenes.kt` 全3262行と直接関連ファイルは前段で全文確認済み。
- U はファイル先頭の `private const val U = 16`。
- Entropy の 14、14、12 は現在 U 式へ変更済み。
- TemplateCustomizerScene の固定 UI 幾何は現在 U 式。残る小数は表示寸法に対する割合なので変更しない。
- TemplateForgeScene の安全上端12と内容余白20を、元値を保つ U 式へ変更した。
- SettingsScene の安全上端12、左右余白20、バー内部の最小幅1を、元値を保つ U 式へ変更した。
- EntropyScene の左右余白24、安全上端12、追加ボタン幅4、ページ切替ボタン18を、描画と当たり判定の双方で同じ U 式へ変更した。
- BlockOverlayScene の固定ボタン最小幅200・最小高32はすでに U 式。残る小数は表示寸法比率であり変更しない。
- 最終検索で旧固定値表記の残存なし。ActiveTimerScene を対象とする編集なし。
- `$env:JAVA_HOME="D:\Programes\Android Studio\jbr"; .\gradlew :app:assembleDebug` は成功した。

# Rejected
- ActiveTimerScene の修正。
- タイマー円・魔法陣などグラフィックスの U 化。
- 論理寸法／playArea 自体の変更。

# Unverified
- なし。

# Next
完了。ユーザーへ変更内容とビルド結果を報告する。
