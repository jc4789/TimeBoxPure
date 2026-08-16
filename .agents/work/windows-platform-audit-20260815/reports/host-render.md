# Scope

- 読み取り対象は `shared-engine/src/winMain/kotlin/Main.kt`、`shared-engine/src/winMain/kotlin/com/example/timeboxvibe/engine/win/Win32Host.kt`、`Win32Apis.kt`、`Win32EngineCanvas.kt` に限定した。
- 意図の比較にだけ `app/src/main/java/com/example/timeboxvibe/MainActivity.kt`、`ui/main/Pc98SurfaceView.kt`、`platform/android/AndroidEngineCanvas.kt` を用いた。`commonMain` は読まず、監査していない。
- 製品コード変更、テスト追加、画像生成は行っていない。今回は変更後監査ではないため、画素差分も実行していない。
- 基準別の概況は、配置単位と動的表示寸法は合格、パレット変換はアルファ以外合格、プラットフォーム防火壁は合格、DPI処理・入力契約・描画合成・フレーム待機・例外境界・高頻度割り当ては不合格である。

# Confirmed

- **重大候補：`setDrawAlpha` は Windows では合成になっておらず、半透明描画が常に不透明になる。** 意図は Android 側の `setDrawAlpha` と `Paint` による描画から確認できる（`AndroidEngineCanvas.kt:63-69,81-107,123-136`）。Windows 側はアルファを DWORD の上位バイトへ置くだけで、既存画素へ直接上書きする（`Win32EngineCanvas.kt:56-58,189-193,211-213,216-232`）。提示時は 32ビット `BI_RGB` と `SRCCOPY` であり、Microsoft の仕様上、この形式の上位バイトは未使用である（`Win32Host.kt:174-196`、[BITMAPINFOHEADER](https://learn.microsoft.com/en-us/previous-versions/dd183376%28v%3Dvs.85%29)）。したがって、半透明の前景色は背景との混色ではなく前景RGBの不透明上書きになる。

- **重大候補：ドラッグ開始後にマウスをウィンドウ外へ出すと、対応する移動・解放を失う。** `WM_LBUTTONDOWN` で `SetCapture` せず、`WM_LBUTTONUP` で `ReleaseCapture` せず、捕捉喪失時のキャンセルもない（`Win32Host.kt:357-370`）。Windows は未捕捉時にはカーソル下のウィンドウへメッセージを送るため、外で離すとエンジンには押下だけが残る（[Using Mouse Input](https://learn.microsoft.com/en-us/windows/win32/inputdev/using-mouse-input)）。Android は `ACTION_CANCEL` を含めて入力列を必ず終端できる形である（`Pc98SurfaceView.kt:110-133`）。

- **重大候補：Win32 の C コールバック境界から Kotlin 例外が出ない保証がない。** `staticCFunction(::win32WndProc)` の先で、フレームバッファとレンダラーの割り当て、描画、エンジン入力処理を直接呼んでいる（`Win32Host.kt:228,337-401`、`Win32Host.kt:142-164`、`Win32EngineCanvas.kt:37-51`）。少なくともサイズ変更時の配列割り当て失敗や下位呼び出しの例外は境界まで到達できる。Kotlin/Native 公式資料は `staticCFunction` から例外を投げないよう明記し、投げた場合の副作用は非決定的としている（[Mapping function pointers from C](https://kotlinlang.org/docs/mapping-function-pointers-from-c.html)）。

- **中：入力メッセージがフレーム待機を解除すると、期限前でも次フレームを描く。** 各フレーム後に `MsgWaitForMultipleObjects` を呼ぶが、その返値を区別せず呼び出し元へ戻る（`Win32Host.kt:303,321-334`）。`QS_ALLINPUT` でマウス移動などがタイマーより先に到着すると、外側ループは即座に次の更新・描画へ進む（`Win32Host.kt:275-301`）。連続入力時は入力周波数まで描画率とCPU使用率が上がり、16ミリ秒のフレーム期限を保てない。Android 参照は入力到着と無関係に各フレームの残時間を待つ（`Pc98SurfaceView.kt:243-252`）。

- **中：最小化しても、古いフレームバッファを毎フレーム更新・提示し続ける。** `WM_SIZE` は最小化状態を示す `wParam` を見ず、幅または高さがゼロなら寸法更新だけを中止する（`Win32Host.kt:142-145,340-343`）。`running`、旧 `canvas`、旧論理寸法は残るため、主ループの更新・描画・`GetDC`・`StretchDIBits` は継続する（`Win32Host.kt:291-303,167-201`）。Windows の省電力指針は非表示・背景時にCPUを起こすタイマーや不要な資源使用を避けるよう求めている（[Power consumption improvements](https://learn.microsoft.com/en-us/windows/apps/develop/performance/power)）。アラームや時刻更新の必要性と、画面描画の必要性は分離すべきである。

- **中：`WM_DPICHANGED` の推奨ウィンドウ矩形を完全に捨てている。** ハンドラーは `lParam` を使わず、現在のクライアント矩形を再取得するだけである（`Win32Host.kt:344-347,310-319`）。Microsoft は推奨矩形を適用しないと、画面間移動時のカーソル相対位置ずれや再帰的DPI変更を招き得ると明記している（[High DPI Desktop Application Development](https://learn.microsoft.com/uk-ua/windows/win32/hidpi/high-dpi-desktop-application-development-on-windows)）。独自の物理寸法維持方針が必要なら、単に無視するのではなく `WM_GETDPISCALEDSIZE` 側で契約を表す必要がある。

- **中：高解像度ホイールの部分差分を毎回捨てる。** `signedHighWord(...) / 120` がゼロなら即時復帰し、余りを保存する状態がない（`Win32Host.kt:404-420`）。30ずつ4回届く装置では、合計が1ノッチでも4回とも何も起きない。Microsoft は120未満の差分を処理するか、未使用分を蓄積するよう説明している（[Miscellaneous Mouse Operations](https://learn.microsoft.com/en-us/windows/win32/learnwin32/other-mouse-operations)）。なお、1ノッチを `3 * CANONICAL_UI_UNIT` とする配置量自体は、指定された16×16単位則に合っている（`Win32Host.kt:408`、`Win32Apis.kt:43-45`）。

- **中：`WM_PAINT` の描画順が逆で、提示後に背景消去が走り得る。** 現在は `host.present()` を `GetDC` で先に実行し、その後で `BeginPaint` と `EndPaint` を呼ぶ（`Win32Host.kt:348-355,167-201`）。ウィンドウクラスには黒ブラシがあるため（`Win32Host.kt:234`）、無効領域に消去指定があれば `BeginPaint` が提示後に黒で消す。Microsoft の契約は描画を `BeginPaint` と `EndPaint` の間で、返された更新領域用DCへ行う形である（[WM_PAINT](https://learn.microsoft.com/en-us/windows/win32/gdi/wm-paint)、[BeginPaint](https://learn.microsoft.com/en-us/windows/win32/api/winuser/nf-winuser-beginpaint)）。特に対話的サイズ変更中は、黒い領域やちらつきが残り得る。

- **中：フレームタイマーの作成・設定・待機失敗経路が、全速空回りまたは無期限停止になる。** 二つの `CreateWaitableTimerExW` が両方失敗すると `frameTimer` は `null` となり、待機関数は何も待たず戻る（`Win32Host.kt:92-97,321-327`）。`SetWaitableTimer` の失敗も無視してから無限時間待つため、未作動タイマーでは新しい入力が来るまでGUIスレッドが止まり得る（`Win32Host.kt:327-334`）。`MsgWaitForMultipleObjects` の `WAIT_FAILED` も区別していない。各APIは明示的な失敗返値を持つ（[SetWaitableTimer](https://learn.microsoft.com/en-us/windows/win32/api/synchapi/nf-synchapi-setwaitabletimer)、[MsgWaitForMultipleObjects](https://learn.microsoft.com/ar-sa/windows/win32/api/winuser/nf-winuser-msgwaitformultipleobjects)）。

- **中：Windows の線・円ラスター化は Android 参照と同じ太さ契約にならない。** Android は論理 `strokeWidth` を `Paint` へ渡し、外側の `canvas.scale(scaleFactor, scaleFactor)` で物理拡大する（`AndroidEngineCanvas.kt:89-97,123-136`、`Pc98SurfaceView.kt:286-293`）。Windows の線は `strokeWidth * scale == 2` のとき半径1の円盤を各点へ置くため、中心を含む物理3画素幅になる（`Win32EngineCanvas.kt:82-96,235-247`）。円も `thickness == 2` に対して半径1の円盤を置き、同じ偶数幅ずれがある（`Win32EngineCanvas.kt:123-135`）。破線円は弧長ではなく中点円の反復回数で一度だけ位相を進め、その判定を8対称点へ同時適用する（`Win32EngineCanvas.kt:279-315`）ため、Android の `DashPathEffect` と形が一致しない。これは独自描画の好みではなく、参照端末との数式上の差である。

- **中：対話的サイズ変更が大容量配列とレンダラーを繰り返し作る。** すべての `WM_SIZE` でクライアント寸法を適用し（`Win32Host.kt:340-343`）、1画素でも変われば `physicalWidth * physicalHeight` の新しい `IntArray` を作る（`Win32EngineCanvas.kt:37-51`）。さらに同じキャンバスを包む `ScaledProceduralRenderer` も毎回作り直す（`Win32Host.kt:154-164`）。サイズ変更メッセージが連続するドラッグ中には、大量の一時メモリ、GC圧力、停止時間となり、前記の無防備なCコールバック例外経路も悪化させる。

- **軽微：高頻度経路に毎フレームのネイティブ一時構造と不要な状態設定がある。** `present()` は毎回 `memScoped`、`BITMAPINFO`、`GetDC`、`SetStretchBltMode`、ピン留めを構成する（`Win32Host.kt:167-201`）。待機側も毎回 `LARGE_INTEGER` と1要素ハンドル配列を作る（`Win32Host.kt:321-334`）。ピン留めの寿命は正しいが、寸法が変わるまで不変の DIB ヘッダーや待機用領域まで毎フレーム作る必要はない。なお、`StretchDIBits` の転送元と転送先寸法は同一なので、現在の `SetStretchBltMode` は結果へ寄与しない（`Win32Host.kt:180-191`）。

- **軽微：クライアント座標を符号なしワードとして展開している。** `WM_LBUTTON*` と `WM_MOUSEMOVE` は `lowWord`、`highWord` を使い、負座標を65536近辺へ変える（`Win32Host.kt:357-369,423-424`）。現状は捕捉していないため通常はウィンドウ外座標自体を受け取れないが、上記の捕捉修正を正しく行うと直ちに顕在化する。Microsoft は `GET_X_LPARAM` と `GET_Y_LPARAM` 相当の符号付き展開を使うよう示している（[Mouse movement](https://learn.microsoft.com/nb-no/windows/win32/learnwin32/mouse-movement)）。

# Rejected

- **一般的な Unicode 境界不具合は認定しない。** クラス登録、ウィンドウ作成、タイトル、カーソル、モジュール取得はワイド文字版APIを使っている（`Win32Host.kt:28-65,222-267`、`Win32Apis.kt:58-78`）。描画文字集合は16×16の東雲ROMグリフという前提なので、一般表示のUnicode化は監査範囲ではない。文字入力の補助平面・IMEについてだけは `Unknown` に残す。

- **DIB の赤青順は正しい。** `convert12BitToDib` は整数値を `0x00RRGGBB` として作り（`Win32EngineCanvas.kt:195-204`）、リトルエンディアン32ビットDIB上ではメモリ順が青・緑・赤・未使用になる。`BI_RGB` の契約と一致する（`Win32Host.kt:174-195`）。

- **パレット変換を画素ごとに再計算してはいない。** 16項目の `cachedNativePalette` を改訂番号が変わったときだけ同期し、各描画は添字参照を使う（`Win32EngineCanvas.kt:23-24,178-193`）。色の意味は共通側に残り、Windows 側はネイティブバイト順への変換だけを担っている。

- **`usePinned` の寿命違反はない。** `pixels` のアドレスは同期的な `StretchDIBits` 呼び出しの間だけ保持され、ブロック外へ保存されない（`Win32Host.kt:181-197`）。`RECT`、`POINT`、`PAINTSTRUCT`、待機構造も各同期API呼び出し内の `memScoped` に留まる（`Win32Host.kt:310-335,348-354,404-420`）。

- **`staticCFunction` が状態を捕捉する違反はない。** `win32WndProc` は非捕捉のトップレベル関数で、単一ホストは `activeHost` が強く保持する（`Win32Host.kt:214-220,228,337-338`）。この単一ウィンドウ設計では `StableRef` が必須ではない。問題は状態参照方法ではなく、前記の例外流出である。

- **入力キューの無施錠は競合ではない。** WndProc、`PeekMessageW`、`drainTouches`、更新処理は同じGUIスレッドで直列に実行される（`Win32Host.kt:108-140,272-300,337-401`）。Android の描画専用スレッドとは違い、ここへ Android と同じロックを機械的に足す根拠はない。

- **16ミリ秒という周期自体は Windows 固有の不具合ではない。** `FRAME_NANOS = 16_000_000` は Android 参照の `FRAME_SLEEP_MS = 16` と一致する（`Win32Apis.kt:31`、`Pc98SurfaceView.kt:385-386`）。問題は周期値ではなく、入力で期限前に待機を終了する制御である。

- **固定画面寸法・固定縦横比・任意配置画素の混入はない。** 論理寸法はクライアント物理寸法と検証済みDPIから `DisplayScalePolicy` で導出し、全縦横比をそのまま渡す（`Win32Host.kt:142-164`、`Win32Apis.kt:70-84`）。ホイール移動量も名前付きセル数と `CANONICAL_UI_UNIT` から導出する（`Win32Host.kt:408`）。

- **背景ブラシはフレームごとの漏れではない。** `CreateSolidBrush` のハンドルは登録済みウィンドウクラスが所有し、クラス解除時にシステムが削除する（`Win32Host.kt:225-242`、[WNDCLASSEXW](https://learn.microsoft.com/en-us/windows/win32/api/winuser/ns-winuser-wndclassexw)）。プロセス終了時には登録クラス自体も解除される。再入可能なホストやDLL化を要求する場合の明示的 `UnregisterClassW` は別の設計課題だが、現在の単発実行だけから永続リークとは判定しない。

- **プラットフォーム防火壁の責務漏れは、この4ファイルには見つからない。** Windows 側はウィンドウ、DPI、時刻刻み、入力転送、フレームバッファ提示、ネイティブ色変換を担うだけで、シーン状態、画面遷移方針、UI配置、パレットの意味は所有していない（`Win32Host.kt:269-300`、`Win32EngineCanvas.kt:10-13,178-204`）。

# Unknown

- `setDrawAlpha` が現在どの場面で何回使われるかは `commonMain` を読まない制約上、未確認である。実装上アルファが効かないことは確定だが、現行画面での露出範囲と重大度の最終確定には既知場面の画素差分が必要である。
- Windows と Android の線・円・破線について、期待ハッシュ、変更画素数、最大ずれ量は未計測である。数式差は確定しているが、画素差分用の既知場面・基準画像は今回の対象外である。
- `WM_CHAR` はUTF-16コード単位をそのまま投入する（`Win32Host.kt:384-390`）。補助平面文字はサロゲート対の2入力になるが、ゼロバイト東雲ROMフォントと入力仕様がそれを受理すべきかは、共通入力契約を読まない限り判定できない。BMP内の通常文字入力については、この点だけで不具合とはしない。
- `WM_DPICHANGED` で「論理ウィンドウ寸法を維持する」か「物理ウィンドウ寸法を維持する」かの製品方針は対象ファイルに記されていない。ただし、どちらを選ぶ場合も現在のように推奨矩形を黙って捨てるのではなく、WindowsのDPIサイズ契約として明示する必要がある。
- 最小化中もアラーム、音声、時刻更新のどこまでを継続すべきかは、この担当範囲外の各アダプターと製品方針に依存する。不要と確定できるのは画面の再描画とDIB提示である。
- `CS_OWNDC` を必要とする将来意図は不明である。現状は毎フレーム `GetDC` と `ReleaseDC` を行い、専用DCの性質を特に利用していない（`Win32Host.kt:227,167-201`）。即時不具合とはしないが、残す理由か削除判断が必要である。

# Recommendation

- 最優先で表示契約を一本化する。Windows 側で既存画素への整数アルファ合成を行うか、アルファを使わない契約へ正式に変える。線・円・破線の太さ、端点、破線位相も Android 参照と同じ画素則へ揃え、後続の修正時には既知場面のパレット・寸法・画素差分で確認する。
- 入力列を必ず終端できるよう、押下時の捕捉、解放時の解除、捕捉喪失時のキャンセル、符号付き座標展開を一組で設計する。ホイール差分は120未満の余りをホスト状態に保存する。
- フレーム期限を絶対時刻として保持し、入力で待機が解除された場合はメッセージだけ処理して残り時間を再待機する。タイマー作成・設定・待機の全失敗を区別し、有限時間の安全な代替待機へ落とす。最小化中は描画と提示を止め、必要なアラーム処理だけを低頻度またはイベント駆動で継続する。
- `WM_DPICHANGED` は推奨矩形を `SetWindowPos` へ適用するか、製品方針を `WM_GETDPISCALEDSIZE` で表す。`WM_PAINT` は先に `BeginPaint` し、そのDCを使ってDIBを提示してから `EndPaint` する。
- `win32WndProc` の外へ Kotlin 例外を出さない境界を設け、失敗時は診断を残して安全に終了する。`GetClientRect`、`ScreenToClient`、`StretchDIBits`、タイマーAPIの返値も、継続可能・機能停止・起動不能に分類する。
- サイズ変更ではレンダラーを再生成せず、フレームバッファの容量再利用またはサイズ変更の集約を検討する。不変のDIBヘッダーと待機用ネイティブ領域は明示的なホスト所有にして、終了時解放と対にする。
- 現在正しい部分、すなわちワイド文字版Win32 API、動的論理寸法、16×16配置単位、16色パレット改訂キャッシュ、同期呼び出し内だけのピン留め、単一GUIスレッド入力列は維持する。
