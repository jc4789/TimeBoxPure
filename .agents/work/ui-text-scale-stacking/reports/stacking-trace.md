# Scope

- 読み取り専用で、コミットメッセージ `shit`、文字積み上げ導入差分、現行 API と呼び出し経路、指定された3画像に現れる Template / Main の破綻を追跡した。製品コード、テスト、ビルド成果物は変更していない。
- 対象コミットの特定には `git log --all --oneline --decorate --grep='^shit$' -i`、変更範囲には `git show --stat --oneline 30f0b34` と `git diff 30f0b34^ 30f0b34 -- ...` を使用した。
- 現行呼び出し箇所の列挙には `rg -n` / `rg -c` で `ProceduralTextRenderer`、`measureButtonHeight`、`drawButton`、`sessionLimitLabel` を検索した。
- 画像は `D:\Programes\TB screenshots\Screenshot_20260809-151313.png`（日本語 Template）、`Screenshot_20260809-151249.png`（英語 Template）、`Screenshot_20260809-151245.png`（英語 Main）を直接確認した。
- `canonical-unit-layout` の Responsive Row Stacking にある判定形、`padding = U / 2`、`maxRowHeight` の一回計算、`currentY += maxRowHeight + padding` の一回更新を必須条件として評価した。ユーザーが明示した `((U * 4) - (U / 4))`、`(U / 2 + U / 8)`、`(U + (U / 2) + (U / 8))` 等の表現は正当な設計値として維持対象にした。

# Confirmed

- `shit` は現在の HEAD `30f0b34b6e5b23402659fb249da78f7540c818af` である。`git show --stat 30f0b34` では9ファイル、追加1211行・削除761行の大規模変更で、積み上げだけの単独コミットではない。
- 積み上げの中核はこのコミットで `ProceduralUiPrimitives.kt` の `ProceduralTextRenderer` に導入された。現行 API は次の通りである（同ファイル75～409行）。
  - 非積み上げ描画は `drawRaw(...)` だけで、最大幅、中央寄せ、クリップ、行高測定を持たない（83～97行）。
  - `String` と `IntArray` の両方に `measureWrappedLineCount(...)`、`measureWrappedHeight(...)`、`drawWrapped(...)` がある（99～122、188～218、253～333行）。
  - 見出しは `headingScale(...)`、`measureHeadingHeight(...)`、`drawHeading(...)` があり、空き幅から倍率2または1を自動選択したうえで必ず `drawWrapped` を通る（124～147行）。
  - プリセットID専用の `measurePresetIdHeight(...)` / `drawPresetIdWrapped(...)` と、入力カーソル用 `locateWrappedCursor(...)` がある（149～250行）。
  - 折返しは `cellsPerLine = floor(maxWidth / (U * scale))`、改行優先、次に半角・全角空白で単語境界を探し、空白がなければ `hardEnd` で単語途中でも切る（335～394行）。
