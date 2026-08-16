# Scope

- 監査対象は `shared-engine/src/winMain/kotlin/com/example/timeboxvibe/engine/win/Win32Audio.kt`、`shared-engine/src/winMain/native/timebox_miniaudio.c`、`.h`、`miniaudio.def`。`shared-engine/build.gradle.kts:27-42,81-113` は C interop/リンク方法の確認、Android の `SoundPreviewPlayer.kt` は既存端末の意図確認だけに参照した。追加の到達性確認では `Win32TimerActions.kt`、`Win32SettingsStore.kt` と `SongCatalog` の公開契約だけを参照した。`commonMain` の実装品質は監査していない。
- 所有権分類は、`device` が `NATIVE_OWNED`、`stableSelf` が `STABLE_REF`、`onRender()` の `output` がコールバック中だけ有効な `BORROWED`、`memScoped` 内の `slot` が `SCOPED`。音声形式は 48 kHz、モノラル、`float32` で、C の一フレームと Kotlin の一要素が一致する（`Win32Audio.kt:34,95-148,175-192`、`timebox_miniaudio.c:62-71`、`timebox_miniaudio.h:8-17`）。
- 読み取り専用確認として `./gradlew.bat :shared-engine:compileKotlinWin --console=plain` は成功した。この Gradle コマンドは `shared-engine/build.gradle.kts:115-117` の依存設定により既存の `:shared-engine:opnaAudit` を一回実行し、結果は PASS だったが、その出力を `commonMain` 監査の根拠には使用していない。`test`、`commonTest`、Windows 実行試験は実行していない。さらに GCC 9.2.0 の `-std=c11 -Wall -Wextra -Wpedantic -Wshadow -fsyntax-only` で当該 C/H 自体の警告はなかった。製品コードとテストは変更していない。

# Confirmed

## 1. 初期化・開始失敗が呼び出し側へ一切伝わらず、アラームが無音で失敗する

- **重大度候補: 高（アラーム用途）**。
- C 境界は `ma_device_init()` と `ma_device_start()` の全エラーを同じ `-1` に潰す（`timebox_miniaudio.c:73-79,82-92`）。Kotlin 側はその値を状態・ログ・戻り値へ残さず、初期化失敗なら単に `start()` から戻り、開始失敗なら破棄するだけである（`Win32Audio.kt:177-205`）。公開三関数も `Unit` なので呼び出し側には成功したように見える（`Win32Audio.kt:56-78`）。
- 具体的な失敗経路は、既定出力装置なし、WASAPI 初期化失敗、装置使用不能などで `ma_device_init()` または `ma_device_start()` が失敗する → `Win32Audio` は無音のまま復帰する → 呼び出し側は失敗を検出できず、アラーム表示等だけを続行する、である。Android 参照実装は少なくとも生成・ストリーム例外を出力している（`SoundPreviewPlayer.kt:197-223,326-330`）。
- これは解放漏れではない。初期化失敗時は C が `self` を解放し、Kotlin も `StableRef` を破棄する（`timebox_miniaudio.c:73-76`、`Win32Audio.kt:193-198`）。問題は失敗情報と利用可能性が消失することにある。

## 2. 直接の無効再生要求は現在の音を止めないが、通常の TimerActions 経路では遮断される

- **重大度候補: 低**。
- Windows は曲検索または `buildPlayback()` が失敗すると `start()` に入らないため、そこで行う唯一の `stop()` も実行されない（`Win32Audio.kt:56-78,150-152`）。Android は各 `playPreview`、`playGentleReminder`、`playAlarm` の先頭で既存再生を止めてから曲を検証する（`SoundPreviewPlayer.kt:34-45,135-150`）。
- ただし、通常のアラームキーは初期値、設定更新、保存読込の全てでカタログ内 ID に補正される（`Win32TimerActions.kt:42-43,151-166,382-395`、`Win32SettingsStore.kt:161-170,192-197,227-238`）。`previewSound()` 自体は任意の文字列を転送するが、現在の画面呼出しは同じ補正済み `selectedFocusSound` / `selectedRelaxSound` を渡す（`Win32TimerActions.kt:227-229`、`Scenes.kt:2390,2397`）。現カタログは既定 ID と同じ一曲だけを公開する（`SongCatalog.kt:23-43`）。
- したがって具体的な失敗経路は、`Win32Audio.play*()` または `Win32TimerActions.previewSound()` を将来の別経路が未知キーで直接呼ぶ、あるいはカタログ項目の `buildPlayback()` が内部不整合で `null` になる → Windows は即時 return → 以前の音が継続する、に限定される。公開関数の挙動差は実在するが、現在の通常 UI・保存・アラーム経路から未知キーでは到達できないため重大度を下げる。

## 3. WASAPI 自動再ルーティングが回復不能になった停止を Kotlin 状態機械が観測できない

- **重大度候補: 中（条件付き。再ルーティングの再初期化・再開始失敗）**。
- C 設定は `dataCallback` だけを登録し、`notificationCallback` を登録しない（`timebox_miniaudio.c:62-71`）。C API に装置状態取得も停止通知も公開されていない（`timebox_miniaudio.h:8-21`）。一方、Kotlin の `pump()` は自前の `running == false` のときだけ破棄し、`running` は自前の停止、開始失敗、レンダー終端でしか false にならない（`Win32Audio.kt:36-47,80-89,95-115,200-205`）。
- miniaudio 自体は装置の started/stopped/rerouted 通知を持ち、明示停止以外の停止も通知対象としている（`miniaudio.h:6811-6851,20188-20228`）。WASAPI の既定装置変更では、通常は miniaudio が内部で stop → reinitialize → start を行う（`miniaudio.h:22475-22572`）。そのため通常の装置切替そのものは本指摘の失敗条件ではない。
- 静的に確定できる最小の失敗経路は、既定装置変更中の再初期化失敗などを受けた内部 `ma_device_start()` が失敗する → miniaudio 装置が stopped に残る → stopped を C ラッパーが転送せず data callback も再開しない → Kotlin の `running` は true のまま → `pump()` が破棄・再初期化へ進まない、である（`miniaudio.h:22530-22570,24514-24535,44329-44336`、`Win32Audio.kt:80-83`）。発生には自動再ルーティングの再開始失敗が必要なので、重大度を高から中へ下げる。

