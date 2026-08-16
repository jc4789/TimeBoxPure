# Scope

- 読み取り専用監査対象: `Win32AlarmScheduler.kt`、`Win32TimerActions.kt`、`Win32Power.kt`、`Win32SettingsStore.kt`、winMain の `PlatformTime.kt`。
- 意図確認だけに使用: `AndroidAlarmScheduler.kt`、`FocusService.kt`、`TimerStateHolder.kt`、`BlockOverlayActivity.kt`、`DataRepository.kt`、`MainScreenViewModel.kt`、Windows 側の呼出元 `Win32Host.kt`。commonMain は `TimerEngine` の状態遷移と `TimerActions` 契約の確認だけに使用し、監査対象にはしていない。
- 製品コード変更・試験追加・実行はしていない。レイアウト、DPI、画素差分、パレット処理は本スコープの各ファイルに存在しないため、該当する指摘はない。

# Confirmed

## 1. 次区間用に予約したアラームを Windows 側が直後に解除する

**重大度候補: 高。** 自動継続する区間の終了時、`TimerEngine` は次区間へ進んで次の exact alarm を予約してから `IntervalComplete` を返す（意図確認: `TimerEngine.advanceExpiredBlock`, `TimerEngine.kt:532-630`）。ところが Windows は `IntervalComplete`/`SequenceComplete` を受けると最初に無条件で `cancelAlarm()` を呼ぶ（`Win32TimerActions.handleTickEvent`, `Win32TimerActions.kt:315-328`）。`eng.isActive == true` の gentle-reminder 経路には再予約がないため、次区間の OS アラームが消える。Android は同じ完了処理後、継続中なら `scheduleAlarm()` を明示的に呼び直している（`FocusService.tick`, `FocusService.kt:399-404`）。

結果として通常のフレームポンプが動く間は QPC の秒進行で見かけ上動くが、次区間では UI スレッド停止・長時間遅延を補う OS アラームが存在しない。

## 2. `WM_APP_ALARM` がどの予約に属するか識別できず、古い通知が現在区間を即時終了できる

**重大度候補: 高。** 待機スレッドはタイマーのシグナルを受けると、世代番号・予定 epoch・予約 ID を一切載せず `WM_APP_ALARM` を投函する（`Win32AlarmScheduler.waiterLoop`, `Win32AlarmScheduler.kt:108-125`）。UI 側は「現在 engine が active か」だけを確認し、active なら `hasSyncInterruption = true` として現在の残り時間を強制消化する（`Win32TimerActions.onOsAlarm`, `Win32TimerActions.kt:92-97`; 意図確認: `TimerEngine.processSyncInterruption`, `TimerEngine.kt:366-377`）。

具体的には、旧タイマーがシグナル状態になった後、待機スレッドが `PostMessageW` する前に UI 側の QPC ポンプが gentle 区間を完了して次区間へ進む、またはユーザーが停止後に再開する競合で成立する。その後の旧 `WM_APP_ALARM` は新しい active 区間を正規期限前にゼロへ進める。`CancelWaitableTimer` は既に投函済み、またはシグナル取得後に投函される Windows メッセージを取り消せない（投函箇所 `Win32AlarmScheduler.kt:118-122`; 受信箇所 `Win32Host.kt:391-393`）。

## 3. 待機スレッド終了を確認せず、待機中のハンドルを閉じられる