- 現行 API に積み上げの可否を指定する引数はない。`drawWrapped` は名前どおり常時折返し、`ScaledProceduralRenderer.drawButton(...)` にもフラグがなく、全ボタンが内部で無条件に `measureWrappedHeight` / `drawWrapped` を通る（`ScaledProceduralRenderer.kt` 677～721行）。`measureButtonHeight(...)` も常に折返し高を採用する（33～37行）。
- 呼び出し範囲は局所的ではない。`rg -c` では `drawWrapped` の直接呼び出しが `Scenes.kt` 24箇所、`NestedTimeboxInstrumentRenderer.kt` 2箇所、`ScaledProceduralRenderer.kt` 1箇所、`measureWrappedHeight` がそれぞれ30、2、2箇所である。さらに `Scenes.kt` の `drawButton` 27箇所が共通ボタン経由で強制積み上げの影響を受ける。
- 導入差分は、以前の「一行をクリップする」経路を多数置換している。例として ActiveTimer の入力は `drawUpperClipped` から可変高 `drawWrapped` と折返しカーソルへ、Entropy のタスクは最大文字数で打ち切る処理から `drawWrapped(IntArray, ...)` へ、ボタンは中央一行 `drawText(... clipWidth/clipHeight ...)` から無クリップの `drawWrapped` へ変更された。根拠は `git diff 30f0b34^ 30f0b34` の `ProceduralUiPrimitives.kt`、`ScaledProceduralRenderer.kt`、`Scenes.kt`。
- Template の `ＦＯＲＧＥ` 崩れは数式で確定できる。
  - `TemplateCustomizerScene.render` は `forgeBtnW = maxOf(((U * 6) - (U / 4)), playAreaW * 0.24f)`、`forgeBtnH = U + U / 2 + U / 8`、文字列リテラル `ＦＯＲＧＥ` を `drawButton` へ渡す（`Scenes.kt` 942～945、1032～1034行）。タッチ側も同じ寸法式を使う（1107～1123行）。これらの U 表現自体は意図された値であり、誤りではない。
  - 最小幅では `U=16` よりボタン幅92、ボタン高26。`drawButton` は左右 `U/2` を除いた76を文字領域にするため4セルしか入らない。一方 `ＦＯＲＧＥ` は5セル＝80幅である。
  - 空白のない5文字なので `stringLineEnd` は4文字で強制改行し、`ＦＯＲＧ` と `Ｅ` の2行、文字高32になる。`textY = y + (26 - 32) / 2 = y - 3` で描画し、しかも現行 `drawWrapped` はボタン矩形でクリップしない。このため上下3論理pxずつ枠外へ出る。両 Template 画像の `FORG` / `E` 表示と一致する。
  - コミット前の同じボタンは全幅に対して80幅の一行を中央配置し、ボタン矩形でクリップしていた。寸法式と文字列は同じで、強制積み上げへ置換したことが回帰点である（`git show 30f0b34^:.../ScaledProceduralRenderer.kt` の旧 `drawButton` と `git show 30f0b34^:.../Scenes.kt` の `ＦＯＲＧＥ` 呼び出し）。
- Template の「英語ではタイトルも本文も同じ大きさ」は、言語ごとの別フォントではなく `headingScale` の幅依存自動倍率で説明できる。
  - `strings.presetsTitle` は日本語 `テンプレート` が7文字ではなく6セル、英語 `Ｔｅｍｐｌａｔｅｓ` が9セルである（`Strings.kt` 227、333行）。
  - `headingScale` は倍率2の全幅が `maxHeaderW` に入る場合だけ2、入らなければ1にする（`ProceduralUiPrimitives.kt` 124～129行）。Template は測定・描画ともこの API を使う（`Scenes.kt` 946、1033行）。提示画面では短い日本語は倍率2、長い英語は倍率1となり、英語見出しだけ本文と同じ倍率になる。これは空き幅と言語が文字単体倍率を変えている確定経路である。
- Main の英語 `SESSION LIMIT` / `60:00` 衝突も数式で確定できる。
  - 英語 `ＳＥＳＳＩＯＮ　ＬＩＭＩＴ` は13セル、日本語 `セッション上限` は7セル、中国語 `会話限制` は4セル（`Strings.kt` 138、244、350行。文字数確認コマンドは PowerShell の各文字列 `.Length`）。
  - `NestedTimeboxInstrumentRenderer.drawStaticTextCentered` はラベルを `maxTextWidth` で常時折返し、高さ全体を指定された固定中心 `centerY` の上下へ半分ずつ広げる（472～475行）。英語は空白で `SESSION` / `LIMIT` の2行＝2U、日本語・中国語は1行＝Uになる。
  - `dual.5` 分岐では `bigTimeRemaining` を中心 `centerY + 2U`、倍率1で描くため上端は `centerY + 3U/2`、下端は `centerY + 5U/2`。その直後のラベル中心は固定 `centerY + 3U`（442～445、491～502行）。英語2行の上端は `centerY + 2U` となり、時間と `U/2`＝8論理px重なる。日本語1行の上端は `centerY + 5U/2` なので接するだけで重ならない。英語だけ発生するという画像・報告と一致する。
  - `dual.5` 以外の macro 分岐は時間の下端が `centerY + 2U`、英語2行ラベルの上端も `centerY + 2U` なので同じ式でも重ならない。提示画像に `ALARM: 15:00` があることも `dual.5` 分岐と整合する。
  - この回帰は `30f0b34` で旧一行 `drawStaticTextCentered` を可変高 `drawWrapped` に置換しながら、隣接する時刻の位置を固定のまま残したことで導入された（`git diff 30f0b34^ 30f0b34 -- NestedTimeboxInstrumentRenderer.kt`）。
