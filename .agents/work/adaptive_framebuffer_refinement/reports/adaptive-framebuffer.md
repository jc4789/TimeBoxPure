# Adaptive framebuffer 監査

## 結論

固定 `640x400 / 400x640` profileは、現在のindexed framebuffer構成を壊さずに、platformから渡されたclient/surface寸法そのものへ置換できる。

最小で清潔な方式は次の通り。

```text
platform surface/client width,height（事実のみ）
    -> SoftwareEngineCanvas(width, height)
    -> SceneManager logical bounds = framebuffer width,height
    -> 4-bit active indexed frameを同じwidth,heightで完成
    -> platform presenterが同寸法へ1回だけpresent

pointer(client x,y)
    -> 同寸法PresentationTransform
    -> primitive x,y（恒等写像）
    -> SceneManager / HUD / scene
```

`PresentationTransform`は削除・改名する必要がない。同じsource/output寸法でconfigureすればviewportはclient全域になり、入力も恒等写像になる。これによりletterboxは発生しない。platformは寸法を報告するだけで、layout policy、HUD placement、scene logicは引き続きcommonMainが所有する。

ただし、これだけでは魔法陣の相対サイズは小さくならない。Active Timerのradiusはplay areaの幅・高さの45%から求められているため、framebufferをclient寸法にしても画面占有率はほぼ同じである。改善されるのは、16px glyph、32px icon、borderなどが現在のframe全体の拡大で巨大化している問題である。

## 現在のreachable path

### Android resizeからpresentまで

1. `Pc98SurfaceView.surfaceChanged()` が実surfaceの `width,height` を受け取る（`app/.../Pc98SurfaceView.kt:51-52`）。
2. その寸法は現在 `PrimitiveDisplayProfile` によって640x400または400x640へ置換される（同:55-56、`PrimitiveDisplayProfile.kt:10-21`）。
3. 固定primitive寸法で `PresentationTransform`、`SoftwareEngineCanvas`、`AndroidFramebufferPresenter` が作られる（`Pc98SurfaceView.kt:57-60`）。
4. frameごとにcanvas寸法が `SceneManager.setLogicalBounds()` と `render()` に渡る（同:205-227）。
5. presenterはindexed frameをcached 16-color native paletteでARGB bufferへ展開し、viewportへnearestで描く。その直前にsurface全体を黒で消す（`AndroidFramebufferPresenter.kt:25-39,49-74`）。

### Android input

1. `MotionEvent` のsurface座標を `PresentationTransform.primitiveX/Y()` でprimitive座標へ変換する（`Pc98SurfaceView.kt:79-94`）。
2. preallocated touch queueを経て `SceneManager.update()` へ渡る（同:143-145,205-214）。
3. `SceneManager` は同じlogical boundsからHUD/content rectをresolveし、HUD hit-test後にsceneへ渡す（`SceneManager.kt:179-187,254-288`）。

### Win32 resizeからpresentまで

1. `WM_SIZE` / `WM_DPICHANGED` は `GetClientRect` のclient寸法を `applyClientSize()` へ渡す（`Win32Host.kt:316-324,343-353`）。
2. 現在はその寸法を固定profileへ変換し、logical bounds、transform、framebufferを更新する（同:150-167）。
3. main loopは同logical boundsでupdate/renderし、その後presentする（同:297-306）。
4. presenterはindicesをcached paletteからDIB pixelsへ展開する（`Win32FramebufferPresenter.kt:18-27,37-52`）。
5. `present()` はclient全域を `PatBlt(...BLACKNESS)` で消してから、別操作の `StretchDIBits` でviewportだけを描く（`Win32Host.kt:170-203`）。

### Win32 input

mouse座標はpresentに使う同じ `PresentationTransform` でprimitive座標へ変換され、preallocated queueを経てSceneManagerへ届く（`Win32Host.kt:118-147,363-375`）。wheelも同transformを使う（同:410-428）。

## スクリーンショットとの一致