## 境界・ホットパスの合格事項

- `StableRef` は装置初期化前に作成し、`ma_device_stop()`/`ma_device_uninit()` の後で一度だけ破棄される（`Win32Audio.kt:175-205,208-219`）。`ma_device_stop()` は作業スレッド停止を待って復帰する契約なので、現在の順序に callback-after-dispose は確認できない（`miniaudio.h:9214-9231`）。
- `memScoped` から逃げるのは `slot.ptr` ではなく、C の `calloc()` で確保された `slot.value` である（`Win32Audio.kt:177-192`、`timebox_miniaudio.c:55-79`）。スコープ外ポインター参照ではない。
- miniaudio の可変 `frameCount` は 4096 フレーム単位へ分割され、共有 `FloatArray` を越えない。モノラルなので `output[frame]` の添字も正しい（`Win32Audio.kt:34,118-147`、`timebox_miniaudio.c:63-69`）。コールバック本体に新規配列、コレクション演算、文字列生成、コルーチンはない。

# Rejected

- **`stop()` と `shutdown()` の二重呼び出しによる二重解放**: `teardownDevice()` は最初に `device = null` とし、`stableSelf` も解放後 null にするため、二回目は C 解放を呼ばない（`Win32Audio.kt:86-93,208-219`）。Host が actions と audio の両方から止める構造でも、現実装は冪等である。
- **停止中の use-after-free / `player`・`synth` の競合**: 呼び出し元の開始・停止・pump は Win32 主スレッド経路に集約され、停止時は `running = false` の後に `ma_device_stop()` が callback 完了を待ち、最後に共有オブジェクトを null にする（`Win32Audio.kt:86-89,150-219`）。現在の呼び出し関係では確認できない。
- **C callback の早期 return が未初期化ノイズを出す**: miniaudio は既定で output buffer を callback 前に無音化する（`miniaudio.h:8703-8706,20244-20255`）。`timebox_miniaudio.c:28-34` の return だけからノイズとは判定しない。
- **`noFixedSizedCallback = MA_TRUE` によるバッファ超過**: miniaudio の frame count は任意になり得るが、Kotlin は callback 全体を固定配列サイズ以下へ分割している（`timebox_miniaudio.c:69`、`Win32Audio.kt:121-146`）。
- **Windows 側だけが OPNA 合成を再実装している**: `Win32Audio` は共通の `CompiledOpnaPlayer` / `OpnaLikeSynthesizer` を呼ぶだけで、Android 参照端末も同じ生成・loop-reset 手順を持つ（`Win32Audio.kt:29-32,150-172`、`SoundPreviewPlayer.kt:153-190,343-380`）。Windows 固有の合成ロジック漏出とは判定しない。

# Unknown

- `win32AudioRenderThunk()` は例外を捕捉しない（`Win32Audio.kt:230-238`）。`onRender()` 以下から例外が出れば C callback 境界でプロセス終了へ至る可能性があるが、例外発生可能性の確定には監査対象外の共通プレイヤー内部までの証明が必要なため、確認済み不具合にはしない。
- `stop()` は Win32 主スレッドで同期的に `ma_device_stop()` と `ma_device_uninit()` を行う（`Win32Audio.kt:86-89,208-214`）。miniaudio は stop が作業スレッドや現在フラグメントを待つ場合があると明記し（`miniaudio.h:9214-9218`）、要求値は 2048 フレーム×4 period（`Win32Apis.kt:46-49`）だが、実際の UI 停止時間は WASAPI が採用した値と実機測定なしには確定できない。
- 装置抜去、既定装置なし、再初期化失敗、再開始失敗別の自動再ルーティング結果は実機未確認。上記 Confirmed 3 は「回復失敗後の stopped を通知・状態取得する経路が存在しない」ことの静的確認であり、各 Windows 11 環境での発生頻度までは示さない。

# Recommendation

1. C API は `ma_result` を識別可能な値で返し、Kotlin の `playPreview` / `playAlarm` / `playGentleReminder` も成功・失敗を呼び出し側へ返すか、少なくとも一箇所へ失敗状態を保存する。初期化失敗と開始失敗を区別し、アラーム無音を黙殺しない。
2. 低優先度の契約整理として、Android の既存パターンに合わせ、新しい再生要求では曲検索・`buildPlayback()` より前に現在音を停止する。現在の通常経路はキーを補正済みなので、緊急修正とはしない。
3. miniaudio の notification callback または安全な装置状態取得を薄い C 境界へ追加し、callback では原子的な状態フラグだけを更新する。停止・破棄・再初期化は現在どおり主スレッドの `pump()` で行い、miniaudio が禁じる callback 内 stop/uninit は行わない。通常の reroute と回復不能 stopped を区別する。
4. 現在の `stop -> uninit -> StableRef.dispose` 順序、C 所有装置、可変フレーム分割、事前確保バッファは維持する。同期 stop の UI 影響は実機計測で問題が確認された場合だけ別途扱う。
