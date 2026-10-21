## 2026-10-21 チュートリアルモードのPR準備（v23）

- 最終更新日: 2026-10-21。ユーザー依頼によりAndroidの `versionCode` / `versionName` を22から23へ更新。最新main `71a61ec` を取り込み、`codex/tutorial-mode-77` からmain向け通常PRへまとめる。
- コミット対象: Issue #77の操作練習（基本編6章・追加レッスン5種）、coreのシナリオ/進捗、Androidの画面/保存分離/演出制御、回帰テスト、要件定義書・AGENT/CLAUDE・本進捗、Androidバージョン設定。実装・指摘修正は `claude -p`、独立レビュー・検証はCodexが担当。
- v23で隔離検証の `:core:test :desktop:test :androidApp:testDebugUnitTest :androidApp:compileDebugAndroidTestKotlin :androidApp:lintDebug --continue` が `BUILD SUCCESSFUL`（変更のないタスクは直前の成功結果を再利用）。core330件・Android226件・Desktop63件の計619件成功、Desktop既存3件スキップ、失敗/エラー0。生成されたAndroidManifestでもコード/名称が23で一致。Lintはエラー0・警告15・Hint1。記録: `.codex-analysis/tutorial/v23-validation-20261021.log`。
- 元の秘密設定は参照せず、証明書ファイルを開く既存 `GradlePropertiesTest` 2件は隔離検証から除外。実機/エミュレーターでのComposeテスト実行・表示・タッチ・読み上げ・プロセス終了/復帰は引き続き未検証。
- 次作業: コミット・push、規約の5分待機後にmain向け通常PRを作成。PR作成後も5分待機してレビュー・コメント・CIを確認する。以下は各作業時点の履歴。

## 2026-10-21 チュートリアルモード Issue #77 最終レビュー・自動検証完了

- 最終更新日: 2026-10-21。ユーザー指定どおり `claude -p` が実装・修正し、Codexが独立してコードレビュー、再現テスト、最終検証を実施。基本編6章と追加レッスン5種を実装。Issue: https://github.com/kisschan/mogura-game/issues/77 。ブランチは `codex/tutorial-mode-77`、変更は未コミット（push・PR作成なし）。
- レビュー指摘4件を修正: 古い「次へ」を世代＋説明シリアルで拒否、練習のダイス・演出の入力遮断をHUDより下に限定してメニューの読み上げも維持、山札枚数のテスト期待値を1へ修正、食事・逃走の効果音を世代＋イベントIDで重複判定。効果音の不具合はCodexが実操作による章再開で先に再現し、Claudeの修正後に同じプローブが成功した。
- 最終検証: `.codex-analysis/tutorial/verify.ps1` から `:core:test :desktop:test :androidApp:testDebugUnitTest :androidApp:compileDebugAndroidTestKotlin :androidApp:lintDebug --continue` を実行し `BUILD SUCCESSFUL`。core330件・Android226件・Desktop63件＝製品テスト619件成功、Desktopの既存3件スキップ、失敗/エラー0。別途Codexの効果音再現プローブ1件成功。Androidの全6レッスン完走、通常保存4状態の保持、設定・乱数の分離、進捗失敗/再試行/章からの再開、古い操作・相手役予約・演出通知、効果音の再生を確認。
- 追加のComposeテストはコンパイル成功。練習メニュー、演出中の操作・Semantics、360×740/390×844と文字倍率1/1.2/1.5、盤面位置、入口/完了、演出軽減を対象にしている。接続端末がないため、これらのテスト実行・実表示・TalkBack・実際のプロセス終了/復帰は未検証。HUDの修正はコードレビューとコンパイルまでの確認であり、実機で動作確認済みとはしない。
- Lintはエラー0、警告15、Hint1。従来14警告に加え、`DiceRouletteOverlay` の任意の `modifier` 引数の位置に関する `ModifierParameter` 警告が1件ある。機能上の失敗ではなく、依存関係・製品ビルド設定は変更していない。
- 元の秘密設定・署名ファイルを読み込まない検証用コピーを使用。証明書ファイルを読む既存 `GradlePropertiesTest` 2件は従来どおり除外。検証したKotlinソース147ファイルが作業ツリーと一致し、`git diff --check` も成功。レビュー用一時テストは検証コピーから削除し、再現用の原本だけ `.codex-analysis/tutorial` に保持。
- 記録: `.codex-analysis/tutorial/final-validation-20261021.log`、`review-feedback.txt`、`review-status.txt`、Claudeの `resume-20261021-retry-events.jsonl` / `review-followup-20261021-events.jsonl`。下記は実装時点の履歴であり、検証待ちの記述はこの最終結果で更新された。
- 残る確認: 実機/エミュレーターでComposeテストを実行し、表示・タッチ・読み上げ・プロセス終了/復帰を確認する。

## 2026-10-21 チュートリアルモード Issue #77 レビュー指摘の修正（Claude実装時点の記録）

- 最終更新日: 2026-10-21。ユーザー指定により `claude -p` が修正し、Codexが独立にレビュー・`verify.ps1` で検証する。ブランチは `codex/tutorial-mode-77` のまま。コミット・push・PR作成なし。Claudeはシェルを持たないため、Gradle・テストは一切実行していない。
- Codexの検証報告（HUD下の遮断層の修正前）: core 330件成功、desktop 63件成功/既存3件スキップ、Android JVM 221件成功（`AndroidTutorialTest` 17件を含む）。その後のHUD修正では `Modifier.belowPlayHud()` が `WindowInsets.safeDrawing` を非Composable文脈で読みコンパイル不可となったため、Composable側で読んだ上端insetを引数で渡す形に修正済み。この修正と指摘4の修正以降の結果は未確認。
- 指摘1（説明確認）: `acknowledgeTutorial(generation, explanationSerial)` に変更し、画面世代と説明シリアルの両方が一致した場合だけ進める。既定値なし。操作バーの「次へ」は表示時点の世代とシリアルを捕まえて渡す。
- 指摘2（ダイス・演出中の練習メニュー）: 練習中だけ、捕獲ダイス表示と捕獲・食事・体力消費の入力遮断層をHUDの下（上端の安全領域＋余白＋HUD高）から覆う。既存のHUDメニューの「この章をやり直す」「練習を終了」にタッチで届く。読み上げでは、演出中も練習HUDを残し、盤面と操作バーだけを従来どおり隠す。通常対戦の全画面遮断・Back消費・盤面の位置と大きさは変更なし。
- 指摘3: `AdditionalLessonsTest` の第5章開始時の山札期待値を1に修正（エンジンは変更なし）。
- 指摘4（章やり直し後の効果音、Codexの検証待ち）: 捕獲失敗・食事回復の効果音の重複防止を、イベントIDだけでなくプレイバック世代込みで判定する。`CaptureFailureSoundEffectTrigger`/`EatRecoverySoundEffectTrigger` は最後に鳴らした（世代, ID）を保持し、同じ世代ではそのID以下を鳴らさず、新しい世代では同じIDも1回だけ鳴らし、古い世代は鳴らさない。ViewModelの `captureFailureSoundEffectFor`/`eatRecoverySoundEffectFor(event, generation)` は現在の世代以外を無視する（既定値は現在の世代）。画面は表示中の世代を渡し、効果音のLaunchedEffectを世代ごとに作り直す。同じ世代の再通知とActivity再作成時の二重再生の抑止は従来どおり（ViewModelが状態を保持）。先に追加したテスト: トリガー単体（世代違いの同じID・古い世代の無音）各1件、`AndroidTutorialTest` 3件（NEST_EAT/DICE_ESCAPEの章やり直し、レッスン切替・終了後の再開始、練習後に開始した通常対戦の最初の逃走・食事）。
- 追加したAndroid JVMテスト `AndroidTutorialTest`（実ViewModel・手動タイマー・演出完了通知経由）: 説明確認の正当な確認／連打、レッスン切替・章再開・終了後の再開始での古い確認の無効化。全6レッスン完走（説明中・演出中は相手役の予約なし、完了は演出後のみ、完了後の予約なし、通常セーブ読込1回・書込0回）。通常保存の無し・途中・終了済み・破損で、練習後も保存と表示が不変で、途中/終了済みは練習前と同じ状態に再開、破損は上書き確認を維持。設定（人数・モグラ・巣・先手・CPU席・難易度）と乱数・駒透が練習で変わらないこと（同じシードの参照対局と一致）。進捗の読込失敗→未記録表示→次の保存で置換、保存失敗中も練習継続→再試行で最新の記録、プロセス終了後に章の先頭から再開（途中操作は再適用しない）。章再開・終了後の古い相手役予約、同じIDを再使用する捕獲・食事・体力消費・ダイス着地の古い完了通知が状態を変えないこと。
- 追加したinstrumentationテスト（端末未実行。Codexはコンパイルのみ可能）: `TutorialPlayScreenComposeTest`（ダイス表示中・捕獲/食事/体力消費の演出中にHUDメニューへタッチと読み上げで到達してやり直し/終了、通常対戦のダイス表示はメニューを遮断、360×740・390×844と文字倍率1/1.2/1.5で盤面とHUDが通常対戦と同じ位置・大きさで第1章を通して不動、設定画面からの入口・「次へ」・終了、古い「次へ」ボタンのタップ無効、完了画面）、`TutorialReducedMotionComposeTest`（演出軽減時のダイス・捕獲の自動完了と盤面不動）、共通ドライバー `TutorialComposeTestSupport`。
- 未完了: Codexによる `verify.ps1` での全モジュール回帰・instrumentationのコンパイル・Lint、端末/エミュレーターでのCompose実行・実表示・読み上げ・プロセス終了/復帰の確認。`.codex-analysis/tutorial` のレビュー用プローブのうち `ReviewTutorialLifecycleTest`/`ReviewTutorialPlaybackTest` は旧シグネチャ `acknowledgeTutorial(serial)` を使うため、そのままでは新APIでコンパイルできない（製品テストへ置き換え済み）。`ReviewTutorialSoundTest` は新シグネチャで変更不要（世代を省いた効果音の問い合わせは現在の世代として扱う）。

## 2026-10-20 チュートリアルモード Issue #77（実装途中・レビュー修正待ち）

