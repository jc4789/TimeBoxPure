# Objective
`shared-engine/src/winMain` の Windows プラットフォームコードを読み取り専用で監査し、意図を確認したうえで実在する不具合と不適切な実装を根拠付きで報告する。

# Constraints
- 製品コードを変更しない。
- `commonMain` は監査対象外。
- Android ラッパーは意図確認の参照に限る。
- 東雲系 16x16 ROM グリフの内部表現を Unicode 化しない。文字入力・Win32 文字列境界だけ必要時に確認する。
- 指定された既存設計・ダミー端末原則を別設計へ置換しない。
- 推測だけの指摘を報告しない。

# Plan
- [x] Windows と Android のラッパー構造、ビルド設定、呼び出し関係を把握する。
- [x] Win32 ライフサイクル、入力、DPI、描画、色変換を監査する。
- [x] C interop、音声、電源、永続化、アラームを監査する。
- [x] ビルドと既存検証を読み取り専用で実行できる範囲で確認する。
- [x] 重要指摘をルートで再確認し、重大度・根拠・影響・修正方向をまとめる。

# Confirmed
- 監査対象は `shared-engine/src/winMain`。Android 参照先は `app/src/main/java/com/example/timeboxvibe`。（ユーザー指定）
- Windows 対象には Kotlin/Native Win32、miniaudio C 橋渡し、描画・設定・電源・アラーム実装がある。（`rg --files`）
- 作業開始時の `git status --short` は空。（2026-08-15）
- `:shared-engine:linkDebugExecutableWin -x opnaAudit` は成功。既存試験は実行していない。（2026-08-15）
- Windows の `BI_RGB` 転送では画素上位バイトが未使用だが、`setDrawAlpha` はそこへ値を入れるだけ。Android は描画時にアルファ合成するため半透明描画が一致しない。（`Win32EngineCanvas.kt:56-57,189-203`, `AndroidEngineCanvas.kt:63-69`）
- 次区間を予約して `IntervalComplete` を返した直後、Windows が予約を無条件解除する。（`TimerEngine.kt:532-630`, `Win32TimerActions.kt:315-328`）
- `WM_APP_ALARM` に予約世代がなく、旧通知を現在の active 区間へ適用できる。（`Win32AlarmScheduler.kt:108-125`, `Win32TimerActions.kt:92-97`）
- `skipTimer()` は UI から停止中・鳴動中にも到達できるが、音声・電源・QPC の副作用を同期しない。（`Win32TimerActions.kt:130-132`, `TimerEngine.kt:648-689`, `Scenes.kt:531-559`）
- 設定保存は最終ファイルを先に切り詰め、書込み成否と完全長を確認しない。（`Win32SettingsStore.kt:43-75`）
- 入力捕捉、DPI 推奨矩形、ホイール差分、最小化時の描画停止、入力時のフレーム期限維持に具体的な Win32 契約違反または実装欠陥がある。（`Win32Host.kt`）
- 音声装置の初期化・開始失敗は呼出側へ伝達されず、アラームが無音でも検出不能。（`Win32Audio.kt:177-205`, `timebox_miniaudio.c:73-92`）

# Rejected
- 内部グリフ表現全体への Unicode 要件適用：プロジェクトの文字方式と監査範囲に反する。
- DIB の赤青順、パレット改訂キャッシュ、同期 `usePinned`、音声 `StableRef` の通常解放順、可変 audio frame 分割、単一 GUI thread の入力キューは不具合ではない。
- 未知の曲キーによる旧音継続は公開関数上の差だが、現 UI・保存・アラーム経路ではカタログ補正により到達不能。

# Unverified
- `strictMode`、`tickEnabled`、`vibeIntensity` の Windows 非対応が意図的かは製品判断が必要。
- Windows 実機での DPI 移動、装置喪失、最小化、画素差分の再現試験は未実行。
- `WM_CHAR` の補助平面文字・IME 要件は東雲 ROM glyph と製品入力仕様に依存するため、一般 Unicode 不具合として認定しない。

# Next
読み取り専用監査結果を、重大度順・根拠付きでユーザーへ報告する。製品コードは変更しない。