**重大度候補: 高（発生頻度は低いが、発生時は未定義動作）。** `shutdown()` はスレッドを最大 1000 ms 待つが、`WaitForSingleObject` の戻り値を確認せず、その直後に timer、stop event、`StableRef` を破棄する（`Win32AlarmScheduler.shutdown`, `Win32AlarmScheduler.kt:92-105`）。タイムアウトまたは待機失敗なら worker はまだ `WaitForMultipleObjects` 中、あるいは callback 開始前であり得る。前者で待機対象ハンドルを閉じることは Win32 仕様上未定義、後者で `StableRef` を破棄すると thread entry の `asStableRef()` が無効参照になる（thread entry `Win32AlarmScheduler.kt:65-70`; wait `Win32AlarmScheduler.kt:111-127`）。Microsoft の [`WaitForMultipleObjects`](https://learn.microsoft.com/en-us/windows/win32/api/synchapi/nf-synchapi-waitformultipleobjects) 資料も、待機中の handle close は未定義としている。

**C INTEROP BOUNDARY CHECK:** `StableRef` は `STABLE_REF`、所有者は `Win32AlarmScheduler`、正常 shutdown が解放経路。ただし thread 終了確認より先に dispose できるため **FAIL**。

## 4. スレッド作成・タイマー設定・通知投函の失敗がすべて黙殺される

**重大度候補: 中。** `CreateThread` が null を返しても `startWaiter()` は成功したように戻り、作成済み `StableRef` は shutdown まで残る。さらに同メソッドを再試行すると前の `StableRef` を上書きして恒久リークする（`Win32AlarmScheduler.startWaiter`, `Win32AlarmScheduler.kt:58-74`）。`SetWaitableTimer`、`CancelWaitableTimer`、`SetEvent`、`PostMessageW` の戻り値も未確認である（同 `:76-105,116-122`）。特に `PostMessageW` 失敗時は auto-reset timer の通知を待機スレッドが既に消費しており、現在予約の通知が失われる。Microsoft の[メッセージ列資料](https://learn.microsoft.com/en-us/windows/win32/winmsg/about-messages-and-message-queues)も `PostMessage` の失敗を確認して再投函するよう明記している。

なお、現在の host は `startWaiter()` を一度だけ呼ぶ（`Win32Host.kt:216-220`）ため、再試行リーク自体は現行通常経路では発生しない。ただし最初の作成失敗で OS アラーム機能が無言で無効になる経路は現行に存在する。

## 5. `skipTimer()` が engine の状態変化と電源・音声の副作用を同期しない

**重大度候補: 中。** Windows は `skipTimer()` で `engine?.skip()` だけを呼ぶ（`Win32TimerActions.kt:130-132`）。sequence/calendar/dual-sequence の最終区間を skip すると engine は inactive になり alarm を解除するが（意図確認: `TimerEngine.skip/advanceExplicitStage`, `TimerEngine.kt:648-689`）、`ES_SYSTEM_REQUIRED` は解除されず、以後 stop/reset/select/shutdown まで PC の自動スリープを妨げる（取得 `Win32TimerActions.kt:99-115`; 解除 API `Win32Power.kt:19-25`）。逆に停止中の非最終区間で skip すると engine は active になり alarm を予約するが、`acquireSession()` は呼ばれない。ringing 中なら engine が ringing を抜けても `audio.stop()` されない。skip ボタンは mode だけで表示判定され、running/ringing でゲートされていないため、この入口は UI から到達可能である（意図確認: `Scenes.kt:531-559,753-755`）。

**電源要求の釣合い:** start/stop/reset/dismiss/select/shutdown の通常経路は同じ UI thread 上で釣り合う（`Win32TimerActions.kt:99-144,180-187,309-313`; host loop `Win32Host.kt:275-306`）。不釣合いは上記 skip 遷移に限定して確定した。

## 6. 設定保存が既存ファイルを先に切り詰め、書込み結果を無視する

**重大度候補: 中。** `save()` は最終ファイルを `CREATE_ALWAYS` で直接開くため、書込み前に既存の正常ファイルを 0 byte にする。その後 `WriteFile` の BOOL と `written.value` を一切確認せず、失敗・短い書込みでも成功扱いで handle を閉じる（`Win32SettingsStore.save`, `Win32SettingsStore.kt:43-75`）。[`WriteFile`](https://learn.microsoft.com/en-us/windows/win32/api/fileapi/nf-fileapi-writefile) は成否と実書込み byte 数を別々に返す。ディスク満杯、I/O エラー、プロセス停止などで次回 `load()` は既定値へ戻り、全設定を失う（`load/readAllBytes`, `Win32SettingsStore.kt:38-41,77-111`）。一時ファイルへの完全書込みを検証してから同一ディレクトリで置換する処理がなく、クラッシュ整合性もない。

`bytes.usePinned` は同期 `WriteFile` の呼出し内だけ有効であり、ポインター寿命そのものは正しい（`Win32SettingsStore.kt:60-70`）。問題は保存トランザクションと結果確認である。

## 7. 文字入力ごとに UI thread で同期ファイル I/O を行う

**重大度候補: 中。** `updateTask()` は常に `persistSettings()` を同期実行し（`Win32TimerActions.kt:146-149,400-416`）、その先で directory、file open、UTF-8 全量書込み、close を行う（`Win32SettingsStore.kt:43-75`）。タスク欄は各入力コードごとに `updateTask()` を呼ぶ（意図確認: `Scenes.kt:401-416`）。Windows host は同じ thread でメッセージ処理、engine update、render、present を行う（`Win32Host.kt:275-303`）ため、キー入力のたびにフレームとメッセージ処理がディスク I/O 待ちになる。`%APPDATA%` がリダイレクト先の場合は遅延が特に顕在化する。Android 参照実装は DataStore 書込みを `viewModelScope.launch` に分離している（`MainScreenViewModel.updateTask`, `MainScreenViewModel.kt:312-318`）。

## 8. `getUiState()` が呼出しごとに全プリセットを再生成・再正規化する

**重大度候補: 中～低（フレーム時間・GC ジッター）。** `getUiState()` の冒頭で毎回 `currentPresets()` を呼び（`Win32TimerActions.kt:240-242`）、そのたびに default 全件の `map/normalized`、emergency preset の構築、3 リストの連結を行う（同 `:359-369`）。`getUiState()` は scene の update/render から繰返し参照される（例: `Scenes.kt:93,108,124,484`）ため、platform terminal の通常フレーム経路に不要なオブジェクト生成を持ち込む。Android は DataStore 更新時に `combined` を作り UI state に保持する（`MainScreenViewModel.kt:149-187`）。

## 9. 保存可能サイズと読込可能サイズが一致しない

**重大度候補: 低。** 保存側は byte 数を制限しないが、読込側は 262,144 byte 超を無条件で拒否する（save `Win32SettingsStore.kt:43-75`; load limit `:90-93,243-245`）。`customPresets` は `upsertCustomPreset()` で件数上限なく増やせ、毎回 JSON 全量を保存する（`Win32TimerActions.kt:193-214,400-415`）。したがって UI で十分な数のカスタムプリセットを追加できれば、保存直後は動作するが次回起動で全設定が既定値になる、という自己生成可能な破損経路がある。

## 10. `%APPDATA%` が 512 UTF-16 code unit 以上だと永続化が無言で全面停止する

**重大度候補: 低。** `GetEnvironmentVariableW` 用 buffer が固定 512 で、必要長がそれ以上なら null を返すだけで再確保しない（`Win32SettingsStore.roamingAppData`, `Win32SettingsStore.kt:123-136,243`）。[`GetEnvironmentVariableW`](https://learn.microsoft.com/en-us/windows/win32/api/processenv/nf-processenv-getenvironmentvariablew) が扱うユーザー環境変数は最大 32,767 文字を取り得るため、長い、または管理された環境の `APPDATA` では load は既定値、save は無処理になる。広い環境変数上限に追従する二段階問合せ、または既知フォルダ API は使われていない。

## 11. プラットフォーム端末が domain/controller の責務を所有している

**重大度候補: 中（設計逸脱と Android/Windows 差分の温床）。** `Win32TimerActions` は OS 入出力の転送だけでなく、設定値、`TimerEngine` の生成・所有・秒進行、custom preset の upsert/delete、emergency preset と日本語・中国語名の生成、alarm/gentle の製品方針、UI state の組立てを所有する（`Win32TimerActions.kt:35-59,66-90,189-224,240-297,315-416`）。クラスコメントの「only owns OS-side scheduling and audio」とも一致しない（同 `:25-28`）。Android では少なくとも UI 設定・プリセット管理は `MainScreenViewModel`、バックグラウンド進行は `FocusService`、OS exact alarm は `AndroidAlarmScheduler` に分かれている（`MainScreenViewModel.kt:149-187,574-615`; `FocusService.kt:304-407`; `AndroidAlarmScheduler.kt:13-111`）。

**PLATFORM FIREWALL CHECK:** Platform は Win32。allowed responsibility である alarm、電源、保存 I/O は存在するが、core responsibility である session state、preset policy、localized preset creation まで漏れている。**Result: FAIL**。

## 合格した境界

- `PlatformTime.getEpochMillis()` の `FILETIME` は `memScoped` 内で取得・消費され、1601→1970 の定数と 100 ns→ms の変換も正しい（`PlatformTime.kt:10-20`）。壁時計 epoch は OS alarm の deadline 作成、QPC は経過時間に分離されている（`Win32AlarmScheduler.kt:76-83`; `Win32TimerActions.kt:66-90,431-445`）。
- alarm の `LARGE_INTEGER`、wait handle 配列、`FLASHWINFO`、QPC 構造体、settings の DWORD/UTF-16 buffers はいずれも同期 API 呼出し内の `SCOPED`。ByteArray は同期 `ReadFile`/`WriteFile` の間だけ `PINNED_SYNC` であり、スコープ外へ逃げるポインターは見つからない（`Win32AlarmScheduler.kt:80-84,111-127`; `Win32TimerActions.kt:419-445`; `Win32SettingsStore.kt:60-70,94-106,123-136`; `PlatformTime.kt:13-20`）。

# Rejected

- **「東雲 ROM glyph が Unicode 非対応だから設定保存も ANSI にすべき」**は棄却。内部描画方式とは別に、Windows path は `CreateDirectoryW/CreateFileW/GetEnvironmentVariableW`、保存内容は UTF-8 `encodeToByteArray/decodeToString` であり、`APPDATA` やタスク名の非 ASCII 文字を保持する境界として正しい（`Win32SettingsStore.kt:38-49,77-88,123-136`）。UTF-16 surrogate code unit を `StringBuilder` へ順に戻す実装も pair を保持する。上記の固定 512 問題は Unicode 問題ではなく長さ処理の問題。
- **`memScoped` から pointer が逃げる疑い**は棄却。対象各ファイルでは永続 native struct を `memScoped` で作って field に保存する処理、`nativeHeap` の未解放、unpinned Kotlin array の C 渡しは見つからない。唯一の長寿命 Kotlin/C bridge は `StableRef` であり、問題は Confirmed 3/4 の停止順と作成失敗時処理に限定される。
- **通常経路で `shutdown()` が二重実行される疑い**は棄却。登録失敗、window 作成失敗、通常 message-loop 終了は排他的 return 経路で、それぞれ一度だけ `host.shutdown()` へ達する（`Win32Host.kt:238-261,275-307`）。`shutdown()` 自体は冪等ではないが、現行呼出グラフに具体的な二重 close 経路はない。
- **QPC/Frequency が Windows 10/11 で未対応という疑い**は棄却。API 選択と wall clock との用途分離は妥当。戻り値未確認は現行対応 OS の具体的失敗経路として重大指摘にしない（`Win32TimerActions.kt:431-445`; `PlatformTime.kt:13-20`）。
- **`MessageBeep` が毎クリック鳴る疑い**は棄却。Windows 実装は `IMPACT` の場合だけ鳴らすが、現 common scene の haptic 呼出しは `CLICK`/`TICK` であり `IMPACT` の到達呼出しを確認できない（`Win32TimerActions.kt:303-307`; `InputPrimitives.kt:16-20`; `rg "EngineHaptics.IMPACT"`）。到達経路なしでは実害として扱わない。

# Unknown

- Windows 版で running session 中、`ES_SYSTEM_REQUIRED` を全期間保持するのが製品意図かはコードだけでは確定できない。これは自動 sleep を防ぐ一方、[`SetWaitableTimer`](https://learn.microsoft.com/en-us/windows/win32/api/synchapi/nf-synchapi-setwaitabletimer) の仕様上、`fResume = FALSE` と相対 due time は Windows 8+ の低電力状態中に進まず、ユーザーが明示 sleep した PC を起こさない（`Win32Power.kt:11-20`; `Win32AlarmScheduler.kt:76-84`）。「PC を寝かせない」「sleep は許すが wake はしない」「期限に wake する」のどれが受入条件か確認が必要。
- `strictMode`、`tickEnabled`、`vibeIntensity` は Windows では保存・UI 表示されるだけで、timer pump や power/audio 制御に使われない（`Win32TimerActions.kt:38-41,151-157,240-296`）。Android では strict foreground blocking、tick tone、vibration に使う（`FocusService.kt:127-143,375-391,458-476`）。Windows で非対応設定を意図的に表示しているのか、機能欠落なのかは製品判断が必要なため、確定バグにはしていない。
- 設定ディレクトリを roaming `%APPDATA%\\TimeBox` にする方針自体はコメントどおりで一貫する（`Win32SettingsStore.kt:33-36,113-121`）。将来 MSIX/package、portable 配布、LocalAppData を望むかは監査対象コードから決められない。
- `appendPair()` が値の CR を削除し LF を space に変えるのはデータ変形だが、現 UI の task field が単一行という意図と整合する可能性が高い（`Win32SettingsStore.kt:154-159`; `Scenes.kt:401-420`）。複数行 task を保存する要件がないため欠陥認定しない。

# Recommendation

優先順位は次のとおり。

1. `handleTickEvent` の予約所有を一本化し、engine が次区間を予約した後に Windows wrapper が解除しないことを最優先で保証する。gentle 自動継続、dual.5 small/mid、sequence/calendar/dual-sequence の各継続区間で次の OS alarm が残ることを確認する。
2. alarm 予約に世代または deadline token を持たせ、`WM_APP_ALARM` 受信時に「現在予約と一致する通知」だけを同期割込みとして採用する。停止→再開、期限と frame tick の同時競合、gentle 自動遷移を受入条件にする。
3. waiter shutdown は thread が終了したことを確認してから timer/event/`StableRef` を解放する。timeout/failure なら待機対象を閉じない。`CreateThread` 失敗時はその場で作成した `StableRef` を dispose し、alarm terminal を明示的な失敗状態にする。
4. `skipTimer()` 後の `isActive/isRinging` を基に audio、QPC baseline、`Win32Power`、alarm の状態を start/stop/dismiss と同じ規則へ同期する。
5. 設定は同一ディレクトリの一時ファイルへ全量を書き、BOOL と byte count を検証してから置換する。UI thread の各キー入力から同期 I/O を外し、連続変更をまとめる。保存上限と読込上限は同じ契約にする。
6. `currentPresets()` の結果を設定変更時だけ再構築し、`getUiState()` は既存 state の参照を組み立てるだけにする。将来の cleanup では OS terminal と Windows 用 application controller を少なくともクラス責務として分け、platform wrapper に domain policy を追加しない。

本報告で製品コード・試験・`STATE.md` は変更していない。