- ユーザー指定により `claude -p` が実装し、Codexがコードレビューと隔離ビルドを担当。Issue: https://github.com/kisschan/mogura-game/issues/77 。作業ブランチは `codex/tutorial-mode-77`。コミット・push・PR作成は未実施。
- Claudeが利用上限で停止したため、完了扱いにはしない。次の解除表示は2026-10-20 23:10（日本時間）。実装担当をCodexへ変更するか、Claudeの利用枠が戻ってから続行するかをユーザーへ確認中。
- 追加済み: 要件16章と6・14・15章の注記、AGENT/CLAUDEとの整合。coreの `TutorialModel` / `TutorialScenario` / `TutorialSession` / `TutorialSteps` / `TutorialScenarios` / `TutorialProgress`。基本編6章、追加レッスン5種、合法手の再生による開始状態、操作の許可判定、章再開、進捗Codec。
- 追加済み（レビュー修正未反映）: Androidの `TutorialProgressRepository` / `TutorialScreens`、ViewModelの練習セッション・通常保存との分離・進捗保存・演出/相手役の制御、設定/再開画面/遊び方からの入口、HUDメニュー、対象の枠、完了画面。通常のゲームルール・保存形式v3・デスクトップUIは変更していない。
- Claude作成のcoreテスト: `TutorialSessionTest` / `TutorialLessonsTest` / `AdditionalLessonsTest` / `TutorialProgressTest` と共通ヘルパー。追加後の31件中30件成功、1件失敗。基本編と追加レッスンの完走・各章の再開・許容分岐・拒否時不変性・体力/得点/逃走/強奪/防衛・進捗の版互換を確認。失敗は `AdditionalLessonsTest.kt:135` の山札期待値（2ではなく1。手番開始時の1枚は引かれている）。
- 最初のcore全体検証は307件中306件成功。公開直後の案内文に関する当時の失敗は、Claudeによる `LARVA_REVEALED` の修正後に解消した。
- Androidの既存JVMテスト204件は成功。Codexのレビュー用一時テスト2件では、練習中に通常保存を再読み込みしない検査が成功し、別レッスンへ切り替えた後に古い説明確認を送る検査が失敗。`acknowledgeTutorial` は説明シリアルだけを比較し、新しいセッションで番号が再使用されるため、画面世代も照合する必要がある。
- Codexの別のレビュー用テストで、Androidの操作入口・演出完了通知・手動CPUスケジューラーを通して全6レッスンを完走し、通常セーブへの書き込み0回・通常勝利表示なし・完了後の予約なしを確認。一時テストは `.codex-analysis/tutorial` に保存し、製品のテストソースには追加していない。
- 静的レビュー指摘: 捕獲ダイスの全画面入力遮断とBackHandlerにより、練習のHUDメニューから「終了/章やり直し」を常に利用する要件を満たさない。練習中の捕獲表示にも到達できる終了/再開導線を設け、通常対戦の背面入力遮断を維持する必要がある。
- Android本体と既存instrumentationソースのコンパイル成功。`:androidApp:lintDebug` はエラー0、警告14、Hint 1。端末がないため、Composeの実行・360×740/390×844の実表示・文字拡大・読み上げ・実機のプロセス終了/復帰は未検証。
- 検証は `.codex-analysis/tutorial/verify.ps1` のソース専用コピーで実行。署名や秘密情報を含み得る元の設定を読み込まず、証明書ファイルを参照する既存 `GradlePropertiesTest` 2件は対象外。変更前の基準はcore299件・Android204件・Desktop63件成功、Desktopの既存3件スキップ。
- 残作業: 上記レビュー指摘の修正、Androidの保存状態4種・進捗の失敗/再開・古い通知・UI導線/レイアウトを覆う製品テストの追加、全モジュールの最終回帰、端末検証。詳細と再現用ソースは `.codex-analysis/tutorial/review-feedback.txt`、検証ログは同ディレクトリに保持。

## 2026-10-14 CPU難易度・相手対応戦略のPR準備（v22）

- 最終更新日: 2026-10-14
- ユーザー依頼により、作業中のAndroid `versionCode` / `versionName` を21から22へ更新（最新mainは20）。
- main `b4c846f`（CPU対戦と勝利BGMのPRをマージ済み）を基点に、`codex/cpu-difficulty-v22` へCPU難易度・相手対応戦略・保存v3・回帰テスト・仕様更新をまとめる。
- 検証: 下記の最終検証572件成功・既存3件スキップ、Android計測テストのコンパイル成功。隔離環境もv22へ合わせて4タスクを再確認し `BUILD SUCCESSFUL`（変更のないテストは直前の成功結果を再利用）。バージョン値の一致と増分も確認。実機の操作・思考時間は未検証。
- 次作業: コミット・push後、main向け通常PRを作成。規約の5分待機後にレビュー・コメント・CIを確認する。

## 2026-10-14 勝利時BGM

- [x] ユーザー提供の `the_champion_s_burrow.mp3` を `assets/audio` とAndroidの `res/raw` に追加。元添付と両リソースのSHA-256一致を確認。
- [x] Android・デスクトップで勝者が確定した結果表示時に通常曲から勝利曲へ切り替えてループ再生。引き分けでは通常曲を維持し、再戦時は通常曲へ復帰する。
- [x] Androidの音量・ミュート・バックグラウンド停止・フォーカス制御を維持。曲変更時に旧プレイヤーを解放し、結果表示の更新では再生成しない。デスクトップでは曲変更直前に開始された旧再生スレッドも停止する。
- 検証: mainに今回のBGM差分だけを加えたソースを、秘密情報を参照しない隔離環境で確認。`:core:test :androidApp:testDebugUnitTest :desktop:test :androidApp:compileDebugAndroidTestKotlin --continue` が成功。core 278件・Android 201件・Desktop 63件成功、Desktopの既存条件付き3件スキップ、失敗/エラー0。追加7件で切り替え・再戦・音量・ライフサイクル・MP3全フレームのデコードを確認。製品設定を参照するcoreの設定検査3件は隔離検証の対象外。
- コミット対象: 勝利BGMの13ファイルのみ。mainをベースとする `codex/victory-bgm` から通常PRを作成する。
- 実機での聴取確認は未実施。製品ビルド設定・署名・配布は変更していない。

## 2026-10-14 相手の体力・得点に応じた「つよい」CPU

- 最終更新日: 2026-10-14
- [x] ユーザー依頼により `claude -p` で公開ソースとルールを渡し、戦略案と実装レビューを取得（ツール無効、セッション保存なし）。記録は `.codex-analysis/cpu-adaptive/claude-strategy.txt` と `claude-review.txt`。帰巣で阻止される強奪の過大評価、次手番の生存、強奪時の食事判断の一貫性を改善。勝利時のHP条件を外す案は、既存エンジンが体力消費後に勝利判定することを確認して採用しなかった。
- [x] `OpponentAssessment.kt` を追加し、公開された相手のHP・得点・レンコウ・位置から得点競争、生存重視、首位への強奪、自巣防衛を選ぶ。2人戦だけで相手の脱落勝ちを狙い、相手に1回の掘りで回復へ届く可能性があれば待ち勝ちを決めつけない。
- [x] 相手の次の帰巣/強奪での勝利を評価し、自巣の泥棒を追い出す、掘り候補の回転で相手の勝利経路を切る判断を追加。強奪前の持ち主の帰還・脱落リスク、多人数戦で下位を妨害する利益の小ささも考慮する。
- [x] 得点競争では安全な帰巣と次手番のHP余裕を残して得点を急ぐ。次手番に不確かな捕獲だけに生存を頼る地上停止を避け、局面ごとに価値/距離から移動目標を選ぶ。掘り候補間で方針・目標を固定し、評価の比較基準をそろえる。
- 変更対象: `HardCpuStrategy.kt`、新規 `OpponentAssessment.kt`、`StandardCpuStrategy.kt`内の公開盤面ヘルパー追加。ふつう・やさしいの判断、合法操作、ゲームルール、保存形式v3、Android UIは維持。要件15章・CLAUDEに追記。
- テスト: 新規 `OpponentAwareCpuTest.kt` の11局面（防衛、相手HP/得点による食事判断、首位強奪、帰巣妨害、強奪失敗リスク、2人限定生存戦略、次の掘りでの回復、次手番の生存など）。旧CPUで防衛・首位強奪・得点競争の3件が失敗することを確認して修正。
- 最終検証: 秘密情報を読まない既存隔離環境で `:core:test :androidApp:testDebugUnitTest :androidApp:compileDebugAndroidTestKotlin :desktop:test --continue` 成功。core297件・Android212件・Desktop63件成功、既存Desktop3件スキップ、失敗/エラー0（並行作業のBGMテストも含む）。実設定を読むcore設定検査3件は従来どおり対象外。CPUの最終ソースと検証コピーの一致も確認。
- 対局検証: 比較288局＋未使用シード416局＋混在12局＝716局が合法・進行・復元後同一判断を保って完走。従来と同じ96局ではつよい58勝/ふつう38勝（前版51勝/45勝）。未使用96〜191シードの席順反転192局ではつよい123勝69敗（64.1%）。やさしいに対して92勝4敗。
- 多人数の確認: 未使用96〜127シードでつよい1席を全席ローテーションし、残りをふつうにした結果、3人戦32勝/96局、4人戦32勝/128局。戦術の動作は確認できたが、この標本では多人数戦での勝率優位は確認できない。今後の強化余地として明示する。
- 未実施: 実機上の思考時間測定・操作確認。コミット・pushは行っていない。並行して追加されたBGM関連ソース/音声資産には変更を加えていない。

## 2026-10-14 CPUごとの3段階難易度