添付Windows画像は約2560px幅のclient内で、描画域の左右に約185pxずつ黒帯がある。640:400（1.6）のsourceを約2560:1368（約1.87）のclientへaspect-fitした場合のpillarbox幅と一致する。

根拠は `PresentationTransform.configure()` がsource比率を維持してviewportを中央配置する実装（`PresentationTransform.kt:28-54`）と、Win32 presenterがviewport外を毎回黒く塗る実装（`Win32Host.kt:185-193`）である。

## 固定profileを外した時の具体的なbreakpoint

### 1. production参照

- Androidのimport、初期 `primitiveWidth/Height`、`surfaceChanged()` 内のprofile変換（`Pc98SurfaceView.kt:16,30-31,55-56`）。
- Win32のimportと `applyClientSize()` 内のprofile変換（`Win32Host.kt:11,154-155`）。
- `PrimitiveDisplayProfile.kt` 自体。

これ以外にproduction code内の640/400依存は見つからなかった。

### 2. 固定profile専用test

`PrimitiveDisplayProfileTest.kt:8-22` は「wide/tallのpixel countが同じ」「squareは640x400」という廃止対象の契約を直接assertしている。このtestは新仕様と両立しない。

`PresentationTransformTest.kt:10-40` のaspect-fit testはtransformの一般機能として残せるが、exact client pathを証明する恒等写像testは現在ない。

### 3. resize allocation

- framebuffer resizeは寸法変更時に `ByteArray(width * height)` を作り直す（`IndexedFramebuffer.kt:20-27`）。
- Android presenterもBitmapと`IntArray`を作り直す（`AndroidFramebufferPresenter.kt:42-47`）。Androidは現在surface変更時にrender thread一式を止めて再生成するため、frame hot loop内のallocationではない（`Pc98SurfaceView.kt:51-71`）。
- Win32 presenterも次のexpand時に`IntArray`を作り直す（`Win32FramebufferPresenter.kt:18-35`）。Win32ではlive resize中の各`WM_SIZE`を直ちに適用するため、messageが多数来ればByteArray/IntArrayを何度も交換する。

したがってexact client方式での通常frameはallocation-freeのままだが、Win32 live resizeだけはallocation churnが増える。最も小さい対処は、`WM_SIZE`では最新client寸法だけをprimitive fieldsに記録し、message drain後・次のrender前に一度だけ `applyClientSize()` するcoalescingである。これはlayout policyではなくOS寸法事実の転送である。

### 4. pixel workload

現在の固定frameは256,000 pixelsである。添付画像相当の2560x1368 exact frameは3,502,080 pixelsとなる。

- index framebuffer: 約3.34 MiB（現実装は1 pixel = 1 byte。packed 4-bitではない）
- native presenter buffer: 約13.36 MiB（1 pixel = 4 bytes）
- Androidはさらに同寸法ARGB Bitmapを持つ
- 全frameでindicesからnative pixelsへ全pixel展開するため、60fpsでは約2.10億pixel/秒

これは構造上正しく動くが、特に4K Android/WindowsでCPU負荷が大きくなる。今回の最小refinementでpacked 4-bit化、dirty rectangles、tile rendererを同時導入するとscopeが広がりspaghetti化しやすい。まずexact framebufferとsingle presentを成立させ、実機のframe pacingを確認するのが最小の境界である。

### 5. visual density

- glyphはROM bit 1個をframebuffer pixel 1個として描く（`ScaledProceduralRenderer.kt:110-134,148-181`）。exact framebufferではidentity textが実pixelで16x16になり、現在のwhole-frame magnificationは消える。
- HUDのiconは32x32 primitive pixelsである（`RetroHudComponent.kt:12-15,125-133`）。これも実pixel32x32になる。
- 一方、HUD領域はlogical width/heightの比率で計算される（`UiShellLayout.kt:57-76`）。panel自体の画面占有率は変わらない。
- Active Timer radiusは `min(playW*9/20, playH*9/20)` である（`ActiveTimerScene.kt:21-24,556-559`）。魔法陣の相対占有率も変わらない。

