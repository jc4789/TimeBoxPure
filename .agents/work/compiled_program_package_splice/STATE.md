# 目的

`CompiledInstrumentBank`、`CompiledOpnaSong`、`CompiledOpnaTimeline` を物理 OPNA package から論理 MML package へ、挙動を変えず移す。

# 制約

- 所有権と依存方向だけを修復する。
- 音響値、イベント順、時間軌跡、公開動作を変えない。
- テストは作成も実行もしない。
- `ENGINE_BRIEF.md` の既存未コミット変更を編集しない。
- 関係ファイルと呼び出し元を全文で読んでから変更する。

# 計画

- [x] 対象3ファイルと直接関係ファイルを全文で読む。
- [x] 全参照と package 依存を確認し、変更許可リストを確定する。
- [x] 3ファイルを `audio.mml` package へ移し、必要最小限の import を修正する。
- [x] 差分と禁止事項を確認し、許可されたコンパイルだけで検証する。

# 確認済み

- `soundaudit_proper_interpretation.md` は Finding 9 を採用し、所有単位ごとの package と依存の同時修正を要求する。
- ユーザーは簡単な案として対象3型を `audio.mml` に置くことを認可している。
- 作業開始時点で `ENGINE_BRIEF.md` に既存の未コミット変更がある。
- 実行経路は `MmlCompiler` → `CompiledOpnaSongBuilder` → `CompiledOpnaSong` → `MmlArrangementScheduler` → `CompiledOpnaTimelineFactory` → `CompiledOpnaTimeline` → `CompiledOpnaPlayer` → `OpnaLikeSynthesizer`。
- `CompiledInstrumentBank` はコンパイル時に参照を intern し、タイムライン／プレイヤーへ不変の `FmPatch`／`SsgPatch` を渡す。
- 旧プレイヤー、デバッグ経路、テスト参照は対象型の参照集合に存在しない。`OpnaSequencer` はこの経路に参加しない。
- 変更許可リスト: `CompiledInstrumentBank.kt`、`CompiledOpnaSong.kt`、`CompiledOpnaTimeline.kt`、`ArrangementLanes.kt`、`CompiledOpnaPlayer.kt`、`LogoSong.kt`、`MmlArrangementScheduler.kt`、`MmlParser.kt`、`MmlCompiler.kt`、`PmdPerformanceState.kt`、`PmdSampleClock.kt`、`OpnaPatchBank.kt`。
- 対象3型は `audio.mml` を宣言し、旧 `audio.opna` import と `audio/mml` 配下の OPNA package 宣言はゼロ。
- 差分は12ファイル、9追加／21削除で、package/import 以外の処理本体変更なし。
- `compileCommonMainKotlinMetadata`、`compileDebugKotlinAndroid`、`app:compileDebugKotlin` は成功。既存 OPNA 高頻度経路監査も成功。
- テスト、APK組み立て、聴取は未実行。

# 却下

- `audio.mml.compiler` と `audio.driver` を同時新設する案は、粗案であり今回の最小修復を超えるため現時点では採用しない。

# 未確認

- 音響的採用判断。今回の構造限定差分では音響変更を意図しておらず、聴取は実行していない。

# 次

完了報告。