- 3画像の目視結果はコード追跡と一致した。日本語 Template と英語 Template の両方で `ＦＯＲＧＥ` が不要な2行になり枠を越える。英語 Template だけ見出しが本文と同倍率に見える。英語 Main では中央の `60:00` 相当表示と2行化した `SESSION LIMIT` が重なる。
- Responsive Row Stacking の現行実装は二系統ある。
  - `TemplateForgeScene` の `labelNeedsStack` は `requiredLabelWidth > labelColumnWidth` という判定自体は正しく、積み上げ時の返却値も数値上は `labelHeight + U/2 + controlHeight + U/2` になる（`Scenes.kt` 2011～2157行）。ただし判定・X・Y・幅・高さを複数ヘルパーで繰り返し算出し、必須の単一 `maxRowHeight` 形を共有していない。
  - `SettingsScene.layoutRow` は積み上げ時に `currentY` をラベル分とコントロール分で別々に進め、間隔も `U/4` を二度使用する。side-by-side でも `max(labelHeight, controlHeight)` を計算していない（`Scenes.kt` 2481～2482、2504～2532行）。これは canonical-unit-layout の「`maxRowHeight` を計算して `currentY` を一度だけ進める」という必須形に明確に不一致である。
- `UIscalefix.md` の幅依存 `headingScale` 批判と UI 内倍率／表示倍率の分離は、この追跡結果と整合する。一方、同文書4.4の「折返し・文字積み上げを変更しない」は、今回のユーザーによる明示的な積み上げ修正依頼を満たさず、今回の受入条件としては使用できない。

# Rejected

- `ＦＯＲＧＥ` の原因が日本語ローカライズである、という仮説は否定した。文字列は言語非依存の固定リテラルで、両言語画像に同じ崩れがある。
- Forge の既存 `forgeBtnW` / `forgeBtnH` の U 表現が不正、という仮説は否定した。ユーザーが明示的に正当とした形であり、実際の回帰は共通ボタンが固定高さを無視して強制的に2行を描くことにある。
- Forge をボタン高の増加だけで直す案は採用しない。32以上へ拡大すれば枠外描画だけは隠せるが、不要な一語積み上げを残し、「積み上げを任意指定可能にする」という要求を満たさない。
- Main の衝突が `60:00` 側だけの倍率異常、または字形データの異常である、という仮説は否定した。倍率1の時刻と、2行高を固定中心へ逆方向に広げたラベルの論理矩形が8px重なる。表示倍率は衝突を物理的に拡大して見せるが、衝突そのものは論理座標ですでに存在する。
- 英語だけ別の文字倍率が直接設定されている、という仮説は否定した。言語分岐ではなく、同じ `headingScale` が翻訳後の文字列幅を入力にして結果1／2を変えている。
- 積み上げ機能そのものを削除する案は対象外であり禁止条件に反する。必要な長文、入力、説明、タスク行には折返しを残す必要がある。
- 現行 `SettingsScene.layoutRow` が canonical-unit-layout の正確な行高更新形を満たす、という評価は否定した。見た目上の総和ではなく、`maxRowHeight` の事前計算と単一カーソル更新が必須である。

# Unknown

