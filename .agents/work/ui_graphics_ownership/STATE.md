# Objective
魔法陣をUI/IMGUIのscale所有から外し、指定Android参照画像の見た目を保つ。固定グリフセルUIの核心を直し、Active TimerのStart・Reset・Nextを常に表示・操作可能にする。

# Constraints
- `commonMain`が描画・layout・入力の権威。platform wrapperは変更しない。
- UI layoutだけが `U = glyphWidth = glyphHeight` を使う。
- 魔法陣はgraphicsであり、UI grid・HUD・IMGUIに寸法を所有させない。
- 参照: `D:\Programes\TB screenshots\Android before scaling overhaul\Screenshot_20260816-151707.png`。
- 16色palette index、整数snapped raster、hot path allocation-free。
- 外部asset、UI framework、診断基盤、API renameを追加しない。

# Plan
- [x] 現在のframebuffer/UI/graphics/inputの到達経路を確認する。
- [x] UI gridと魔法陣graphicsの所有境界を実装する。
- [x] Start・Reset・Nextのrenderとhit-testを同じlayoutで到達可能にする。
- [x] 既存test/buildで確認する。

# Confirmed
- 現行 `ActiveTimerScene.timerRadius` はplay areaの45%を半径にするため、魔法陣をUI領域寸法へ従属させている。
- `NestedTimeboxInstrumentRenderer.render` は外側graphicsと中央UI readoutを一つのrender呼び出しに束ねている。
- 指定Android参照では魔法陣は画面幅内に収まり、操作ボタン三個が魔法陣直下、HUD上に見える。
- 悪いWin32画像では魔法陣がcontent下端/HUD境界まで到達し、Active Timer操作列が魔法陣と競合している。
- UI入力はplatform座標から `SceneManager` で一度だけ `UiRasterGrid` のlogical座標へ変換され、HUD hit-test後にsceneへ届く。
- 現在の修正中コードではtask入力、graphics viewport、操作列を別領域にし、操作列のrender/hit-testは同じhelperを使用している。
- 魔法陣はsource radius 162px、参照上限半径486pxをgraphics-local値として持ち、実framebufferのgraphics viewport短辺へ収まる連続半径を使う。
- 中央readoutを含む魔法陣全要素はgraphics-local半径から比率を導き、UI gridへ戻らない。装飾glyphのpixel blockだけはaliasを保つため最寄り整数にする。
- 操作列上端 `btnY` とgraphics viewport下端 `btnY - U` は別領域。render/hit-test双方が同じ `timerControlRowY/timerControlWidth/timerControlX` を使う。
- `testDebugUnitTest`、`winTest`、Win32 debug executable link、Android debug APK assembleは全て成功。
- 最終Win32実画面を1906x1016と1181x1016の二寸法で目視確認し、どちらも魔法陣の外周全体、3操作ボタン、HUDが画面内かつ非重複。
- 最終Win32バイナリで `Start` によりtimerが進行し、`Next` によりStage 1/5から2/5へ、`Reset` によりStage 1/5へ戻ることを可視自動操作で確認。
- Android実機はADB未接続。1080x2400相当surfaceの決定的な座標計算では、参照画像の魔法陣上端・下端・操作列開始位置と約10px以内で一致する。

# Rejected
- whole-frame倍率変更だけで直す: 魔法陣とUIを同時に変形し、所有境界を直さない。
- HUDを縮めて空間を作る: 魔法陣半径をさらに増やし、症状を悪化させた。
- UIの `U` やcontent比率から魔法陣半径を再計算する: graphicsとUIの所有境界を再び混同する。
- 最新コードを未確認のまま視覚的に成功と判断する。
- 魔法陣をsource radius 162pxの整数blockだけで表示する: Win32 landscapeでblock 1になり、小さすぎる。

# Unverified
- Android実機での最終APK目視確認（ADB接続端末なし）。
- calendar modeを含む極小clientでの目視確認。

# Next
実装・build・Win32可視操作確認は完了。Android端末が接続された場合のみ、最終APKを参照画像と実機比較する。
