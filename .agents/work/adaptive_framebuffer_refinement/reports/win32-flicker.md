# Win32 flicker audit

## Scope

- 読み取り対象は、現worktreeのWin32 entry point、ウィンドウclass登録、message loop、`WM_SIZE` / `WM_PAINT` / background erase、indexed framebuffer展開、GDI present経路に限定した。
- production codeは変更していない。
- 到達経路は次の一本である。

```text
Main.kt:main
  -> runWin32Terminal
     -> RegisterClassExW / CreateWindowExW / ShowWindow / UpdateWindow
     -> PeekMessageW / DispatchMessageW
     -> SceneManager.render
     -> Win32Host.present
        -> Win32FramebufferPresenter.expand
        -> GetDC
        -> PatBlt(BLACKNESS)
        -> StretchDIBits
        -> ReleaseDC
```

根拠: `shared-engine/src/winMain/kotlin/Main.kt:1-3`、`shared-engine/src/winMain/kotlin/com/example/timeboxvibe/engine/win/Win32Host.kt:222-313`、同`:170-208`、`shared-engine/src/winMain/kotlin/com/example/timeboxvibe/engine/win/Win32FramebufferPresenter.kt:18-28`。

## Confirmed

### 1. 毎frame、完成画像の前にclient全体をfront buffer上で黒くしている

- main loopは約16 ms周期で`SceneManager.render(...)`の直後に`host.present()`を呼ぶ。根拠: `Win32Apis.kt:28`、`Win32Host.kt:292-309`。
- `present()`はwindow DCを直接取得し、最初に`PatBlt(..., BLACKNESS)`でclient全体を黒くし、その後に別のGDI callでviewportだけへ`StretchDIBits`する。根拠: `Win32Host.kt:170-207`、特に`:175`、`:186`、`:188-202`。
- offscreen DC、DIB section、client-sized composed bufferのいずれもこの経路に存在しない。したがって黒い中間状態と完成画像は、同じfront bufferへの別々の可視操作であり、1回のatomic presentではない。
- 報告された継続的なflickerの直接原因である。letterboxの有無とは別問題で、現在はletterboxを毎frame塗り直すために完成画像まで毎frameいったん消している。

### 2. `WM_PAINT`では、frameを描いた後に`BeginPaint`がbackground eraseを実行し得る