- 最終更新日: 2026-10-14
- [x] Androidの各CPUに「やさしい・ふつう・つよい」を追加。ふつうは既存判断を維持。席ごとの選択を開始時にプレイヤーIDへ紐付け、人間切替時は選択保持、人数変更時はリセットする。
- [x] やさしいは目先の生存・帰巣・エサへの接近と捕獲成功率を優先。つよいは公開通路から帰巣の手番/体力消費、捕獲・強奪の効率、勝利可能性を評価。未知の帰巣経路を安全扱いせず、体力不足なら回復する。ゲーム用乱数や非公開情報は参照しない。
- [x] 保存v3にCPUのID別難易度を追加。v1は全員人間、v2のCPUはふつうへ移行。v3の未知の値・重複ID・CPU集合との不一致は破損として拒否する。
- 実装ファイル: core `CpuDifficulty.kt` / `EasyCpuStrategy.kt` / `HardCpuStrategy.kt`、`StandardCpuStrategy.kt`内の公開盤面経路ヘルパー（標準判断は変更なし）。Android `AndroidGameViewModel.kt` / `GameScreen.kt` / `MainActivity.kt` / `GameSaveRepository.kt` / `GameSaveCodec.kt`。要件15章・AGENT・CLAUDEを更新。
- 追加/更新テスト: `CpuDifficultyTest.kt` / `CpuDifficultyMatchTest.kt` / `StandardCpuStrategyTest.kt`、`AndroidCpuDifficultyTest.kt`、`GameSaveCodecTest.kt` / `CpuGameScreenTest.kt`。未実装APIによるRedを確認後に実装し、満タン時の不要な食事もRedから修正。
- 最終検証: 既存の署名情報を読まない隔離環境で `:core:test :androidApp:testDebugUnitTest :androidApp:compileDebugAndroidTestKotlin :desktop:test --continue` が成功。core285件・Android JVM207件（Codecのホスト検証を含む）・Desktop61件成功、Desktopの既存条件付き3件スキップ。失敗/エラー0。実設定を読むcoreの設定検査3件は隔離環境から除外。製品ビルド設定は今回変更していない。
- 対局比較: 固定48シード×席順反転×3組の288対局と2〜4人の混在12対局が完走し、合法手・進行・復元後の判断一致を確認。勝敗はやさしい/ふつう=6/90、ふつう/つよい=45/51、やさしい/つよい=9/87。脱落数は順に17/1、4/3、17/2、平均手番数は19.94、17.79、20.00。つよいとふつうの差は小さく、この比較は全局面での優位を保証しない。
- 未実施/TODO: Composeの320dp縦画面・難易度選択テストはコンパイル確認まで。実機/エミュレーターでの描画・タッチ・端末上の思考時間は未検証。APKの署名・配布・コミット・pushは行っていない。
- 既存変更の保持: 作業開始時のAndroidバージョン21更新とその進捗記録を保持。

## 2026-10-14 Androidバージョン更新

- 最終更新日: 2026-10-14
- [x] Android `versionCode` / `versionName` を `20` から `21` に更新。
- 検証: バージョン値の一致・増分と差分の形式を静的確認。通常のGradle実行は署名情報を自動で読み込むため未実施。

## 2026-10-13 Android CPU対戦

- ユーザー承認済み仕様: Androidのみ、2〜4席の人間/CPU混在・全CPU観戦、標準1段階、行動を追える自動進行。
- [x] 要件15章と既存ガイドへCPU仕様を追加。既存のゲームルールとデスクトップの操作を維持する。
- [x] Astraによる公開情報専用のCPU判断・合法操作・core回帰テスト13件を追加。2〜4人×12シードの36対局が合法手・保存則を保って完走。勝利可能な帰巣後に食事で得点を減らさない修正も反映。
- [x] Claude Opus 5.5（max、`claude -p`）によるAndroid設定・自動進行・保存v2・UI回帰テストを統合。CLIは主要変更後に利用上限で停止し、残る修正・検証をCodexとAstraで完了。
- [x] 各席の人間/CPU選択、全員CPU観戦、CPU表示と人間入力の抑止、450ms単位の行動、捕獲の600ms公開・600ms回転、食事/消費体力の既存演出を実装。背景・設定・ルール・音量・履歴・メニュー表示中は自動進行を停止。
- [x] 保存成功後のみ画面反映/次行動を許可。保存失敗時は同じ結果を再保存し、停止中の演出完了通知は復帰後一度だけ処理。v2のCPU担当IDを検証し、v1は全員人間として読み込む。
- [x] ダイスの古い完了通知が同じ対局の次の捕獲を進めることを統合テストで再現し、対局generationと捕獲IDで拒否する修正を追加。画面側もIDを不変localに保持。Astraの最終独立レビューで残る不具合の指摘なし。
- [x] 標準CPUのAndroid統合テストは2〜4人×4シードの12対局を保存・演出完了・終局まで通過。coreの36対局と合わせて公開情報/合法手/保存則/自動進行を検証。
- 検証結果: core278件、Android JVM196件＋JSON Codec補足7件、desktop64件中61件成功・既存条件付き3件スキップ。計545件中542件成功、失敗/エラー0。Android debug（未署名）ビルドと全instrumentationテストソースのコンパイル成功。
- 実装ファイル: core `cpu/CpuAction.kt` / `CpuObservation.kt` / `CpuController.kt` / `StandardCpuStrategy.kt`、既存controllerの機会判定公開範囲。Android `CpuStepScheduler.kt` / `AndroidGameViewModel.kt` / `GameScreen.kt` / `DiceRouletteOverlay.kt` / `MainActivity.kt` / `GameSaveRepository.kt` / `GameSaveCodec.kt`。回帰は `StandardCpuStrategyTest.kt` 13件、`AndroidCpuGameTest.kt` 27件、`CpuGameScreenTest.kt` 4件、Codec追加3件。
- 検証方法: 既存の `.codex-analysis/move-selection` 内の署名情報を参照しない隔離ビルドを使用。通常のゲームソース/リソースのみ同期し、製品の署名情報・ユーザーGradle設定を読み込まない。
- 検証環境: JBR21・オフラインGradle・隔離設定/キャッシュ。実設定を読むcoreの設定検査3件は対象外。JSON Codecは公開JSON実装を隔離JVMのみに追加して実行し、製品依存関係は変更していない。
- 未実施/TODO: 接続Android端末・エミュレーターがないため、Composeテストの端末実行、実タッチ・描画・実機バックグラウンド/プロセス終了の検証は未実施。追加の `lintDebug` はオフラインキャッシュにEspresso 3.5.0のruntime依存がなく、AndroidTest lint model生成で停止。通常のテスト・debugビルドとは分離して記録する。デスクトップCPU UIは対象外。
- PR準備: ユーザーの依頼により、最新の `origin/main`（`d618746`）から `codex/android-cpu-opponent` を作成。このCPU対戦の実装・回帰テスト・仕様/検証記録を1コミットにまとめ、main向け通常PRとして提出する。作成後は規約どおり5分待ち、レビュー・コメント・CIを再確認する。

## 2026-10-01 保存再開・入力状態の修正と回転フィードバック

- 対象: `kisschan/mogura-game`、main `4b625073da7fdbe933862c51ae374aa23fb796de` から分離した `codex/bugfix-animation-review-20261002`。新規チェックアウトのため既存未コミット変更なし。GPT-6.1 SolでCore・Android・独立レビューを分担。
- [x] 捕獲エサの逃走結果が確定済みの途中保存を再開した際、演出待ちENDで手番が止まる不具合を修正。未処理の確定出目だけを一度解決し、未確定出目・選択待ちを保持。
- [x] 合法な強奪フェーズ以外の対象選択を拒否。保存された掘りプレビューが非隣接または他プレイヤー占有の場合は復元を拒否。
- [x] Desktopの人数・先手選択キャンセルで既存ゲームを置き換える不具合を修正。終局時の案内・現在プレイヤー表示・操作アクセントを勝者/ドローの結果表示へ変更。
- [x] Androidのダイス完了通知で最新コールバックを参照し、重複通知を抑止。システムの動き軽減/無効時には既存演出を静止表示し、未確定出目を自動確定しない。選択枠は不透明のまま残す。
- [x] 掘りタイルの回転画像だけを140msで時計回り補間。270°→0°・連打・候補切替・直線/十字の対称形を確認。保存形式・合法判定・盤面固定・全文ログは維持。
- 実装ファイル: Core `MoguraGameController.kt`、Android `AndroidGameViewModel.kt` / `GameScreen.kt` / `DiceRouletteOverlay.kt` / 既存捕獲・食事・体力消耗Overlay / 新規 `AndroidGameMotion.kt` / `DigPreviewMotion.kt`、Desktop `MoguraGameApp.kt` / 新規 `DesktopGameSetup.kt` / `DesktopGameStatus.kt`。
- 追加回帰: Core6件、Android JVM9件、Desktop10件、Compose9件。修正前にはCore5/6件・Android8/9件・Desktop5/10件の失敗を観測。独立レビューの対称タイル角度指摘もRed2件から修正。
- 最終検証: `.local/verify.ps1 test build :androidApp:compileDebugAndroidTestKotlin :androidApp:lintDebug --console=plain` が `BUILD SUCCESSFUL`。Core268件成功、Desktop64件中61件成功・既存条件付き3件スキップ、Android JVM169件成功（合計501件中498件成功、失敗/エラー0）。Android lintはエラー0・警告14・ヒント1。Compose全テストソースのコンパイル成功。Swingの開始・掘りプレビュー・終局を実コンポーネント描画で確認。
- 検証環境: チェックアウト内のJDK17.0.20.1、Gradle9.4.1、SDK36.1/Build Tools36.1.0、隔離キャッシュ、今回生成した検証用debugキーを使用。Windows sandboxの署名ロック正規化制限を自動承認済みの実行権限調整で解消。製品のGradle設定・既存truststore・署名設定は変更なし。
- 独立レビュー: GPT-6.1 Solで全変更を確認。対称タイルの指摘修正後、残るコード上のブロッカーなし。
- 未実施/TODO: 接続Android端末・エミュレーターがないためComposeテストの端末実行、実タッチ・TalkBack・バックグラウンド中断/プロセス終了/端末再起動後の復元は未確認。既存の得点到達とHP0の同時発生は体力消耗を優先する挙動を維持し、優先順位の要件解釈は別途確認対象。移動経路に沿う新規演出は提案のみで未実装。
- 次作業: この変更だけをコミット・pushし、main向け通常PRを作成。merge・公開・デプロイは実施しない。

# PROGRESS.md

最終更新: 2026-10-14

