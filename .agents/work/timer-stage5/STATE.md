# Objective
`timerplan.md` の段階5「PMDクロック指定の内蔵LFO遅延を統合」を、指定資料に基づく最小の製品コード変更として実装する。

# Constraints
- `timerplan.md` 全文の後に、指定された3解説資料、さらに直接の正本であるPMDマニュアル由来の2資料を全文読む。
- PMDソフトウェアLFOとYM2608ハードウェアLFO／タイマーの責務を混同しない。
- 指定された計画と5資料以外の外部情報は使わない。
- 関係する製品コードは変更前に全文読む。
- テストは作成も実行もしない。
- 既存挙動を基準線として、段階5以外へ範囲を広げない。
- この台帳と `timerplan.md` は製品変更に含めない。

# Plan
- [x] 指定スキルを全文再読する。
- [x] `timerplan.md` を全文読む。
- [x] 指定3解説資料を全文読む。
- [x] 直接の正本2資料を全文読む。
- [x] 段階5の正しい要求、誤認、完了条件を確定する。
- [x] 関係する製品コードを全文読み、変更許可リストを確定する。
- [x] 最小の製品コード変更を実装する。
- [x] 差分と法則適合を静的確認する。

# Confirmed
- `timerplan.md` はリポジトリ直下に存在する。
- 指定された5資料はすべて指定パスに存在する。
- 作業開始時の `git status --short` は空だった。
- 段階5は、PMD層で内蔵LFO遅延残量をPMDクロック通知により進め、物理層へフレーム単位の適用可否を渡すことを要求している。
- 物理内蔵LFOの周波数、位相、PMS、AMS、オペレーターAM許可と、直接シーケンサー経路は変更対象外である。
- YM2608タイマーBのオーバーフロー／IRQ一回が、PMD演奏時計の一クロックを進める契機であり、二回対一クロックではない。
- YM2608は物理周期と通知を所有し、PMDは通知一回を一クロックとして音楽状態を進める。
- 精緻化資料ではPMDの小文字 `t` は音楽テンポ、大文字 `T` はTimer B直接値である。古い解説資料の逆の説明は訂正前の情報である。
- PMDハードウェアLFOの `H` 第3値／`#D` はキーオンから適用開始までの遅延である。生値は内部クロック、`l音長` は全音符長の約数となる音長として指定される。
- ハードウェアLFO遅延中はPMS／AMSを一時的に0とし、遅延終了後に設定値を適用する。内蔵LFOの物理発振周波数や位相を停止する仕様ではない。
- `H` で遅延を省略した場合は以前の遅延値を保持する。
- PMDソフトウェアLFOのdelayは別機能であり、通常モードは内部クロック、拡張モードは約56Hzで進む。段階5のハードウェアLFO遅延へ流用しない。
- 現行のTimer B通知経路は `CompiledOpnaPlayer.prepareTimerBNotifications` → `timerBNotifications` → `PmdPerformanceState.prepare` である。
- 現行コンパイル済み遅延は通常FM、多声音声、FM3の各キーオンで `hardwareLfoDelayFrames` を呼び、`Fm4OpVoice.setDrivenNoteControls` へ渡してサンプル単位で減算している。
- 直接シーケンサーは `setNoteControls` と `lfoDelayRemaining` を使う別経路であり、段階5後も維持する。
- 遅延中に抑止すべきものはPMS／AMSの適用だけであり、共有 `Lfo.prepare` の位相進行ではない。
- 多声音声の独立したキーオン遅延を保つため、PMDクロック残量は `CompiledOpnaPlayer` の物理音声割当ごとに持つ。
- 生クロック遅延は値をそのままPMDクロック残量とし、音長遅延はキーオン時の四分音符クロック数から全音符クロックへ戻して解決する。
- 各描画フレームでは遅延適用可否を先に出力し、そのフレームのTimer B通知で残量を減らす。指定クロック数を待った次のフレームからPMS／AMSを適用する。
- 共有 `Lfo.prepare` は変更しておらず、遅延中も物理LFO位相は進み続ける。
- 直接シーケンサーの `setNoteControls` とサンプル単位 `lfoDelayRemaining` は残っている。
- 製品変更は許可リストの4ファイル、85行追加・35行削除である。バイナリ、生成物、1024行超追加はない。
- 共通メタデータ、Android共有エンジン、アプリKotlinのコンパイルは成功した。
- ビルド依存の既存OPNAホットパス監査は成功した。
- `git diff --check` は成功した。

# Rejected
- `timerplan.md` の「タイマーBオーバーフロー2回でPMD内部1クロック」候補は誤り。指定資料は一回のIRQをPMD一クロックとしている。
- `gemini exeplantion of timer logic 1.txt` の `t`／`T` 説明は、精緻化資料が明示的に訂正しているため採用しない。

# Unverified
- テストはユーザーの明示許可がないため作成・実行していない。
- 人間による製品経路の聴取は未実施であり、音楽的採用は未完了。

# Change Allowlist
- `shared-engine/src/commonMain/kotlin/com/example/timeboxvibe/engine/audio/mml/PmdPerformanceState.kt`
- `shared-engine/src/commonMain/kotlin/com/example/timeboxvibe/engine/audio/mml/CompiledOpnaPlayer.kt`
- `shared-engine/src/commonMain/kotlin/com/example/timeboxvibe/engine/audio/opna/OpnaDriverFrames.kt`
- `shared-engine/src/commonMain/kotlin/com/example/timeboxvibe/engine/audio/opna/Fm4OpVoice.kt`

# Next
最終報告で、実装内容、コンパイル結果、未実施の聴取を明記する。
