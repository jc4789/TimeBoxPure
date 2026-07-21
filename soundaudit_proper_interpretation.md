## 判定

`soundaudit.md` は**診断書としては条件付きで有効**。ただし、**今回の実装指示書としてそのまま使うのは不可**。

中核である次の三点は、現行コードから明確に立証できている。

* `OpnaLikeSynthesizer` が PMD 状態・物理音源・出力処理を同時所有している。
* `CompiledOpnaPlayer` と `OpnaLikeSynthesizer` が相互に実行し合っている。
* 物理 FM/SSG voice が `PmdModulationFrame`／`PmdSsgFrame` を直接解釈している。

一方、文書後半は K/R の PMD 忠実化、ポルタメント算法変更、パッチ副作用廃止、出力層再編まで混ぜている。そこは「所有権を切り分けるだけ」という今回の範囲を超えている。

今回は読み取り監査だけで、コード変更もテスト実行もしていない。

## オンボーディング文書との整合性

`.agents/AGENTS.md` と `fm-dsp-audit` は、音響構造を「PMD/MML ソフトウェア」と「手続き生成の YM2608 系音源」の二層として明記している。また、隣接した改善や検証基盤を追加せず、要求された最小の production-only 修正に限定するよう定めている。

`ENGINE_BRIEF.md` は、すでに `audio/mml` と `audio/opna` の所有境界が修復済みであるかのように書いている。だが実コードでは `CompiledOpnaPlayer` がまだ OPNA package を宣言し、シンセが `PmdPerformanceState` を所有し、相互呼び出しも残っている。つまり、brief は現在の依存グラフではなく、意図された構造を記述している状態。`soundaudit.md` の中心診断は、この文書ドリフトを正しく発見している。

## Finding ごとの判定

**1. `OpnaLikeSynthesizer` が三領域を所有している — 有効**

`OpnaChipState`、`PmdPerformanceState`、`OpnaMixer`、`SongMastering` を一クラスが所有し、PMD frame の生成、物理 voice の描画、ミックスまで行っている。Finding 1 は正しい。今回抜き出すべきなのは PMD 所有だけで、出力処理の全面再編までは不要。

**2. player と synth の実行サイクル — 完全に有効**

player は synth のリセット、segment render、event dispatch を呼ぶ。一方 synth は `player.render(this, ...)` を呼ぶ。これは単なる参照関係ではなく、実際の production 経路上の循環。最初に切るべき場所という判断も正しい。

**3. 物理 voice が PMD runtime object を解釈する — 完全に有効**

`Fm4OpVoice` は PMD の target flag、TL mask、base attenuation、ソフト LFO 値を内部で解釈している。`SsgVoice` は PMD software envelope と `releaseFinished` を読み、物理 voice の寿命まで決めている。これは明確な逆依存。

**4. ポルタメントと相対操作が hardware object 内にある — 所有権診断のみ有効**

PMD performance 側へ移すべきという診断は正しい。ただし、現在の sample-linear ramp を PMD の別の時間軌跡へ直すことは音響・意味論変更になる。公開 PMD マニュアルからポルタメントが driver performance 命令であることは確認できるが、この監査だけでは別の商・余り算法への変更までは認可できない。現在の補間、丸め、開始点、終了点、sample 数をそのまま移すところまでが今回の範囲。   ([Pigu-A][1])

**5. PMS/AMS 所有とパッチ選択時の暗黙変更 — 半分有効**

現在の applied PMS/AMS が PMD frame にしか存在せず、物理 FM voice がそこから hardware LFO 感度を読む構造は誤り。物理 channel state が現在値を所有すべき。一方、音色選択時に `FmPatch.pms/ams` をイベント化する現在の副作用を消すと既存音が変わる。今回はそのイベントと preview 時の seeding を完全に維持したまま、最終適用値の所有者だけを移すべき。    ([Pigu-A][1]) ([ManualMachine][2])

**6. PMD K/R が SSG3 を通らない — semantic divergence は有効、boundary violation は言い過ぎ**

現実装が K/R の instrument ID を `ProceduralDrums.DrumKind` に変換し、独立 generator を鳴らしていること、原 PMD の SSG3/I-part 競合を再現していないことは正しい。公開 PMD 資料でも競合と優先順位は確認できる。   ([Pigu-A][1])

ただし `ENGINE_BRIEF.md` は、PMD K/R 用の独立 procedural generator を現行の意図的構造として明記している。したがって、今回 SSG3 contention へ変更するのは境界修理ではなく、既存の意図的 approximation を別挙動へ置き換える作業。今回許されるのは PMD scheduling／semantic ID と generator trigger の所有境界を整理するところまでで、生成音、優先順位、mix point は変えない。

**7. patch/catalog が複数領域を混在させている — 一部有効だが修理案が広すぎる**

名前・source ID・fallback lookup は MML／音楽側。物理 operator/register 値は OPNA 側。その区別は正しい。だが `FmPatch` や `SsgPatch` を今回すべて分割する必要はない。物理音源への immutable handoff 型として残しても境界は成立する。最低限、名前解決とカタログ所有を core から外し、現在の値、参照同一性、intern 順序、fallback 順序を維持すれば足りる。

**8. output/profile policy が OPNA package にある — 今回の Finding としては範囲外**

マスタリングが YM2608 の literal register state ではないのはその通り。ただし project brief は、mixing・mastering・procedural generator を `audio/opna` 側の product/audio path に置く設計を明示している。今回の要求は PMD と YM2608 core の分離であり、第三の package／layer を新設することではない。`OpnaMixer`、profile、EQ、filter、clip の移動は今回の必須修理から外すべき。