## 現在の状況
- [x] 2026-09-25: PR #68追加レビューの掘る待ちの停止状態を修正。PLAYING/DIGで掘る候補がある場合は準備済みタイルまたは配置待ちを必須とし、既存の `digTargets()` を再利用して復元時に検査する。候補がない正規状態からの自動進行は維持する。保存/JSON読み込みも復元検査を通す。追加1件をRed→Greenで確認し、core 259件・Android JVM 160件＋補足Codec 4件・desktop 51件成功/3件スキップ、計477件中474件成功・失敗/エラー0。計測テストのコンパイルも成功
- [x] 2026-09-25: PR #68の追加3指摘をTDDで修正。盤面の地下/ホットゾーン16マスのタイル配置を必須とし、盤面・山札・捨て札・掘る途中の実物タイルを26枚の正規構成と照合する。エサの巣マス配置も拒否。欠落タイルの山札移転・タイル追加/欠落/別形状への置換・巣上エサの3テストを追加し、JSON破損検査と正規構成の既存シナリオを補強。core 258件・Android JVM 160件＋補足Codec 4件・desktop 51件成功/3件スキップ、計476件中473件成功・失敗/エラー0。計測テストはコンパイル成功
- [x] 2026-09-25: PR #68の追加レビューで、保存データ全体のエサの種類別枚数を検査。盤上・ストック・捨て札・所持・貯蔵・捕獲/強奪後の選択待ちを計上し、2〜3人の12枚/4人の13枚の正規構成と照合する。過剰・欠落・別種への置換のテストをRed→Greenで確認し、JSON破損検査を補強。既存シナリオも正規枚数のテストデータへ修正。全検証が成功し、core 255件・Android JVM 160件＋補足Codec 4件・desktop 51件成功/3件スキップ、計473件中470件成功・失敗/エラー0。計測テストはコンパイル成功
- [x] 2026-09-25: PR #68の追加レビューに対応し、保存されたエサの逃走表をカード種別ごとの正本と照合する。全5種の検査を追加して修正前の失敗を確認し、空の表・誤った方向のJSONが破損扱いになる検査も補強。修正後はcore 254件、Android JVM 160件＋補足Codec 4件、desktop 51件が成功・3件スキップ（計472件中469件成功、失敗・エラー0）。計測テストのコンパイルも成功
- [x] 2026-09-25: 通常PR #68のレビュー2件をTDDで修正。次の手番に過去の移動先選択を持ち越さず、新規ゲーム開始時は駒の透過表示を解除する。追加2件の失敗を先に確認し、修正後はAndroid JVM 160件と補足Codec 4件が全成功。core 253件成功、desktop 51件成功・3件スキップを合わせて471件中468件成功、失敗・エラー0。計測テストのコンパイルも成功。GitHub Actionsは初回・再実行ともAndroid SDKのセットアップで停止し、CI上のTest/Buildは未実施
- [x] 2026-09-25: ユーザー依頼によりAndroid `versionCode` / `versionName` を19から20へ更新。最新main `61a2282` を基点とする `codex/android-resume-v20` から、自動保存・途中再開の実装をmain向け通常PRにまとめる。隔離環境もv20に揃えて下記4タスクを再確認し `BUILD SUCCESSFUL`（変更のないテスト結果はGradleのUP-TO-DATE判定で再利用）
- [x] 2026-09-25: Androidの自動保存・途中再開を実装。直近1ゲームを操作ごとに保存し、起動時に「続きから」または「新しく始める」を表示する。終了済みゲームは「前回の結果を見る」で確認でき、新規ゲームの保存成功まで前回データを保持する
- [x] coreに独立したスナップショットと整合性検査を追加。盤面・山札とエサの順序・プレイヤー・掘る候補別の回転・確定済みダイス・捕獲後の選択・強奪/帰巣後の食事権利・全履歴を復元する。復元時にセットアップや抽選を再実行しない。演出中の保存からは未処理の進行だけを完了し、確定済みの食事・消耗・演出・効果音を重複適用しない
- [x] Androidはバージョン1のUTF-8 JSONを内部ストレージ `filesDir/game-save/current.json` へ `AtomicFile` で保存する。プロセス共通の直列I/Oで保存と読み込みの順序を保証し、保存完了後に結果を表示する。失敗時は入力を止め、保持した同一データだけを再保存する。破損・未対応バージョンは画面で通知し、自動上書きしない。移動先の仮選択と駒の透過設定も保持する
- 実装ファイル: coreの新規 `persistence/GameSnapshot.kt`、`Player.kt`、`GameEngine.kt`、`MoguraGameController.kt`。Androidの新規 `GameSaveRepository.kt`、`GameSaveCodec.kt`、`GameResumeScreen.kt` と、`AndroidGameViewModel.kt`、`GameScreen.kt`、`MainActivity.kt`。要件14章・`AGENT.md`・`CLAUDE.md`にも保存仕様を反映
- 追加テスト: coreの `GameSnapshotTest` 5件（2〜4人の完走中の各操作前後、捕獲、強奪など）、Android JVMの `AndroidGameSaveTest` 9件（保存前の表示抑止、失敗と同一出目の再試行、上書き取消、演出中断、終了結果など）。端末用に `GameSaveCodecTest` 4件、`GameSaveStorageTest` 1件、`GameResumeComposeTest` 2件を追加。既存MainActivity計測テストは専用メモリ保存へ切り替え、実際の保存データを使わない
- 最終検証（2026-09-25）: 秘密情報を参照しない隔離環境で `:core:test :desktop:test :androidApp:testDebugUnitTest :androidApp:compileDebugAndroidTestKotlin` が `BUILD SUCCESSFUL`。core 253件成功、desktop 51件成功・3件スキップ、Android JVM 158件成功。さらにAndroid由来の公開JSON実装を検証環境だけに導入し、Codec計測テスト4件をJVMで実行して全成功（合計469件中466件成功、3件スキップ、失敗・エラー0）。ソース/リソース131ファイルが本体と検証コピーで一致。最終実行で変更のないcore/desktop結果は同じ作業内の直前の成功結果を再利用。本番設定を参照する設定検査は除外し、製品側の依存関係・署名・ビルド設定は変更していない
- 未実施/TODO: 接続Android端末がないため、追加7件を含む計測テストはコンパイルまで。Android実装のAtomicFile、Compose画面、バックグラウンドでの `adb shell am kill com.moguru.game.paid`、アプリ強制停止後の起動、端末再起動後の復元は実機未確認。次作業は端末上で計測テストを実行し、回転選択待ち・出目確定後・捕獲/食事/消耗演出中からの復元を確認する。署名・APK生成・端末への配布は未実施