つまりexact framebufferは「全primitiveを何倍にも拡大していた問題」を正すが、「比例geometryそのものが大きい」という別問題は正さない。この2点を同じscale機構で再度まとめるべきではない。

## flickerの具体的な原因候補

最も強い候補はWin32の二段presentである。

```text
PatBlt(client全域, BLACKNESS)
StretchDIBits(viewport)
```

これは同じDCに別々の描画命令として毎frame実行される（`Win32Host.kt:185-203`）。画面更新の途中が見える環境では、黒clear後・frame blit前の状態を表示できる。さらに `WM_PAINT` でも通常loopとは別に `host.present()` が呼ばれる（同:354-360）。

exact framebufferではviewportがclient全域になるため黒帯clearは不要である。Win32はclient全域への一回のDIB blitだけにするのが最小修正である。Androidもfull destinationなら `canvas.drawColor(Color.BLACK)` は不要で、一枚のBitmapでsurface全域を覆える（`AndroidFramebufferPresenter.kt:31-39`）。

これで消えない場合に初めてframe pacing / tearingを別監査すべきであり、現時点で新しいtiming infrastructureを追加する根拠はない。

## 最小clean implementation guide

1. `PrimitiveDisplayProfile` をrender pathから外す。別のadaptive profileやscale値へ置換しない。
2. Android `surfaceChanged(width,height)` で、その `width,height` をそのまま `SoftwareEngineCanvas`、presenter、`PresentationTransform.configure(width,height,width,height)` に渡す。
3. Win32 `applyClientSize(width,height)` でlogical/framebuffer寸法をそのclient寸法にし、同じ寸法でtransformをconfigureする。既存canvas objectの `resize()` はそのまま利用できる。
4. Win32 live resizeは最新値へcoalesceし、render直前に最大1回だけbufferをresizeする。
5. exact destinationを全面に一度だけpresentする。Win32 `PatBlt` とAndroid `drawColor` をfull-frame pathから除く。別のletterbox装飾は追加しない。
6. scene、`UiShellLayout`、`RetroHudComponent`、glyph rendererのscale/layout codeにはこの変更のための編集を入れない。logical boundsが自動的に新framebuffer寸法になる既存pathを使う。
7. `PrimitiveDisplayProfileTest` の固定契約を除去し、既存 `PresentationTransformTest` に同寸法configure時の `viewport=(0,0,w,h)` とedge input identityを追加する。`SoftwareEngineCanvas.resize()` が指定寸法を保持するtestも最小の直接証拠になる。

## law audit

### Engine law

- target source sets: `commonMain`, Android terminal, Win32 terminal
- hot path: full-frame raster + palette expansionに影響する。resize allocationはframe外へ限定できる。
- platform interop: Android Canvas/Bitmap、Win32 DIB presentのみ。
- assets/resources: 追加不要。
- platform APIをcommonMainへ漏らす必要はない。

### COLOR LAW CHECK

- Core color representation: `ByteArray`内のpalette index 0..15（`IndexedFramebuffer.kt:3-18,51-64`）
- Native color conversion location: Android/Win32 presenterのみ
- Palette cache: 両presenterともrevision付き16-entry `IntArray`
- Platform leakage: なし
- Result: PASS

### PLATFORM FIREWALL CHECK

- Platform: Android / Win32
- Allowed responsibility: surface/client寸法の報告、indexed frameのnative present、input座標転送
- Core responsibility preserved: scene、layout、HUD placement、palette meaning、render decisions
- Leakage found: exact寸法を渡すだけならなし
- Result: PASS

### HOT LOOP AUDIT

- Function: common raster + Android/Win32 palette expansion
- Status: PASS（resizeをframe内で繰り返さない条件）
- Violations: 通常frameの新規allocationは不要。Win32 live resizeの逐次allocationだけcoalescing対象。

## 推奨範囲

このpassでは「exact client framebuffer」「single full-frame present」「resize coalescing」「直接契約test」までに限定する。魔法陣radius、HUD比率、glyph sizeの再設計は同じ変更へ混ぜない。exact framebufferを実機で見た後、それぞれをlayout/visual densityの独立問題として調整できる。
