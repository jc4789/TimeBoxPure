# Scope

- 一次・公式技術資料だけを使い、次を調査した。
  - low-resolution logical render target
  - nearest-neighbor と整数 pixel replication
  - resolution-independent geometry と pixel-art raster density の違い
  - output resolution へ直接 rasterize した場合に alias character が変わる理由
- Web 資料をコードへ当てはめる前に、現行 commonMain の完整経路を確認した。
  - source framebuffer authority: `PrimitiveDisplayProfile.kt:3-11`
  - indexed storage: `IndexedFramebuffer.kt:3-59`
  - integer software raster boundary: `SoftwareEngineCanvas.kt:29-99`, `SoftwareGraphics.kt`
  - UI/output coordinate branch: `EngineCanvas.kt:5-49`, `ScaledProceduralRenderer.kt:6-119`
  - magic-circle source/output conversion: `NestedTimeboxInstrumentRenderer.kt:28-34,113-194`
  - aliased circle rasterization: `AliasedVectorLayer.kt:55-82`
  - presentation/input inverse: `PresentationTransform.kt:3-64`
- Production code は編集していない。

# Confirmed

## Technical sources

- SDL3 の公式 API は logical presentation を「device-independent resolution」と定義し、renderer が指定された logical target dimensions として動作し、実際の resolution へ必要に応じて scale すると説明する。つまり render resolution と display resolution は別の責任である。[SDL_SetRenderLogicalPresentation](https://wiki.libsdl.org/SDL3/SDL_SetRenderLogicalPresentation)
- SDL3 は logical source を output へ写す方式を `STRETCH / LETTERBOX / OVERSCAN / INTEGER_SCALE` と明示的に分離し、`INTEGER_SCALE` を「整数倍で output resolution に fit」する方式として定義する。[SDL_RendererLogicalPresentation](https://wiki.libsdl.org/SDL3/SDL_RendererLogicalPresentation)
- SDL3 の公式 software-game migration guide は、software-rendered game が一枚の完成済み frame texture を持ち、それを一回の blit で画面へ出す構成を示す。sharp/blocky pixels を保つ場合は nearest-neighbor を選び、その frame を scale/center/letterbox して present する。[SDL 1.2 migration guide, software-rendered games](https://wiki.libsdl.org/SDL3/SDL12MigrationGuide)
- SDL3 の scale-mode 定義では nearest は「nearest pixel sampling」、linear は別方式である。nearest は presentation sampling の選択であり、source geometry を高解像度で再 rasterize する命令ではない。[SDL_ScaleMode](https://wiki.libsdl.org/SDL3/SDL_ScaleMode)
- Microsoft の Direct2D API 定義も、nearest-neighbor は destination pixel に最も近い source bitmap pixel の**正確な色**を使い、linear は近い4 pixel から補間すると区別する。[D2D1_BITMAP_INTERPOLATION_MODE](https://learn.microsoft.com/en-us/windows/win32/api/d2d1/ne-d2d1-d2d1_bitmap_interpolation_mode)
- W3C の規格は整数倍 nearest の性質をさらに明確にする。整数倍では source pixel が均一な「big pixels」になる。一方、2.5倍のような非整数 nearest は一つの source pixel が destination で2pxまたは3pxへ交互に複製され、線が場所によって1px太く/細くなる alias irregularity を生む。[CSS Images Level 3, image-rendering](https://www.w3.org/TR/css-images-3/#the-image-rendering)
- Vulkan の正式仕様では rasterization は primitive が framebuffer-coordinate の**整数 grid のどの square を占有するか**を決定する処理であり、line/polygon coverage はその grid/sample location で決まる。[Vulkan Specification, Rasterization](https://docs.vulkan.org/spec/latest/chapters/primsrast.html)

## Why direct output-resolution rasterization changes alias character

- Geometry は rasterize 前なら resolution-independent に表現できる。しかし aliased result は抽象 geometry ではなく、最終 raster grid で選ばれた離散 pixel 集合である。Vulkan 仕様の通り coverage は framebuffer の整数 grid/sample points に対して決まる。
- 同じ円を radius 162 の source grid で midpoint rasterize し、完成 pixel を3倍 replicate する場合、source の各 stair step は正確に `3x3` block になる。
- 同じ円 geometry を先に radius 486 へ拡大し、1080×2424 grid で再度 midpoint rasterize する場合、algorithm は486px radius の別の整数 circleを生成する。これは162px circleの各 pixelを3倍した集合ではない。step count、step位置、stroke cadence、glyph bitmap density は再計算される。
- したがって「geometry の比率が同じ」と「pixel art が同じ」は同義ではない。前者は shape/proportion、後者は source raster と replication law の契約である。
- Physical output resolution は、直接そこへ rasterize している限り alias result の**因果**になる。しかし、それを design authority にすべきではない。正しい分離では physical resolution は capacity、aspect、最大整数 presentation multiple、余白/clip の入力に限定される。source pixel pattern、stroke width、glyph bit density の authority は engine-owned logical/indexed raster である。

## Current repo behavior

- `PrimitiveDisplayProfile` は primitive framebuffer を terminal の物理寸法と同一にする (`PrimitiveDisplayProfile.kt:3-11`)。既存 tests も 800×600、2560×1368、3840×2160 をそのまま source とすることを固定している (`PrimitiveDisplayProfileTest.kt:8-35`)。
- `SoftwareEngineCanvas` はその framebuffer へ最終 integer raster を書く。line/circle/rect coordinates と stroke/radius は `roundToInt()` される (`SoftwareEngineCanvas.kt:33-99`)。よって現在の alias grid は物理 output grid そのものである。
- `UiMappedEngineCanvas` は通常 UI draw を `UiRasterGrid.pixelBlock` 倍する一方、`beginGraphics()` 後は変換を完全に bypass して output canvas へ直接渡す (`ScaledProceduralRenderer.kt:6-118,178-188`)。これは一つの software renderer 内に二つの raster-density authority がある状態である。
- 魔法陣は UI viewport を `renderer.outputX/Y` で物理座標化し (`NestedTimeboxInstrumentRenderer.kt:142-150`)、その物理 short axis から `graphicsRadius` と `graphicsUnit` を再導出する (`NestedTimeboxInstrumentRenderer.kt:151-158`)。glyph block もその半径を `roundToInt()` して決める (`NestedTimeboxInstrumentRenderer.kt:155-157`)。
- その後 `beginGraphics()` し (`NestedTimeboxInstrumentRenderer.kt:187-194`)、`AliasedVectorLayer` は物理 radius を整数化して物理 framebuffer 上で midpoint circle を新規生成する (`AliasedVectorLayer.kt:55-82`)。これは source circle の拡大ではなく、output resolution ごとの再 rasterization である。
- `GRAPHICS_SOURCE_RADIUS=162` と `GRAPHICS_SOURCE_CELL=16` は既に design/source geometry の候補を表しているが、現在は `GRAPHICS_REFERENCE_RADIUS=162*3` と output-derived `graphicsUnit` により物理 pixel ruleへ変換されている (`NestedTimeboxInstrumentRenderer.kt:28-34,151-158`)。
- `PresentationTransform` 自体は source/output を分離できる型だが、現在の `PrimitiveDisplayProfile` が source=output にするため、通常は 1:1 full-client mapping になる (`PresentationTransform.kt:27-55`; `PrimitiveDisplayProfile.kt:7-11`)。presentation 側の nearest sampling が正しくても、source と output が同一なら low-resolution alias を保存する効果はない。
- Indexed storage は既に適切な primitive である。`IndexedFramebuffer` は1 pixelにつき palette index `0..15` を保持し、UI/platform/presentationを知らない (`IndexedFramebuffer.kt:3-18,29-59`)。捨てるべきものではなく、authority と寸法の置き場所を正すべきである。
- Android presenter は filtering/AA を無効にし、完成 framebuffer を nearest で destination rect へ描く (`AndroidFramebufferPresenter.kt:11-37`)。Win32 も `COLORONCOLOR` + `StretchDIBits` で present する (`Win32Host.kt:197-227`)。platform-side concept は既に dumb terminal に近い。問題の根は、その前段の source framebuffer が物理解像度と同一であることにある。

## Direct answer: should physical resolution be causal/design authority?

- **Design authority: no.** Physical width/height must not choose the circle's source radius, glyph source-pixel size, stroke cadence, or alias silhouette.
- **Presentation capacity: yes.** Physical width/height may determine:
  - source imageの整数倍が何倍まで収まるか
  - centered viewport/letterbox/overscan/crop
  - portrait/landscape用のdesign profile選択（profile内容そのものではない）
  - input inverse transform
- **Current behavior: physical resolution is accidentally causal.** Source=output と output-space re-rasterization のため、display pixel count が circle/glyph の離散 pixel patternを変える。

# Rejected

- **Rejected: vector/procedural geometry なら output resolution へ直接描いても pixel style は同じ。** Shape比率は保てても、coverage grid が違うため alias pattern は別物になる。
- **Rejected: nearest-neighbor presenter だけで問題は解決する。** Current source=output では nearest presentation はほぼ1:1であり、既に高密度で生成された aliasを粗いsource aliasへ戻せない。
- **Rejected: output-derived `graphicsUnit`, `graphicsGlyphBlock`, `rasterBlock`, stroke multiplier を各 feature に追加する。** それぞれが別の raster-density authority となり、同じ問題を layer/widget ごとに再発させる。
- **Rejected: non-integer nearest stretch を常に full screen へ使えば pixel-perfect。** 色は混ざらないが、W3C が説明する通り source pixels と line thickness が不均等になる。整数 replication と余白/crop は別契約である。
- **Rejected: DPI/density ratio を新しい authority にする。** 物理DPIは source raster designを決めない。必要なのは engine-defined source pixels と output presentation multiple である。
- **Rejected: platform wrapper が Android/Win32 ごとに logical sizeやgraphic scaleを決める。** 同じ commonMain frameをpresentするだけ、という既存firewallを破る。
- **Rejected: magic-circleだけの局所 `scale` argument で修復する。** それは現行 `beginGraphics` 分岐を別名で残し、UIとgraphicsの二重authorityを温存する。

# Unknown

- Project-authoritative source framebuffer の正確な width/height は、一般資料から決められない。360×800などの便宜値を採用する根拠はない。既存の glyph cell law、保持したいcell count/aspect profile、accepted screenshots、魔法陣の `sourceRadius=162` から設計責任者が確定すべき値である。
- 一つの portrait/landscape source profileで足りるか、複数の明示的 design profiles が必要かは未確定。ただし profile 選択と source pixel density は分けられる。
- Integer fit 後に余る physical pixels を letterbox、overscan、crop のどれで扱うかはproduct choiceである。非整数 stretchを黙って選ぶべきではない。
- 最小端末で source target が1倍で収まらない場合の契約は未確定。source designを縮めて再 rasterizeするのか、crop/scrollするのかを明示する必要がある。
- 現在進行中の worktree は `NestedTimeboxInstrumentRenderer.kt` と `AliasedVectorLayer.kt` に未commit変更があるため、実装開始時には root agent が最新行を再確認する必要がある。

# Recommendation

## One-owner software-renderer architecture

`commonMain` の一つの software-renderer pipeline を唯一の authority にする。

1. **Design/source profile**
   - Engine が source raster width/height、glyph cell、graphics source coordinates を所有する。
   - 値は accepted designから導出し、terminal width/heightをコピーしない。
   - Aspect差が必要なら、明示的な portrait/landscape design profile をここ一箇所で選ぶ。featureは選択ロジックを持たない。

2. **One indexed source framebuffer**
   - `SoftwareEngineCanvas` が engine-defined low-resolution `IndexedFramebuffer` 一枚へ全 scene/UI/graphics を rasterizeする。
   - Geometry は source coordinatesでlayoutし、source framebuffer境界で一度だけ integer snap/rasterizeする。
   - 魔法陣専用の物理 `graphicsUnit`、UI用 block、feature-local `rasterBlock` を重ねない。

3. **One raster coordinate law**
   - UI glyphもprocedural circleも同じ source-pixel canvasへ描く。
   - `UiMappedEngineCanvas.graphicsDepth` のような「同じdraw APIが状況によりlogical/physicalを切り替える」分岐を最終設計に残さない。
   - `NestedTimeboxInstrumentRenderer` は `GRAPHICS_SOURCE_RADIUS=162` 等のsource geometryを使い、physical output dimensionsを読まない。最終配置だけsource viewport内で行う。

4. **One presentation transform**
   - `PresentationTransform` が source framebuffer → physical terminal の整数 uniform scaleとcentered viewportを一度だけ計算する。
   - `scale = floor(min(outputW/sourceW, outputH/sourceH))`, `scale >= 1` をpresentation capacityとして扱い、余りを明示的にletterbox/cropする。これはUI scaleでもgraphics scaleでもなく、完成frameのpixel replication factorである。
   - Pointer input は同じ transform の厳密なinverseを使う。

5. **Dumb terminals**
   - Platform presenterは16-entry palette展開とnearest integer blitだけを行う。
   - Android/Win32がlayout、glyph size、circle radiusを決めない。

この構成なら、source sceneを一度 rasterizeした同じ `ByteArray` が全platformの視覚的真実になる。Physical resolutionが増えても増えるのは完成source pixelの複製数と余白だけで、circle algorithm、glyph bitmap、stroke stepは変わらない。feature-specific band-aidではなく、`source raster -> indexed frame -> presentation` の三段階が一つの所有境界になる。

## Why this is preferable to a magic-only offscreen surface

- Magic-only fixed surfaceも alias保存自体は可能だが、UI main framebufferと別のdensity/presentation ruleを新設すると、また二重authorityになる。
- 最終architectureとしては whole completed scene を一つの source indexed framebufferへ描く方が単純である。Local surfacesが将来必要でも、その生成・blit法は同じ rendererが一般primitiveとして所有し、magic-specific scaling APIにはしない。

## Verification contract

- 同じ design/source profile と同じ scene stateを、異なる physical output sizesへpresentしても、presentation前 `IndexedFramebuffer.indices` は完全一致すること。
- Physical output間で変化してよいのは integer replication count、viewport offsets、letterbox/cropだけ。
- Source-frame hash、palette-index範囲、source dimensions、known anchorsを engine-level testで比較する。Platform screenshotはpresentation確認でありsource rasterのauthorityではない。