**9. `audio/mml` のファイルが OPNA package を宣言している — 事実。ただし順序の記述を修正すべき**

package mismatch は事実だが、「依存を全部直してから package を最後に変更」も「package を全部先に変更」も危険。**一つの所有単位ごとに、package 移動と露出した依存の修正を同じ小さな差分で行う**のが適切。

また、文書が例示する `PmdSoftwareLfo -> OpnaLfoLaws.PHASE_CYCLE` や `PmdPerformanceState -> OpnRateEnvelope.MAX_ATTENUATION` は、それだけでは逆依存ではない。目標方向は PMD → hardware なので、software 側が hardware-shaped 単位へ lowering する依存は許容できる。問題なのは逆に `Fm4OpVoice`／`SsgVoice` が `Pmd*` 型を知っていること。

なお `CompiledOpnaPlayer` は public class なので、package 移動は音響不変でも外部 API 変更になる。外部 API まで「何も変えない」に含めるなら、public 型の package 移動は別途扱う必要がある。

## `soundaudit.md` に必要な修正

第一に、冒頭へ明確な scope guard が必要。

> この監査が認可するのは既存処理の所有者・依存方向・配置の変更だけであり、生成されるイベント、状態値、時間軌跡、音響出力、PMD compatibility policy の変更を認可しない。

第二に、Finding 6 は「既知の PMD semantic divergence」に格下げし、SSG3 contention の実装を今回の repair order から外すべき。

第三に、Finding 8 は別件へ分離すべき。出力 policy の値変更だけでなく、専用 output layer の新設も今回のスコープ外。

第四に、package 順序を次へ変更すべき。

> package と dependency は ownership slice 単位で同時に修正する。単なる一括 rename も、全 architecture 修正後まで package を放置することも行わない。

第五に、FM の legacy seconds-based compatibility と PMD SSG software envelope を混同しないこと。`PmdSoftwareEnvelope` は PMD 側に保持する。一方 `OpnEnvelopeCompatibility` は既存 seconds-based patch API を OPN rate に変換し、実際の評価は物理 `OpnRateEnvelope` が行う。両方を値・算法そのままで保持するが、前者を理由に後者まで PMD evaluator として移してはいけない。

第六に、参照根拠を再現可能にする必要がある。現在の文書は `D:/Programes/...` のローカル絶対パスと独自 line number を使っており、リポジトリ閲覧者は検証できない。高水準の PMD 意味論は公開マニュアルと梶原氏の公開原典で確認できたが、`PMDDATA.DOC` の正確な work-layout 行や PC-9801-86 の個別行は現状の文書だけでは再現不能。少なくとも「公開参照で再確認済み」と「ローカル資料のみ」を区別すべき。  ([Pigu-A][3]) ([GitHub][4])

## 今回認可できる surgical repair order

1. `PmdPerformanceLaws`、`PmdSampleClock`、`PmdSoftwareEnvelope`、`PmdSoftwareLfo` など、PMD leaf state を ownership slice ごとに MML/PMD 側へ移す。定数、初期値、method body は変えない。

2. `PmdPerformanceState`、logical-part mapping、PMD event interpretation、note lifetime を player 側へ移す。event order、tempo resolution、LFO/envelope clock、release timing はそのまま。

3. production 呼び出し方向を `CompiledOpnaPlayer → OpnaLikeSynthesizer/physical core` の一方向にする。既存 player と synth を使い、別 player、adapter backend、parallel pipeline は追加しない。

4. `Fm4OpVoice` と `SsgVoice` から `Pmd*Frame`、PMD target mask、PMD release semantics を除く。既存配列と計算を移設して再利用し、同じ sample ごとの pitch／attenuation／level を物理側へ渡す。新しいイベント体系や巨大な `ResolvedControlFrame` 階層は作らない。

5. applied PMS/AMS の現在値を物理 FM channel state に置く。ただし compiler が現在 emit しているイベント、patch selection の暗黙値、direct preview の seeding は維持する。

6. K/R は semantic scheduling と generator trigger の境界だけ整理する。現在の procedural generator、音色、優先順位、SSG bus への加算位置は変更しない。

7. ここで止める。ポルタメント算法変更、K/R の SSG3 忠実化、patch DTO 全面分割、output package 再編、gain/EQ/filter/clip/routing、inactive sequencer cleanup は別作業。

絶対に維持すべきなのは、timeline event 順、sample boundary、現在の補間と丸め、相対操作の結果、envelope/LFO の時刻、release 終了 frame、patch ID・名前・lookup・intern 順、暗黙 PMS/AMS、K/R の生成音と mix point、全 gain・EQ・filter・clip・mono/stereo routing。

**最終判定はこうなる。**

* Finding 1〜3：そのまま採用。
* Finding 4〜5：所有権部分だけ採用。算法・意味論変更は禁止。
* Finding 6：divergence の記録だけ採用。忠実化は不採用。
* Finding 7：lookup/catalog 所有だけ採用。型の全面分割は不採用。
* Finding 8：今回の監査範囲から削除。
* Finding 9：採用。ただし ownership slice ごとの package＋dependency 同時修正へ変更。

この限定版なら、YM2608 core と、その上で動く PMD/MML software の境界だけを切り直し、現在の音と動作を触らないという要求に一致する。

