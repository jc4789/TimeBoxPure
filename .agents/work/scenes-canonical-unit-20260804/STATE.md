# Objective
`Scenes.kt` の `ACTIVE TIMER SCENE` から `BLOCK OVERLAY SCENE` まで、canonical-unit-layout 違反を場面ごとに修正する。

# Constraints
- 一度に一場面だけ扱い、ファイル順に進める。
- UI 幾何だけを `U` から導出する。非 UI 値には触れない。
- 元の整数値を正確に維持する。
- 必要がない限り `f` や `.toFloat()` を付けず、整数のまま扱う。
- 新しい `private const val` を追加しない。
- ロジック変更、コードの書き換え、リファクタリングをしない。
- 指定方法や既存コードパターンを別設計へ置換しない。
- 不明点があれば編集を止めてユーザーへ確認する。
- `UIaudit.md` は開かず、変更もしない。
- `git` を使用しない。差分確認は編集箇所の再読と対象範囲の検索で行う。
- 作業台帳はコミットしない。
- `Scenes.kt` は LLM により生成されたコードであり、既存の `U` 表記を正解や模範として扱わない。
- 既存コードは場面境界、型、呼出し形の確認にだけ使い、各式はユーザー指定と canonical-unit-layout から個別に検証する。
an exeample of how a violation should be changed in to U 「28f = (U * 2)-(U/4)」
# Plan
- [x] ACTIVE TIMER SCENE
- [x] TEMPLATE CUSTOMIZER SCENE
- [x] SETTINGS SCENE
- [x] ENTROPY SCENE
- [x] BLOCK OVERLAY SCENE
- [x] 全差分を依頼条件と照合する

# Confirmed
- 対象ファイルは `shared-engine/src/commonMain/kotlin/com/example/timeboxvibe/engine/core/Scenes.kt`。
- 場面境界は 42、914、2095、2589、3141 行付近。
- `Scenes.kt` は BOM なしの有効な UTF-8。
- 開始時の `git status --short` には未追跡の `UIaudit.md` だけが表示された。内容は未閲覧。
- ACTIVE TIMER SCENE: 固定 UI 値 20、36、8、42、10、16、32、220、ボタン内部の 2 を、元値を維持する `U` 式へ変更した。
- ACTIVE TIMER SCENE: 半径比率、表示領域、時間、進捗、既存の算術係数、装飾線幅には触れていない。
- TEMPLATE CUSTOMIZER SCENE: 固定 UI 値 60、6、30、24、20、40、26、90、16、92、28、2、8 を、元値を維持する `U` 式へ変更した。
- TEMPLATE CUSTOMIZER SCENE: 表示領域比率、時間値、文字数上限、既存の算術係数には触れていない。
- SETTINGS SCENE: 固定 UI 幾何は既に `U` から導出されており、変更なし。表示領域比率、音量・振動量、件数、ラスタ最小幅は対象外。
- ENTROPY SCENE: ポップアップ実行ボタンの固定 UI 値 32 と 20 を、描画・当たり判定の双方で元値を維持する `U` 式へ変更した。
- BLOCK OVERLAY SCENE: 戻るボタンの固定 UI 値 200 と 32 を、描画・当たり判定の双方で元値を維持する `U` 式へ変更した。
- `:shared-engine:compileDebugKotlinAndroid` は成功した。
- 新しい `private const val`、関数、クラス、分岐、外部資産、ホットパス割当ては追加していない。
- 対象場面の表示領域比率、時間、進捗、音量、容量、文字コード、既存算術係数は維持した。

# Rejected
- 既存の `U` 表記をプロジェクトの正しい規範として採用すること。LLM 生成コードのため根拠にならない。

# Unverified
- 既存のピクセル差分テスト用ディレクトリが存在しないため、ピクセル差分は未実施。新規テストは追加していない。

# Next
完了報告を行う。
