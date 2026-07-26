# Objective
PMD MML経路から固定小節機構を完全撤去し、Bad AppleをPMDクロックだけで成立させる。

# Constraints
- 基準点は段階5コミット `785d93c`。既存動作を保持する。
- `SENBONZAKURA_DEMO_MML` は `BAD_APPLE_LLS_MML` の別名として保持する。
- Logo曲は有効曲ではなく、互換性を残さない。
- `OpnaSequencer` の手続き的モチーフ経路は対象外。
- 新規テストを作成せず、既存テストも実行しない。

# Plan
- [x] 撤去対象と既存差分を基準化する。
- [x] MML文法・コンパイラ・型から固定小節機構を除去する。
- [x] Bad Appleから固定小節構文を除去し、Logo経路と古い説明を除去する。
- [x] 残存参照、差分、エンジン法則を監査し、コンパイル確認する。

# Confirmed
- `#BAR` は初期MML実装で小節境界と終端を検査するために導入された。
- `beatsPerBar` は現行PMD再生経路では読み取られていない。
- `MmlCommand.Bar` はクロックもイベントも生成しない。
- 段階5はコミット `785d93c`、作業開始時の追跡対象差分はない。
- `MmlDocument`、`MmlCommand`、`MmlCompiler`、`CompiledOpnaSong`、`ArrangementLanes` から固定小節状態を除去した。
- Bad Appleから `#BAR` と17個の `|` を除去し、PMDクロック基準の説明へ変更した。
- Logo曲、専用音色庫、曲庫キー・結果・分岐を削除した。
- 正規化したBad Apple命令列は段階5基準と完全一致し、SHA-256は双方 `5E689C000A17E98D1252F9C51A239846CD2BDB542F0F795AD83D9A66E280B5FE`。
- 共通Kotlin、Android共有エンジン、アプリのコンパイルに成功し、既存OPNA監査も通過した。
- 固定小節名はPMD経路から消え、`beatsPerBar` は対象外の直接 `OpnaSequencer` 内だけに残る。

# Rejected
- `#BAR` を無視する互換層は残さない。PMD文法から完全に除去する。

# Unverified
- 人間による音響確認。命令列同一性を確認済みのため今回の必須条件にはしない。

# Next
最終差分と作業ツリーを報告する。