- 3画像に対応する実行時 `logicalWidth`、`logicalHeight`、`quietRadius` のログは提供されていない。ただし Forge の最小幅条件と Main の相対Y式だけで、報告された崩れは実寸値に依存せず説明できる。
- すべての既存 `drawWrapped` 呼び出しについて、積み上げを許可すべきかどうかの製品上の個別判断は画像3枚だけでは確定しない。入力・説明文・ユーザー作成タスクは積み上げが必要と推定できるが、各ボタン・HUDラベル・見出しは明示的に選別する必要がある。
- Template 見出しの意味上の明示倍率を1と2のどちらにするかは、この読み取り専用調査だけでは設計決定できない。確定している受入条件は、言語や空き幅から暗黙に倍率を切り替えず、同じ役割へ同じ明示倍率を渡すことである。
- ビルド、実行、ピクセル差分はこの読み取り専用範囲では行っていない。他画面での積み上げ回帰は静的な呼び出し範囲まで確認し、全表示状態の目視は未確認である。

# Recommendation

- 積み上げを削除せず、「明示して使う機能」にする。`drawWrapped` は明示的な折返し API として残し、単一行用に最大幅・配置・クリップを持つ経路を用意する。既存 `drawRaw` はクリップ等を持たないため、共通コンポーネントの代替としてそのまま使うだけでは不足する。
- `ScaledProceduralRenderer.drawButton` と対応する `measureButtonHeight` に同じ明示引数（例: `textStacking: Boolean = false`）を通す。false は一行測定・一行中央配置・ボタン矩形内クリップ、true は現行の単語境界折返しと可変高測定にする。測定と描画で異なる値を使ってはならない。これにより既存の積み上げ機能を保持しつつ、呼び出し側が必要なボタンだけ true を選べる。
- Forge は既存の `((U * 6) - (U / 4))` 幅と `(U + (U / 2) + (U / 8))` 高を維持し、`ＦＯＲＧＥ` を非積み上げで明示する。ボタン内部の水平余白を `U/4` ずつとすれば文字領域84となり、5U＝80が一行で入る。既存式を別表現や別寸法へ置換する必要はない。
- Main の session label は必要なら積み上げ true を明示して残すが、固定中心同士で配置しない。まず時刻高 `U` と実測ラベル高を得て、`bigTimeTop`、`labelTop = bigTimeTop + timeHeight + U/2` のように上から順に配置し、グループ全体の高さを一度確定する。英語2行でも日本語1行でも、後続要素が前要素の上へ逆流しない形にする。
- label-control row には共通の一回計算結果を使う。必須形は次の通りで、描画、測定、当たり判定の三者が同じ結果を参照する。

```kotlin
val requiredLabelW = text.length * U
val labelColumnW = usableWidth * LABEL_COLUMN_RATIO
val padding = U / 2
val stack = allowStacking && requiredLabelW > labelColumnW

val maxRowHeight = if (!stack) {
    maxOf(labelHeight, controlHeight)
} else {
    labelHeight + padding + controlHeight
}

currentY += maxRowHeight + padding
```

- `allowStacking` は行・コンポーネントごとの明示値にし、Settings と TemplateForge の自動判定を同じ共通レイアウト計算へ集約する。特に `SettingsScene.layoutRow` の途中での複数 `currentY` 更新を廃止し、必ず最後に一度だけ更新する。TemplateForge の render / touch / contentMinScroll も同じ計算を使い、表示高と当たり判定高を一致させる。
- `headingScale(text, maxWidth)` は幅と言語に依存する暗黙倍率なので廃止し、`measureHeadingHeight` と `drawHeading` の両方へ同じ明示 `scale` と同じ積み上げ可否を渡す。Template の見出しは全言語で同じ意味上の倍率を呼び出し側が指定し、必要な高さはその明示条件から測る。
- 最小変更対象は `ProceduralUiPrimitives.kt`（単一行／明示折返し契約）、`ScaledProceduralRenderer.kt`（ボタンの任意積み上げと対称測定）、`NestedTimeboxInstrumentRenderer.kt`（可変高を順方向に積む）、`Scenes.kt`（Forge の明示非積み上げ、見出しの明示倍率、正確な row shape）である。積み上げ削除、既存 U 式の置換、シーン別の別フォント処理は不要である。
