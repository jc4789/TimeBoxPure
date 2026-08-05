# Scope
`Scenes.kt` の `TemplateCustomizerScene` から `BlockOverlayScene` までにある UI 固定値の U 違反修正。

# Confirmed
- `ActiveTimerScene` は変更していない。
- `TemplateCustomizerScene` は固定 UI 値がすでに U 式であり、追加編集なし。
- `TemplateForgeScene`: 安全上端12、内容余白20を U 式へ変更。
- `SettingsScene`: 左右余白20、安全上端12、バー内部最小幅1を U 式へ変更。
- `EntropyScene`: 左右余白24、安全上端12、追加ボタン幅4、ページ切替ボタン18を描画・当たり判定の双方で U 式へ変更。
- `BlockOverlayScene` は固定ボタン最小幅200・最小高32がすでに U 式であり、追加編集なし。
- 新規定数、新規関数、ロジック変更、命名変更なし。
- `:app:assembleDebug` 成功。

# Rejected
- 表示寸法キャッシュの U 化。
- `0.x` や `3f / 20f` など表示寸法に対する割合の U 化。
- 時間、音量、進捗、スピン、グラフィックスの変更。
- `ActiveTimerScene` の変更。

# Unknown
- なし。

# Recommendation
今回の範囲に追加変更は不要。