## 以前の作業
- [x] 2026-09-13: ユーザーが実機でログ表示・盤面固定を確認し、問題なしと報告。新ブランチ `codex/android-v19-fixed-board` からmain向け通常PRを作成するため、Androidの `versionCode` / `versionName` を18から19へ更新
- 最終検証（2026-09-13）: 最新mainの効果音重複再生防止を維持した差分で、隔離環境の `:core:test :desktop:test :androidApp:testDebugUnitTest :androidApp:compileDebugAndroidTestKotlin` が `BUILD SUCCESSFUL`。core 248件成功、desktop 51件成功・3件スキップ、Android 149件成功（計451件中448件成功、失敗・エラー0）。coreとdesktopは変更がないため前回結果を再利用。計測テストはコンパイル成功で、端末上の自動実行は未実施。対象ソース124ファイルの一致を確認済み。本番設定を参照する設定検査は除外し、秘密情報の読み込み・署名・APK生成は行っていない
- [x] 2026-09-11: ユーザー承認済みの全文ログ表示を維持し、盤面をHUD直下に隙間なく固定。長文の案内・ログと最大操作列に必要な領域を画面幅と文字設定から予約し、現在のログ・フェーズ・手番によって盤面位置やサイズを再計算しない構造に変更。増減する余白は盤面の下側へ集約
- [x] 盤面固定のCompose回帰テスト6件を追加。360×740／390×844・文字100%／150%で掘る→配置・回転→移動→終了・演出→次手番を確認し、捕獲・ルーレット・捕獲演出でも盤面四辺とHUD直下への接触を検査する。既存の全文ログ・案内・ボタン・履歴検査を維持
- 検証: 秘密情報を使わない既存隔離環境で `:androidApp:testDebugUnitTest :androidApp:compileDebugAndroidTestKotlin` が最終版でも `BUILD SUCCESSFUL`。Android単体148件全成功、失敗・エラー・スキップ0。追加6件を含む計測テストのコンパイルも成功。本体と検証コピーの3変更ソースの一致を確認済み。エミュレーターがofflineのため実画面・UIテスト実行は未実施。署名・APK生成・端末反映は行っていない
- [x] 2026-09-10: 写真で切れていた操作案内・最新ログを常時全文折り返しへ変更。案内展開でログを隠す操作を廃止し、最新ログのタップでも履歴を開く。操作バーの固定高さを撤去し、本文の実測後の残り領域に盤面全体を収める
- [x] Android表示の最新5件制限とコントローラーの80件制限を解除。演出中の表示固定は維持し、ゲーム開始からの全ログを保持する。新規ゲーム開始時に履歴をリセットする
- [x] 履歴ポップアップに全件数と新しい順・スクロールの案内を追加。盤面領域内で最大420dpまで広げ、本文を13sp・行高20spに変更。閉じる操作は44dp以上を確保する
- [x] 掘る候補の画像と「めくり／山札」を縦に並べ、省略表示を撤去。読み上げにはタイル形状と選択状態を含める
- [x] 全履歴保持の単体テストと、初期表示・文字150%・配置待ちで実際の文字欠けを調べるCompose回帰テストへ更新。固定高さの数式だけを検証していた旧テストは、実際に使う盤面フィット計算とCompose文字レイアウト検査に置換
- 検証: 署名情報を使わない隔離環境で `:core:test :desktop:test :androidApp:testDebugUnitTest :androidApp:compileDebugAndroidTestKotlin` を確認。core 248件・Android 148件が全成功、desktopは54件中51件成功・3件スキップ。合計450件中447件成功、失敗・エラー0。Android計測テストのコンパイルも成功。変更した本体と検証コピーの一致を確認済み。本番設定を参照するcoreの設定検査テストは従来の隔離手順どおり除外し、秘密を含む可能性のある本番設定ファイルは読み込んでいない
- 未実施: 接続Android端末がないため、Composeテストの端末上実行と実画面確認。今回のコード変更は端末への配布・インストールを行っていない
- [x] Android `versionCode` / `versionName` を `17` から `18` に更新
- [x] Androidの選択中の移動先を、緑の静的内枠から白下地＋赤（`#D32F2F`）の四隅ターゲット枠へ変更。固定枠を常時残し、赤い補助ハローだけを1.6秒周期でゆっくり脈動させる
- [x] 遊び方に「白赤の四隅＝選択中の移動先」の静的凡例を追加し、アニメーション無効・軽減時にも不透明な静止枠を残す表示規約を `AGENT.md` に記録
- 検証: 署名・証明書設定を展開しないmain基準の隔離作業ツリーで `:androidApp:testDebugUnitTest :androidApp:compileDebugAndroidTestKotlin --no-daemon --max-workers=2` が `BUILD SUCCESSFUL`。Android単体テスト151件と、追加したCompose計測テストを含むテストソースのコンパイルが成功
- 未実施: 接続端末・エミュレーターがないため、Compose計測テストの実行と実画面での脈動・アニメーション無効時の確認
- [x] PR #64レビュー対応: 食事音の再生済みイベントをViewModelで保持し、Activity/Effect再生成時の二重再生を防止。次ゲームの食事音と古いイベントの再抑止も確認する
- [x] Android `versionCode` / `versionName` を `15` から `16` に更新。UI改善と強奪後レンコウの修正をmain向け通常PRへまとめる
- [x] PR用ブランチ `codex/android-ui-v16` を最新main `42e4644` から作成し、食事・消耗演出とUI改善を統合。mainのActivity再生成時の失敗音重複防止を保持する
- [x] ⑧の回転アイコンを時計回りの「↻」に合わせ、「右に90°」と現在角度を併記。読み上げも方向と角度を明示し、文字拡大時は掘る操作行の高さを調整する
- [x] ⑧のCompose回帰テスト4件を追加。L字タイルで時計回り4回の一周、配置確定前のゲーム進行不変、確定後の接続方向、360×740の標準/文字1.5倍表示を検証する
- [x] ⑤を遊び方に合わせ、強奪後レンコウをエサだけの即時貯蔵・得点に変更。モグラは相手巣に残り、所持状態にならない。要件8章・`AGENT.md`・`CLAUDE.md`に明記し、Androidの案内と読み上げにも即得点を反映
- [x] ⑤の得点移転・選択カード・重複操作・位置維持・勝利得点・既存の体力消耗順を確認。通常捕獲のレンコウと強奪後にタベる挙動の回帰テストも補強
- コミット済み（③④）: `23c7da1` `fix(android): 操作の省略と終了を区別し次操作を常時案内`
- [x] Androidの③スキップ/終了の意味を明確化。「移動しない」「捕獲しない」など段階別の名称にし、早期の「手番終了」には「残りを省略」を併記。ENDと巣の任意食事では終了ボタンを1つに統合する
- [x] Androidの④次操作案内を直近イベント・捕獲結果から分離して常時表示。掘る場所選択/配置待ち、移動確定、捕獲、強奪、巣の食事、演出中を状態に合わせて案内する
- [x] 文字拡大時は標準操作行の高さも拡張し、移動先座標と早期終了の補足の折り返しに対応。通常バー120dp・配置待ち110dp・捕獲結果144dpを基準に盤面を残り領域へ収める
- コミット範囲（③④、2026-09-07）: `AndroidGameViewModel.kt`、`GameScreen.kt`、`RulesScreen.kt`、新規 `ActionGuidanceTest.kt` / `ActionGuidanceComposeTest.kt`、既存UI文言・レイアウト契約テスト、`AGENT.md`、本記録の9ファイル
- コミット済み（②）: `85308f6` `fix(android): レンコウ中のエサをモグラの手元に表示`
- [x] Androidの全プレイヤー駒に所持エサ情報を渡し、レンコウ中の正本エサタイルを手元（下中央）に駒幅40%の正方形で表示する
- [x] レンコウ表示を駒の移動・手番交代・帰巣時の納品に同期し、「透過」へ連動させる。読み上げにエサ名とレンコウ中である旨を追加する
- [x] レンコウ表示の状態遷移を検証する単体テスト4件と、正方形配置・非表示・タップ・透過を確認するComposeテスト4件を追加する
- コミット範囲（2026-09-07）: Androidレンコウ表示、関連テスト、盤面トークン規約・進捗記録の7ファイル
- [x] Androidの移動を「盤面で選択 → このマスへ移動で確定」に統一。再タップでは移動せず、最初の候補を仮選択し、単一候補でも選択枠を表示する
- [x] 移動候補の切替を「次の移動先」と明記し、確定ボタンに列・行を2行目で表示。操作バーの高さを維持して確定ボタンの横幅を確保する
- [x] 移動の読み上げ説明・遊び方・`AGENT.md`の操作規約を更新し、単体テスト2件とCompose回帰テスト9件を追加する
- コミット範囲（2026-09-07）: Android移動UI修正、関連テスト、遊び方・操作規約・進捗記録の6ファイル
- [x] 次回AABリリース用にAndroid `versionCode` / `versionName` を `11` に更新
- [x] 盤面、穴タイル、エサ、プレイヤーのモデル実装
- [x] 移動、タイル配置、ターン進行のエンジン実装
- [x] 文字化けしていたソースとドキュメントの修正
- [x] Swing GUI の追加
- [x] 要件定義書 `moguru_requirements_v2(3).txt` に合わせた捕獲後選択・地上移動・補充ルールの修正
- [x] 強奪後も即連行せず `タベる / レンコウ` 選択に入るよう修正
- [x] エサ画像から読める逃走ダイス目と8方向逃走を実装
- [x] 2026-07-11共有の正式版エサタイル5画像を正本として、得点・回復・逃走方向・表面意匠の要件を確定
- [x] 正式版エサタイル要件をモデル、テスト、Android/Swing画像へ反映
- [x] 共有写真のモグラを正方形アプリアイコンへ整え、Android/Swingへ反映
- [x] Androidのアプリ名とセットアップ見出し、Swingのウィンドウ名と見出しを正式タイトルへ統一
- [x] 掘る操作を「めくってから回転して確定」する2段階操作に修正
- [x] DIGで掘れる方向の矢印表示と、掘れるタイルがない場合の「移動へ進む」を追加
- [x] DIGでめくったタイルと山札タイルを選んで配置できるように修正
- [x] L字タイルの内部接続方向を画像の見た目に合わせ、直線90度配置の移動テストを追加
- [x] タイル全回転・隣接接続・掘った直後の移動可能性をテストで網羅強化
- [x] DIG対象を現在地タイルの開いている方向に限定するよう修正
- [x] GUI上のプレイヤー駒を見やすいサイズに拡大
- [x] GUI上のエサカードとプレイヤー駒をセルの約75%サイズに拡大
- [x] 盤面上側に単一の腹減りメーターを追加し、全プレイヤー駒を重ねて表示
- [x] 腹減りメーター画像の白背景を透過表示に調整
- [x] `AGENT.md` に要件定義書優先ルールと主要仕様要約を追記
- [x] 掘る・移動フェーズではエサカードを縮小し、ホバー時に拡大プレビュー表示するよう調整
- [x] 掘るタイルの回転ボタン表示を実際の配置待ち回転と同期し、エサ画像上だけで拡大プレビューするよう修正
- [x] 右サイドバー上部に現在手番プレイヤーの画像付きステータスパネルを追加
- [x] 現在手番プレイヤーパネルのモグラ画像を大きくし、カード内の文字を読みやすく調整
- [x] 現在手番プレイヤー画像の透明余白を切り詰め、黄色い円内でカード部分が大きく見えるよう修正
- [x] 逃走判定ダイスを振った捕獲成功でも、直近ダイスに出目を表示するよう修正
- [x] `Burrowed_Logic.mp3` をプレイ中BGMとしてループ再生するよう追加
- [x] 体力切れの勝敗判定を2人戦限定にし、得点に関係なく生存者を勝者とする一方、3〜4人戦は脱落者の手番を飛ばして継続するよう修正
- [x] Avast誤検知を避けるため、BGM再生からPowerShell実行を削除
- [x] 外部メディアプレイヤーを出さず、Swing版アプリ内でMP3を再生するよう修正
- [x] Android版MVP作成のため、`core` / `desktop` / `androidApp` の3モジュール構成に再編
- [x] 既存ゲームロジックと共通controllerを `core` に移し、Swing版は `desktop` として維持
- [x] Jetpack Compose版Androidアプリを追加し、2〜4人ローカルプレイ、盤面操作、掘る候補、回転、捕獲、食べる/レンコウ、ログ表示を実装
- [x] Android用画像リソースを `androidApp/src/main/res/drawable-nodpi` に配置
- [x] Android画面の初期レイアウトを圧縮し、システムバー/カメラ領域を避けるよう調整
- [x] Android版をセットアップ画面とプレイ画面に分離し、プレイ中は有効な操作だけ表示するよう再設計
- [x] Android版の腹減りメーターを透過PNGリソースに差し替え
- [x] Android版プレイ画面をVariant 2ベースで再調整し、HUD画像・盤面トークン・掘る候補カード・最新ログを拡大
- [x] 表示用日本語ラベルと回転表記をテストで固定し、回転ラベルを `0° / 90° / 180° / 270°` に統一
- [x] 内部テスト用AAB作成に向けて、Android release署名設定を `keystore.properties` から読み込む構成にし、keystore・パスワード・AAB/APKをGit管理外にする設定を追加
- [x] Phase 1: 1マスに複数エサを保持できる盤面データへ変更し、逃走先に既存エサがあっても逃走できるよう修正
- [x] Phase 1: エサの巣マス逃走を禁止し、巣へ逃げる場合は捕獲成功として扱うよう修正
- [x] Phase 2: 巣マスを通過不可にし、防衛中の巣を移動先・中継先から除外
- [x] Phase 2: 巣ごとの固定追い出し先を定義し、追い出し処理で固定先へ移動するよう修正
- [x] Phase 3: 強奪を移動時自動発動から、次の自分の手番以降の④選択式へ変更
- [x] Phase 3: 強奪対象選択、強奪後のタベる/レンコウ結果処理、Android/Swingの強奪表示を追加
- [x] Phase 4: セットアップでモグラ・巣・先手を自由選択できるように実装
- [x] ケラの逃走面数は2面で設計者確認済みとして整理
- [x] ユーザー追加確認を `AGENT.md` に反映し、地上マスへの穴タイル配置禁止とタイルなし地上移動を実装
- [x] PR #28 コメント対応として、Android 4人セットアップで使用中のモグラ・巣を選んだ場合に座席同士で入れ替えるよう修正
- [x] PR #28 コメント対応として、単一移動/捕獲ターゲットや複数捕獲対象の専用アクション行でも `スキップ` / `ターン終了` を表示するよう修正
- [x] 新規AABリリース用にAndroid `versionCode` / `versionName` を `4` に更新し、release bundleを作成
- [x] 2026-07-04共有写真の追加ルールを `moguru_requirements_v2(3).txt` / `AGENT.md` / `CLAUDE.md` に反映し、旧 `12-2` と強奪関連の要確認を解消
- [x] 自分の巣に戻った手番で、巣に保存済みのエサを1匹タベられるよう修正
- [x] Phase 6: 要件7.1に合わせ、表向き既存穴タイルも掘る置き換え対象として補助APIとテストを整理
- [x] Phase 6: 要件12-1の未確定エサカード個別データを、コード上もID付きTODOとして明示
- [x] [#52](https://github.com/kisschan/mogura-game/issues/52) 配置済みタイルの再掘削時に現在角度を引き継ぎ、候補ごとの角度を維持する
- [x] [#31](https://github.com/kisschan/mogura-game/issues/31) Android盤面の掘る・移動・捕獲候補を色と線種の両方で区別する
- [x] [#53](https://github.com/kisschan/mogura-game/issues/53) HUDに駒画像だけを半透明化する44dpの表示切り替えを追加する
- [x] PR #54 レビュー対応として、Desktopの操作パネルにも明示的な「置く」を追加し、盤面再クリックなしでDIGを確定可能にする
- [x] Androidに初心者向け「遊び方」ページを追加し、セットアップ画面とプレイ中の統合メニューから開けるようにする
- [x] プレイ中HUDの音量設定・遊び方・新規ゲームを44dpの統合メニューへまとめ、ルール閲覧中も盤面と表示設定を保持する
- [x] 初心者向けルール導線・スクロール・状態保持・新規ゲーム確認を対象とするAndroid UIテスト4件とタップ領域の単体テスト2件を追加する
- [x] Androidプレイ中HUDで点数幅を優先確保し、駒透の右に統合メニューを配置して小画面での点数省略を防止する
- [x] Androidの移動候補が複数ある局面でも「このマスへ移動」を表示し、操作バーで移動先を切り替え、選択中マスを緑の内枠で確認して実行できるようにする

## テスト結果
- 最終実行日: 2026-09-07（PR #64の食事効果音レビュー対応）
- TDD: 単体・Compose回帰テストを先に追加し、ViewModelのイベント消費APIが未実装のコンパイル失敗を確認後に実装。隔離検証はcore 247件・Android 151件成功、Desktop 51件成功・任意プレビュー3件スキップ、Composeテストソースのコンパイル成功。独立レビューでも問題なし
- 最終実行日: 2026-09-07（main統合後のv16）
- PR用ブランチの隔離検証でcore 247件・Android 150件成功、Desktop 54件中51件成功・任意プレビュー3件スキップ。Composeテストソースのコンパイル成功。音声合成のPythonテストも9件成功
- mainの失敗音重複防止を保持していることを独立レビューで確認。署名情報を読み込まないJDK 21の検証条件と、実端末検証未実施の制約は下記のとおり
- 最終実行日: 2026-09-07（⑤強奪後レンコウの即時得点・⑧回転方向表示）
- TDD確認: 旧処理に対して即時貯蔵・得点と案内のテストがcore 4件、Android 3件失敗することを確認してから実装。新規4件と既存テスト更新を含めて成功
- 署名情報を読み込まない隔離コピーにdesktopも追加し、`:core:test :androidApp:testDebugUnitTest :androidApp:compileDebugAndroidTestKotlin :desktop:test` が `BUILD SUCCESSFUL`。core 247件・Android 150件は全成功、Desktop 54件中51件成功・3件スキップ（合計451件中448件成功・3件スキップ）
- ⑧反映後も上記タスクの成功を確認。追加した回転Composeテスト4件はコンパイル成功。Desktopのスキップ3件は明示要求時だけ画像を書き出す任意プレビューテスト
- 未実施: 端末・エミュレーター未接続のためComposeテスト実行と実画面確認。元チェックアウトの署名付きビルドは実行していない
- コミット範囲（⑤⑧、2026-09-07）: 強奪後レンコウの即時得点、回転方向表示、関連テスト・要件・表示説明の13ファイル。追加ファイルは `DigRotationComposeTest.kt`。次は端末上で回転操作・文字拡大時の表示と強奪後の得点表示を確認する
- 最終実行日: 2026-09-07（③操作の省略/終了と④常時操作案内）
- TDD確認: 案内と結果を同時表示する高さ契約テストを先に追加し、`actionGuidanceStripHeight` 未定義の失敗を確認後に実装。段階別操作と案内の単体テスト13件、レイアウト契約1件、Composeテスト9件を追加
- 署名情報を読み込まない既存の隔離検証コピーで `:core:test :androidApp:testDebugUnitTest :androidApp:compileDebugAndroidTestKotlin` 成功（core 245件・Android 148件、計393件、失敗0・スキップ0）。JDK 21を使用し、元設定を検査するcoreのconfigテストは除外
- レビューで見つかった拡大文字のボタン高さ不足と移動先座標の省略を修正。Composeテストは360×740・390×844、文字倍率1.2/1.5、案内とログの分離、履歴、ENDの重複排除、掘る配置、捕獲後の案内を対象とし、コンパイル成功
- 未実施・次の作業: 接続端末・エミュレーターがないため実画面確認とComposeテスト実行は未実施。端末上で `ActionGuidanceComposeTest` と従来の移動・レンコウ表示テストを実行する
- 最終実行日: 2026-09-07（レンコウ中エサ表示）
- TDD確認: 表示用の所持情報を追加する前に `CarriedFoodUiStateTest` を追加し、`carriedFoodType` 未定義によるコンパイル失敗を確認後、実装して成功
- 検証コピーで `:core:test :androidApp:testDebugUnitTest :androidApp:compileDebugAndroidTestKotlin --no-daemon --max-workers=2` を実行し `BUILD SUCCESSFUL`（core 245件・Android 134件、計379件・失敗0・スキップ0）。前回同様、署名情報を読み込まない隔離環境とJDK 21を使用し、元設定を検査するcoreのconfigテストは除外
- 全プレイヤーの所持伝播、捕獲からレンコウ選択後の手番消耗演出中/終了後の保持、移動追従、巣への納品後の非表示を単体テストで確認。追加したComposeテスト4件はコンパイル成功
- 未実施: 接続端末・エミュレーターがないためComposeテスト実行と実画面確認。補助HTMLプレビューの表示もブラウザーのローカルファイル制限で未実施
- 次の作業: 端末上で `CarriedFoodComposeTest` と盤面全体の表示を確認し、モグラ4種・5種のエサ・小画面・透過時の手元の見やすさを確認する
- 最終実行日: 2026-09-07
- Android移動操作のTDD確認: 変更前ソースの検証コピーで、追加した「単一移動候補も選択中になる」単体テストの失敗を確認後、変更後ソースで成功を確認
- 検証方法: 通常のAndroidビルド設定が署名情報を自動ロードするため、ソース・公開ビルド定義・必要リソースだけを `.codex-analysis/move-selection/workspace` へコピーして実行。署名情報の読み込みを除去し、Gradle設定とキャッシュを分離。JDK・コンパイルターゲットは検証コピー内のみ21に揃え、元のビルド設定は変更しない
- 実行: 検証コピーで `:core:test :androidApp:testDebugUnitTest :androidApp:compileDebugAndroidTestKotlin --no-daemon --max-workers=2`。結果は `BUILD SUCCESSFUL`（core 245件・Android 130件、計375件・失敗0・スキップ0）。コピー対象外の元ビルド設定を検査するcoreのconfigテストは除外
- 新規Composeテスト9件を含む計測テストソースのコンパイル成功。盤面タップ・再タップ・候補循環で位置/HP/手番/段階が変わらないこと、ボタン確定、単一/0候補、選択初期化、掘る/捕獲回帰、360×740・390×844での表示範囲と文字切れを検証するテストを追加
- 未実施: 接続端末・エミュレーターがないため、Composeテスト実行および実画面でのレイアウト確認。元チェックアウトの通常ビルドと署名APK作成は実行していない
- 差分確認: 対象ファイルの `git diff --check` 成功。次の作業は端末上で `MoveSelectionComposeTest` を実行し、移動操作と2サイズの表示を確認する
- 最終実行日: 2026-08-23
- TDD確認: 2人戦の体力切れ勝利と、3〜4人戦で最後の1人になっても得点未達なら継続する回帰テストを先に更新。初回実行は作業開始前から変更されていた `GradlePropertiesTest.kt` の `assertFalse` import不足で対象テスト実行前に停止
- `GradlePropertiesTest.kt` に不足していた `assertFalse` importだけを追加後、通常の `.\gradlew.bat test` を実行
- 結果: `BUILD SUCCESSFUL`（core 219件・Android 113件・desktop 34件、計366テスト・失敗0・スキップ0）
- 最終実行日: 2026-08-05
- TDD確認: 複数移動候補の回帰テストを先に追加し、`primaryBoardActions` / `primaryBoardActionsForBar` 未実装のコンパイル失敗を確認後、実装して成功
- 実行コマンド: `.\gradlew.bat test --no-daemon`
- 結果: `BUILD SUCCESSFUL`（Android・core・desktopの全単体テスト成功）
- Android本体確認: `.\gradlew.bat :androidApp:lintAnalyzeDebug :androidApp:assembleDebug --no-daemon` は `BUILD SUCCESSFUL`
- Android UIテストAPK確認: Android Studio同梱JDK 21を明示した `:androidApp:assembleDebugAndroidTest` は `BUILD SUCCESSFUL`
- Android全lint確認: Android Studio同梱JDK 21を明示した `:androidApp:lintDebug` は `BUILD SUCCESSFUL`
- 最終実行日: 2026-07-31
- 実行コマンド: `.\gradlew.bat test --no-daemon`
- 結果: `BUILD SUCCESSFUL`（Android・core・desktopの全単体テスト成功）
- UIテスト確認: `:androidApp:compileDebugAndroidTestKotlin` 成功。接続端末・エミュレーターがないため計測テスト実行は未実施
- ビルド確認: `.\gradlew.bat :androidApp:assembleDebug --no-daemon` 成功
- 最終試行日: 2026-07-29
- 実行コマンド: `.\gradlew.bat :androidApp:compileDebugKotlin`
- 結果: 未完了（長時間終了しなかったためユーザー操作で中断し、残存Gradleデーモンを `.\gradlew.bat --stop` で停止。今回追加したテストは未実行）
- 静的確認: `git diff --check` 成功。Compose API・import・画面状態保持・要件文言を読み取りレビュー済み
- 最終実行日: 2026-07-18
- 実行コマンド: `.\gradlew.bat :core:test :androidApp:testDebugUnitTest :desktop:test :androidApp:assembleDebug :androidApp:assembleDebugAndroidTest`
- 結果: `BUILD SUCCESSFUL`（PR #54 レビュー修正後、357テスト・失敗0・スキップ0。通常APKと計測テストAPKの生成を確認）
- 最終実行日: 2026-07-18
- 実行コマンド: `.\gradlew.bat :desktop:test --tests com.moguru.game.gui.PlayerTokenLayoutTest`
- 結果: `BUILD SUCCESSFUL`（PR #54 のDesktop DIG確定導線回帰テスト）
- 最終実行日: 2026-07-18
- 実行コマンド: `.\gradlew.bat :core:test :androidApp:testDebugUnitTest :desktop:test :androidApp:assembleDebug :androidApp:assembleDebugAndroidTest`
- 結果: `BUILD SUCCESSFUL`（356テスト、失敗0・スキップ0。通常APKと計測テストAPKの生成を確認）
- 最終実行日: 2026-07-15
- 実行コマンド: `cmd /c gradlew.bat :androidApp:testDebugUnitTest :desktop:test :androidApp:assembleDebug`
- 結果: `BUILD SUCCESSFUL`（アプリアイコン・正式タイトル反映後のAndroidリソースパッケージング・APK生成・Android/Swing回帰確認）
- 最終実行日: 2026-07-05
- 実行コマンド: `cmd /c gradlew :core:test`
- 結果: `BUILD SUCCESSFUL`（Phase 6 要件7.1差異整理、未確定12-1 TODO明示後）
- 最終実行日: 2026-07-05
- 実行コマンド: `cmd /c gradlew :androidApp:testDebugUnitTest :desktop:test`
- 結果: `BUILD SUCCESSFUL`（Phase 6 core変更後のAndroid/Swing回帰確認）
- 最終実行日: 2026-07-04
- 実行コマンド: `$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'; .\gradlew.bat :core:test`
- 結果: `BUILD SUCCESSFUL`（共有写真ルール反映、自分の巣エサのタベる処理追加後）
- 最終実行日: 2026-07-04
- 実行コマンド: `$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'; .\gradlew.bat :androidApp:testDebugUnitTest :desktop:test`
- 結果: `BUILD SUCCESSFUL`（controllerのアクション表示変更後のAndroid/Swing回帰確認）
- 最終実行日: 2026-07-03
- 実行コマンド: `$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'; .\gradlew.bat :androidApp:testDebugUnitTest :androidApp:bundleRelease`
- 結果: `BUILD SUCCESSFUL`（Android `versionCode` / `versionName` を `4` に更新後、`androidApp/build/outputs/bundle/release/androidApp-release.aab` を生成）
- 最終実行日: 2026-07-03
- 実行コマンド: `$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'; .\gradlew.bat :core:test :androidApp:testDebugUnitTest`
- 結果: `BUILD SUCCESSFUL`（地上ルール修正、Androidセットアップ入れ替え、任意アクション表示修正後）
- 最終実行日: 2026-07-03
- 実行コマンド: `$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'; .\gradlew.bat :core:test`
- 結果: `BUILD SUCCESSFUL`（地上タイル除外ヘルパー修正後のcore再確認）
- 最終実行日: 2026-07-03
- 実行コマンド: `$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'; .\gradlew.bat :androidApp:testDebugUnitTest :desktop:test`
- 結果: `BUILD SUCCESSFUL`（core再確認後のAndroid/Swing回帰確認）
- 最終実行日: 2026-06-28
- 実行コマンド: `$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'; .\gradlew.bat :core:test :androidApp:testDebugUnitTest :desktop:test`
- 結果: `BUILD SUCCESSFUL`（Phase 4 セットアップ自由選択実装後）
- 最終実行日: 2026-06-27
- 実行コマンド: `$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'; .\gradlew.bat :core:test :androidApp:testDebugUnitTest :desktop:test`
- 結果: `BUILD SUCCESSFUL`（Phase 3 実装後。JDK 21 daemon toolchain は Android Studio JBR を使用）
- 最終試行日: 2026-06-21
- 実行コマンド: `.\gradlew.bat :core:test`
- 結果: 未完了（Gradle wrapper が `gradle-9.4.1-bin.zip` 取得時に `SSLHandshakeException: certificate_unknown` で失敗。`JAVA_OPTS` にチェックイン済みtruststoreを指定しても同じ）
- 最終実行日: 2026-06-21
- 実行コマンド: `.\gradlew.bat :core:test :desktop:test :androidApp:testDebugUnitTest`
- 結果: `BUILD SUCCESSFUL`（Phase 1 実装後）
- 最終実行日: 2026-06-14
- 実行コマンド: `.\gradlew.bat :core:test`
- 結果: `BUILD SUCCESSFUL`
- 実行コマンド: `.\gradlew.bat :desktop:test`
- 結果: `BUILD SUCCESSFUL`
- 実行コマンド: `.\gradlew.bat :androidApp:testDebugUnitTest`
- 結果: `BUILD SUCCESSFUL`
- 実行コマンド: `.\gradlew.bat :androidApp:assembleDebug`
- 結果: `BUILD SUCCESSFUL`
- 実行コマンド: `.\gradlew.bat :androidApp:testDebugUnitTest :androidApp:assembleDebug`
- 結果: `BUILD SUCCESSFUL`
- 実行コマンド: `.\gradlew.bat :androidApp:testDebugUnitTest :androidApp:assembleDebug`
- 結果: `BUILD SUCCESSFUL`（Android画面再設計後）
- 実行コマンド: `.\gradlew.bat :core:test :androidApp:testDebugUnitTest`
- 結果: `BUILD SUCCESSFUL`（Android視認性改善後）
- 実行コマンド: `.\gradlew.bat :androidApp:assembleDebug`
- 結果: `BUILD SUCCESSFUL`（Android視認性改善後）
- 実行コマンド: `.\gradlew.bat :desktop:test`
- 結果: `BUILD SUCCESSFUL`（回転ラベル変更後のSwing回帰確認）
- 実行コマンド: `.\gradlew.bat :androidApp:bundleRelease`
- 結果: `BUILD SUCCESSFUL`（`keystore.properties` 無しでもrelease AAB作成可。実keystore未作成のためPlay Console用署名は未適用）
- 運用: 生成AAB/APKはGitHubへpushせず、Play Consoleの内部テストトラックへ直接アップロードする

## 実装済みファイル
- `assets/audio/burrowed_logic.mp3`
- `libs/jlayer-1.0.1.jar`
- `libs/README.md`
- `src/main/kotlin/com/moguru/game/gui/BackgroundMusicPlayer.kt`
- `src/main/kotlin/com/moguru/game/model/Board.kt`
- `src/main/kotlin/com/moguru/game/model/HoleTile.kt`
- `src/main/kotlin/com/moguru/game/model/Food.kt`
- `src/main/kotlin/com/moguru/game/model/Player.kt`
- `src/main/kotlin/com/moguru/game/util/Dice.kt`
- `src/main/kotlin/com/moguru/game/engine/MovementEngine.kt`
- `src/main/kotlin/com/moguru/game/engine/TilePlacementEngine.kt`
- `src/main/kotlin/com/moguru/game/engine/GameEngine.kt`
- `src/main/kotlin/com/moguru/game/gui/MoguraGameApp.kt`
- `src/main/kotlin/com/moguru/game/gui/MoguraGameController.kt`
- `src/main/kotlin/com/moguru/game/gui/HungerMeter.kt`
- `src/main/kotlin/com/moguru/game/gui/FoodDisplay.kt`
- `src/main/kotlin/com/moguru/game/gui/DigRotationDisplay.kt`
- `src/main/kotlin/com/moguru/game/gui/CurrentPlayerDisplay.kt`
- `src/test/kotlin/com/moguru/game/model/BoardTest.kt`
- `src/test/kotlin/com/moguru/game/model/HoleTileTest.kt`
- `src/test/kotlin/com/moguru/game/model/FoodTest.kt`
- `src/test/kotlin/com/moguru/game/model/PlayerTest.kt`
- `src/test/kotlin/com/moguru/game/util/DiceTest.kt`
- `src/test/kotlin/com/moguru/game/engine/MovementEngineTest.kt`
- `src/test/kotlin/com/moguru/game/engine/TilePlacementEngineTest.kt`
- `src/test/kotlin/com/moguru/game/engine/GameEngineTest.kt`
- `src/test/kotlin/com/moguru/game/gui/MoguraGameControllerTest.kt`
- `src/test/kotlin/com/moguru/game/gui/HungerMeterTest.kt`
- `src/test/kotlin/com/moguru/game/gui/FoodDisplayTest.kt`
- `src/test/kotlin/com/moguru/game/gui/DigRotationDisplayTest.kt`
- `src/test/kotlin/com/moguru/game/gui/CurrentPlayerDisplayTest.kt`

## Android版で追加・再配置した主なファイル
- `core/src/main/kotlin/com/moguru/game/model/`
- `core/src/main/kotlin/com/moguru/game/engine/`
- `core/src/main/kotlin/com/moguru/game/presenter/MoguraGameController.kt`
- `desktop/src/main/kotlin/com/moguru/game/gui/MoguraGameApp.kt`
- `androidApp/src/main/kotlin/com/moguru/game/android/MainActivity.kt`
- `androidApp/src/main/kotlin/com/moguru/game/android/AndroidGameViewModel.kt`
- `androidApp/src/main/kotlin/com/moguru/game/android/GameScreen.kt`
- `androidApp/src/main/kotlin/com/moguru/game/android/BoardPlayerToken.kt`
- `androidApp/src/main/kotlin/com/moguru/game/android/RulesScreen.kt`
- `androidApp/src/test/kotlin/com/moguru/game/android/AndroidGameViewModelTest.kt`
- `androidApp/src/test/kotlin/com/moguru/game/android/MobileGameplayLayoutContractTest.kt`
- `androidApp/src/androidTest/kotlin/com/moguru/game/android/MobileGameplayComposeTest.kt`
- `androidApp/src/androidTest/kotlin/com/moguru/game/android/MoveSelectionComposeTest.kt`
- `androidApp/src/test/kotlin/com/moguru/game/android/CarriedFoodUiStateTest.kt`
- `androidApp/src/androidTest/kotlin/com/moguru/game/android/CarriedFoodComposeTest.kt`
- `androidApp/src/main/res/drawable-nodpi/`
- `androidApp/build.gradle.kts`
- `.gitignore`
- `keystore.properties.example`

## TODO / 未確定・要確認
- 未確定・要確認事項はなし。旧 `12-1` は2026-07-11共有の正本画像5枚により解決済み。
- 正式版エサタイルの得点、逃走方向、表面画像、関連テストは確定要件へ反映済み。

## Issue 整理
### 完了済み
- [x] [#16](https://github.com/kisschan/mogura-game/issues/16) セットアップでモグラ・巣・先手をプレイヤーが自由に選べるようにする（2026-07-04 closed）
- [x] [#17](https://github.com/kisschan/mogura-game/issues/17) 逃走先に別のエサがある場合でも逃走できるようにする（2026-07-04 closed）
- [x] [#18](https://github.com/kisschan/mogura-game/issues/18) エサの逃走先が巣マスの場合は逃走失敗として捕獲成功にする（2026-07-04 closed）
- [x] [#19](https://github.com/kisschan/mogura-game/issues/19) 巣マスを通過不可にし、防衛中の巣へ侵入できないようにする（2026-07-04 closed）
- [x] [#20](https://github.com/kisschan/mogura-game/issues/20) 巣ごとの固定追い出し先マスを定義して追い出し処理に使う（2026-07-04 closed）
- [x] [#21](https://github.com/kisschan/mogura-game/issues/21) 強奪を移動時自動発動ではなく④強奪選択として実行する（2026-07-04 closed）
- [x] [#22](https://github.com/kisschan/mogura-game/issues/22) 強奪UIと強奪後の食べる/レンコウ結果を実装する（2026-07-04 closed）
- [x] [#23](https://github.com/kisschan/mogura-game/issues/23) 正本で確定したエサ5種の得点・逃走方向・表面画像を実装とテストへ反映（2026-07-11実装完了）

### 未完了・Phase 6 対象
- [ ] [#34](https://github.com/kisschan/mogura-game/issues/34) Phase 6: 要件・実装差異の解消とリリース品質確認

### UI/UX 改善候補
- [ ] [#12](https://github.com/kisschan/mogura-game/issues/12) UIで文字が切れる問題を修正する
- [ ] [#13](https://github.com/kisschan/mogura-game/issues/13) ダンゴムシの幼虫になっている問題を修正する
- [ ] [#29](https://github.com/kisschan/mogura-game/issues/29) Android UIの色セマンティクスをトークン化しセットアップ選択表示を統一する
- [ ] [#30](https://github.com/kisschan/mogura-game/issues/30) Androidプレイ画面の手番・フェーズ認知をコンパクトHUD内で強化する
- [x] [#31](https://github.com/kisschan/mogura-game/issues/31) Android盤面ハイライトの色・形状を掘る/移動/捕獲で明確に分離する（2026-07-18 実装完了、マージ待ち）
- [x] [#52](https://github.com/kisschan/mogura-game/issues/52) 再掘削時のタイル角度を現在の向きから開始し、明示的な「置く」で確定する（2026-07-18 実装完了、マージ待ち）
- [x] [#53](https://github.com/kisschan/mogura-game/issues/53) 駒の下のタイルを確認できる半透明表示切り替えを追加する（2026-07-18 実装完了、マージ待ち）
- [ ] [#32](https://github.com/kisschan/mogura-game/issues/32) Androidの不可逆操作に確認または明確な確定導線を追加する
- [ ] [#33](https://github.com/kisschan/mogura-game/issues/33) Androidプレイ画面に短時間のトークン移動フィードバックを追加する

## 未実装解消ロードマップ
### Phase 1: 盤面ルールの土台を先に直す
- [x] [#17](https://github.com/kisschan/mogura-game/issues/17) 1マス複数エサを表現できる盤面データへ変更する。
  - `foodPositions: Map<Position, FoodCard>` 前提を見直す。
  - coreの捕獲・補充・表示用DTOを同時に更新する。
  - 旧仕様テスト `逃走先に別のエサがある場合は捕獲成功` を新仕様へ置き換える。
- [x] [#18](https://github.com/kisschan/mogura-game/issues/18) 巣マスへのエサ逃走を禁止する。
  - `CellType.NEST` を逃走不可条件に入れる。
  - 巣マスにエサが生成されないことをテストで固定する。

### Phase 2: 巣と移動の不変条件を確定する
- [x] [#19](https://github.com/kisschan/mogura-game/issues/19) 巣マスを通過不可にする。
  - BFSで巣マス到達後に探索を継続しない。
  - 防衛中の巣は停止先にも中継先にも含めない。
- [x] [#20](https://github.com/kisschan/mogura-game/issues/20) 巣ごとの固定追い出し先を実装する。
  - 4つの巣に対する固定追い出し先座標を定義する。
  - 追い出し先に駒やエサがある場合も、ユーザー確認に基づき固定先へ強制移動する。

### Phase 3: 強奪を正式フローへ置き換える
- [x] [#21](https://github.com/kisschan/mogura-game/issues/21) 強奪を移動時自動発動から④選択式へ変更する。
  - 他人の巣に入った手番では強奪不可。
  - 次の自分の手番以降に強奪可能になる状態を保持する。
- [x] [#22](https://github.com/kisschan/mogura-game/issues/22) 強奪専用UIと強奪後の結果処理を実装する。
  - 通常捕獲由来と強奪由来の pending decision を型で分ける。
  - 強奪後の「食べる」は回復、「レンコウ」は手持ち化し、自分の巣へ戻った時点で得点化する。
  - Android/Swing で「強奪」表示を追加する。

### Phase 4: セットアップとカードデータを仕上げる
- [x] [#16](https://github.com/kisschan/mogura-game/issues/16) セットアップ自由選択を実装する。
  - モグラ選択、巣選択、スタートプレイヤー選択を core/presenter/UI に通す。
  - 固定配置・固定先手の仮実装をテストから外す。
- [x] [#23](https://github.com/kisschan/mogura-game/issues/23) 正本から確定したエサ5種のデータと表面画像を更新する。
  - ミミズの得点を1点へ修正する。
  - ミミズ、ケラ、ムカデ、カエルの確定逃走表を盤面絶対方向で実装する。
  - 同種カードへ同一仕様を適用し、5種の得点・回復・逃走目・方向をテストで固定する。
  - Android/Swingの旧PNGを正式版表面画像へ差し替え、全体表示を確認する。

### Phase 5: 回帰確認
- [x] `:core:test` で盤面ルール、捕獲、強奪、巣防衛の回帰を確認する。
- [x] `:androidApp:testDebugUnitTest` でAndroid表示状態と操作可否を確認する。
- [x] `:desktop:test` でSwing表示ロジックと共通presenter利用箇所を確認する。
- [x] `PROGRESS.md` とコード内TODOを、解消した issue 番号に合わせて整理する。

### Phase 6: 要件・実装差異の解消とリリース品質確認
- [ ] [#34](https://github.com/kisschan/mogura-game/issues/34) を親 issue として、差異の棚卸しと対応範囲を管理する。
- [x] 要件定義書、`AGENT.md`、`CLAUDE.md`、`PROGRESS.md` と実装の差異を棚卸しする。
  - 要件7.1の旧「表向きタイル不可」テスト差異を解消済み。
  - 旧 `12-1` は正本画像5枚により解決済み。
  - 正本との差異だったミミズ得点、逃走表、旧PNG、関連テストを更新済み。
- [ ] core、Android、Swing の挙動差異と表示差異を確認し、要件に照らして修正対象を決める。
- [ ] 実プレイ上わかりにくい操作を洗い出し、要件変更が必要なものと表示補助で済むものを分ける。
- [x] [#23](https://github.com/kisschan/mogura-game/issues/23) エサ5種の確定得点・回復・逃走目・絶対方向と正式版表面画像を反映し、テストで固定する。
- [x] 2026-07-15: versionCode / versionName 5 の署名付きリリースAABを生成する。
  - `.\gradlew.bat test bundleRelease --stacktrace` は `BUILD SUCCESSFUL`。
  - 出力: `androidApp/build/outputs/bundle/release/androidApp-release.aab`
  - SHA-256: `F98BB6A56F0C116AF2758483904D6144F33A23F9AD9899A75462FE98CA4DD289`
- [x] 2026-07-17: versionCode / versionName 6 の署名付きリリースAABを生成する。
  - `.\gradlew.bat test bundleRelease --stacktrace` は `BUILD SUCCESSFUL`。
  - 出力: `androidApp/build/outputs/bundle/release/mogura-game-v6-release.aab`
  - SHA-256: `B7696EC66EF199B94AD90F9262DE6A396609820376C158E1CCB117909829E599`
- [x] 2026-07-18: BGM停止後の遅延した一時オーディオフォーカス喪失が次の再生を妨げないよう修正し、回帰テストを追加する。
- [x] #31・#52・#53 の全回帰テストとAndroidテストAPKビルドを実行する。
- [x] 2026-07-26: versionCode / versionName 7 の署名付きリリースAABを最新のルール修正を含めて再生成する。
  - `.\gradlew.bat test bundleRelease --stacktrace --no-daemon --max-workers=1` は `BUILD SUCCESSFUL`（全361件、失敗0件）。
  - 出力: `androidApp/build/outputs/bundle/release/mogura-game-v7-release.aab`
  - SHA-256: `373E2B182FEA110F4EB17E60569C9FDFF2299C633260AC7F0FAB2B5BF8855437`

## 次の作業
- Gradleが正常終了する環境で `:androidApp:testDebugUnitTest` と `:androidApp:assembleDebugAndroidTest` を実行し、今回追加したルール画面テストを確認する
- Android/Swing の実プレイ表示差異を要件に照らして確認し、必要な表示補助だけを実装する
- 生成AAB/APKはGitHubへ上げず、Play Consoleへ直接アップロードする
