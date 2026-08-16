# Objective
監査で確認した指摘1〜14を、既存のKMP設計・16x16東雲ROMグリフ・ダミー端末原則を維持して修正する。

# Constraints
- 指摘1〜14以外へ変更範囲を広げない。
- Windows 10/11 の Win32 契約に従う。
- commonMain へ Win32/C API を漏らさない。
- ホットパスへ新しい割り当て、コレクション演算、コルーチンを入れない。
- コア色はパレット添字0..15のまま維持する。
- 外部資産、依存関係、設定形式を追加しない。
- 既存 Android 実装は意図確認だけに使い、変更しない。
- `commonMain` は一切変更しない。（ユーザーによる追加制約、2026-08-15）

# Plan
- [x] 現行契約、呼出経路、ビルド設定を再確認する。
- [x] アラーム・タイマー・音声・電源の状態遷移を修正する。
- [x] Win32ホスト・描画・入力・DPI・フレーム制御を修正する。
- [x] 設定保存とフレーム状態生成を修正する。
- [x] winMain内でWindows調停クラスとOSダミー端末を分離する。
- [x] ビルド、既存検証、画素差分相当、C境界、最終差分を確認する。

# Confirmed
- ユーザーは監査指摘1〜14の実装を明示的に許可した。（2026-08-15）
- 作業開始時、製品ソースに差分はなく、前回監査記録だけが未追跡である。（`git status --short` は `.agents/work/windows-platform-audit-20260815/` のみ）
- 指摘14は、ユーザー指定により commonMain へ移さず、winMain内で調停クラスとWin32 API端末を分ける。OSダミー端末からドメイン判断を除く。
- `Win32AlarmScheduler` はUI命令とネイティブタイマー所有を分離し、予約世代を `WM_APP_ALARM` に載せる。待機スレッド終了確認後だけハンドルとStableRefを解放する。
- `Win32TimerController` は `IntervalComplete` 後の次予約を解除せず、skip/dismiss後の音声・電源・QPC状態を同期する。
- miniaudio C境界は実際の `ma_result` を返し、Kotlin再生APIは成否を呼出側へ返す。永続アラーム失敗時はWindows警告音へフォールバックする。
- 設定は300ms集約後に専用スレッドへ渡し、一時ファイルの完全書込み・flush後に置換する。終了時は最終保存を排出してスレッド終了を待つ。
- プリセット一覧は言語・カスタム設定変更時だけ再構築し、`getUiState()`から全件再生成を除いた。
- `Win32TimerActions` を削除し、OS APIを持たない `Win32TimerController` と、`Win32Feedback`/audio/power/alarm/settings端末へ分離した。
- `:shared-engine:compileKotlinWin -x opnaAudit` は一度のprivate可視性修正後に成功した。（2026-08-15）
- `Win32EngineCanvas` は16色キャッシュを維持し、非不透明描画だけ既存DIB画素へ整数アルファ合成する。`clear` はAndroidと同じく不透明のまま。
- マウス押下でcaptureし、UP/capture喪失/cancel mode/最小化で入力列を終端する。捕捉中の座標は符号付きで展開する。
- フレーム期限を絶対QPC値で保持し、入力で待機解除された場合はメッセージ処理後に残時間を再待機する。
- 最小化中はactions/audio pumpを継続するが、scene renderとDIB提示を止める。
- `WM_DPICHANGED` の推奨RECTを `SetWindowPos` で適用後、新DPIでクライアント寸法を再導出する。
- ホスト・キャンバス変更後の `:shared-engine:compileKotlinWin -x opnaAudit` は成功した。（2026-08-15）
- 設定の300ms集約、設定オブジェクト生成、シリアライズ、UTF-8化、I/Oは書込みスレッド側へ移し、controller pumpの割り当てを除いた。
- miniaudio C/Hは GCC `-std=c11 -Wall -Wextra -Wpedantic -Wshadow -fsyntax-only` で警告なし。
- `:shared-engine:linkDebugExecutableWin -x opnaAudit` は成功した。
- 生成EXEを非表示起動し、`WM_CLOSE` で5秒以内に終了、exit code 0。alarm/settings workerの通常shutdownを通過した。
- `git diff --check` 成功。commonMainとAndroidの差分は0。ControllerにWin32/C importはない。
- 50%赤→青の整数アルファ式は `0x0080007F`。0/255分岐もコード上で無描画/直接上書き。

# Rejected
- 内部グリフや描画文字列全体のUnicode化：今回の指摘1〜14ではなく、既存方式を壊す。
- 新しいUI、依存注入基盤、シリアライズ基盤の導入：修正範囲を越える。
- OS非依存タイマー調停のcommonMain移設：ユーザーが明示的に禁止した。

# Unverified
- 既存のWin32キャンバス用画素差分ハーネスは存在せず、既知シーン全体の画素差分は未実行。新規テストは追加していない。
- 実機の音声装置失敗、DPIモニター移動、ウィンドウ外drag、最小化、期限直前alarm競合の対話的再現は未実行。

# Next
最終差分と状態を確認し、完了内容・検証・未確認事項をユーザーへ報告する。