- classは`CS_HREDRAW | CS_VREDRAW`を持ち、非nullの黒brushを`hbrBackground`へ登録する。根拠: `Win32Host.kt:231-244`、特に`:233`、`:240`。
- `WM_ERASEBKGND` handlerは存在しないため、`win32WndProc`末尾の`DefWindowProcW`へ到達する。根拠: `Win32Host.kt:343-407`。
- `WM_PAINT` handlerの実際の順序は、(1) `host.present()`が`GetDC`でframeを描く、(2) その後`BeginPaint`、(3) `EndPaint`である。根拠: `Win32Host.kt:354-361`。
- Win32仕様上、erase-mark付きupdate regionに対する`BeginPaint`は`WM_ERASEBKGND`を発生させ、class background brushがあれば戻る前にそのbrushでupdate regionを消す。現コードではこれが**完成frameを描いた後**に起き、次のmain-loop presentまで黒領域を残せる。
- `CS_HREDRAW` / `CS_VREDRAW`はwidth/height変更時にclient全体をinvalid化するため、resize/maximize時にこの経路の影響範囲がclient全体になる。Microsoft仕様: [BeginPaint](https://learn.microsoft.com/en-us/windows/win32/api/winuser/nf-winuser-beginpaint)、[WM_ERASEBKGND](https://learn.microsoft.com/en-us/windows/win32/winmsg/wm-erasebkgnd)、[Resized Windows](https://learn.microsoft.com/en-us/windows/win32/gdi/resized-windows)。

### 3. `WM_PAINT`とmain loopが同じcompleted framebufferを別経路から二重presentする

- `WM_PAINT`はmessage drain内の`DispatchMessageW`から`host.present()`を呼ぶ。根拠: `Win32Host.kt:281-289`、`:354-361`。
- message drain後、同じthreadのmain loopもrenderして`host.present()`を呼ぶ。根拠: `Win32Host.kt:297-307`。
- 両方ともpaint DCではなく`GetDC`を取得し、両方とも黒全消去→bitmapの2操作を行う。これはdata raceではないが、不要なfront-buffer消去/present回数を増やし、flickerを悪化させる。

### 4. primitive framebufferがresizeされた直後、まだsceneが描かれていないzero frameを`WM_PAINT`がpresent可能

- `WM_SIZE`は即座に`applyResizeFromHwnd`→`applyClientSize`へ進む。根拠: `Win32Host.kt:316-325`、`:343-349`。
- primitive寸法が変わると`SoftwareEngineCanvas.resize`→`IndexedFramebuffer.resize`が新しいzero-filled `ByteArray`を作る。根拠: `Win32Host.kt:150-167`、`SoftwareEngineCanvas.kt:21-23`、`IndexedFramebuffer.kt:20-27`。
- その後、同じmessage drain中に`WM_PAINT`が来れば、次の`SceneManager.render`より先にそのzero frameを展開して表示する。根拠: `Win32Host.kt:281-306`と`:354-361`。
- 現在の固定profileでは主にwide/tall切替時に発生する。adaptive framebuffer化でresize頻度が上がる場合、completed-frame guardなしではこの経路も頻発し得る。

### 5. C interop lifetimeは、このflickerの原因ではない

- `WNDCLASSEXW`、`BITMAPINFO`、`PAINTSTRUCT`は同期callの範囲内だけで使う`memScoped`所有である。根拠: `Win32Host.kt:177-204`、`:228-248`、`:354-360`。
- `IntArray`は同期`StretchDIBits`中だけ`usePinned`される。根拠: `Win32Host.kt:187-203`。
- `staticCFunction(::win32WndProc)`はKotlin stateをcaptureせず、process-lifetimeの`activeHost`を参照する。根拠: `Win32Host.kt:220-225`、`:234`、`:343-344`。
- よってdangling pointer、escaped pin、callback captureは確認されず、今回の症状はpaint/present順序の問題である。

## Rejected

- **palette展開がflickerを作る説:** `Win32FramebufferPresenter.expand`はcompleted indexed frameを既存`IntArray`へ順次展開し、展開完了後にのみGDIへ渡す。同thread同期実行で、GDIが展開途中を読む経路はない。根拠: `Win32FramebufferPresenter.kt:18-28`、`Win32Host.kt:174-203`。
- **framebufferをplatformが同時更新する説:** render、expand、present、message dispatchは同じWin32 main thread上で直列実行される。描画bufferのproducer/consumer raceは確認されない。根拠: `Win32Host.kt:281-309`。
- **DPI metadataが直接flickerを作る説:** `WM_DPICHANGED`はclient寸法を再取得するだけで、描画中に別threadから倍率を変更しない。根拠: `Win32Host.kt:350-353`、`:316-325`。
- **`StretchDIBits`のsource pointer lifetime違反説:** pointerはcallが戻るまでpinされ、native側に保存されない同期使用である。根拠: `Win32Host.kt:187-203`。

## Unknown

- ユーザーが見ているflickerの割合が、通常60 Hz present中の黒全消去によるものか、resize/maximize時のbackground erase/zero frameによるものかは静止画だけでは時間的に分離できない。ただし両経路とも現コード上で実在し、通常時の原因はConfirmed 1、window invalidation時の原因はConfirmed 2/4である。
- `StretchDIBits`の戻り値を検査していないため、特定driver/DCでのblit失敗は観測できない。根拠: `Win32Host.kt:188-202`。ただし失敗を仮定しなくても上記flicker経路は成立するため、第一修正には不要。

## Recommendation

最小のdumb-terminal修正は、Win32側の責務を「completed frameを一度だけ表示する」に限定し、次の順序で行う。

1. **1 frame = 1 visible blitにする。** adaptive framebufferがclient全域を覆うなら、毎frameの`PatBlt(BLACKNESS)`を削除し、client全域への`StretchDIBits`または`SetDIBitsToDevice`を1回だけ行う。letterboxを残す場合は、client-sized persistent BGRA backbufferへblack barsとviewportを合成してからwindowへ1回だけblitする。window front bufferへ`PatBlt`→`StretchDIBits`を連続実行する形は残さない。
2. **`WM_PAINT`の契約を正す。** `BeginPaint`を最初に呼び、得た`PAINTSTRUCT.hdc`を`presentTo(hdc)`へ渡し、最後に`EndPaint`する。`WM_PAINT`内で`GetDC`を別取得しない。
3. **OS background eraseを無効化する。** `wc.hbrBackground = null`とし、`WM_ERASEBKGND`は描画せずnonzeroを返す。completed framebufferがclient backgroundまで所有するため、これはlayout/rendering判断をWin32へ移さずterminal責務を保つ。
4. **未完成resize bufferを表示しない。** framebuffer寸法変更時は`frameReady = false`、`SceneManager.render`完了時に`true`とし、`WM_PAINT`は新寸法のcompleted frameがない間はpresentしない。直前の画面を保持したまま次のmain-loop frameへ進める。
5. **不要なfull-client invalidationを外す。** continuous main loopが全frameを再描画するため、class styleは`CS_OWNDC`だけで足り、`CS_HREDRAW | CS_VREDRAW`を外せる。これは2-4を置き換える修正ではなく、resize時の重複`WM_PAINT`を減らす補助である。

推奨する実装境界:

```text
commonMain: completed indexed framebuffer + presentation mapping
Win32 terminal: palette byte-order expansion + one GDI blit + exact inverse input forwarding
```

scene/UI/layout/scale policyをWin32へ追加する必要はない。

```text
PLATFORM FIREWALL CHECK:
Platform: Win32
Allowed responsibility: completed framebuffer presentation, OS paint lifecycle, input forwarding
Core responsibility preserved: scene/UI/layout/palette meaning remain commonMain-owned
Leakage found: current flicker fixにはcore policy leakage不要
Result: PASS for the recommended boundary
```

```text
C INTEROP BOUNDARY CHECK:
File: Win32Host.kt
Pointer crossing: BITMAPINFO, PAINTSTRUCT, WNDCLASSEXW, pinned pixel IntArray
Ownership class: SCOPED / PINNED_SYNC
Lifetime owner: current Win32 call frame
Shutdown/free path: no persistent pointer introduced by the recommended minimal fix
Result: PASS for current pointer lifetimes; persistent offscreen GDI objectsを選ぶ場合のみexplicit DeleteObject/DeleteDCが追加で必要
```
