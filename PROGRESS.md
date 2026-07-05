# PROGRESS.md

最終更新: 2026-07-05

## 現在の状況
- [x] 盤面、穴タイル、エサ、プレイヤーのモデル実装
- [x] 移動、タイル配置、ターン進行のエンジン実装
- [x] 文字化けしていたソースとドキュメントの修正
- [x] Swing GUI の追加
- [x] 要件定義書 `moguru_requirements_v2(3).txt` に合わせた捕獲後選択・地上移動・補充ルールの修正
- [x] 強奪後も即連行せず `タベる / レンコウ` 選択に入るよう修正
- [x] エサ画像から読める逃走ダイス目と8方向逃走を実装
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
- [x] 体力切れで最後の生存プレイヤーだけが残った場合にゲーム終了するよう修正
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

## テスト結果
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
- `androidApp/src/test/kotlin/com/moguru/game/android/AndroidGameViewModelTest.kt`
- `androidApp/src/main/res/drawable-nodpi/`
- `androidApp/build.gradle.kts`
- `.gitignore`
- `keystore.properties.example`

## TODO / 未確定・要確認
- `moguru_requirements_v2(3).txt`: `12-1` 各エサカード13枚の具体的な逃走ダイス目と矢印方向

## Issue 整理
### 完了済み
- [x] [#16](https://github.com/kisschan/mogura-game/issues/16) セットアップでモグラ・巣・先手をプレイヤーが自由に選べるようにする（2026-07-04 closed）
- [x] [#17](https://github.com/kisschan/mogura-game/issues/17) 逃走先に別のエサがある場合でも逃走できるようにする（2026-07-04 closed）
- [x] [#18](https://github.com/kisschan/mogura-game/issues/18) エサの逃走先が巣マスの場合は逃走失敗として捕獲成功にする（2026-07-04 closed）
- [x] [#19](https://github.com/kisschan/mogura-game/issues/19) 巣マスを通過不可にし、防衛中の巣へ侵入できないようにする（2026-07-04 closed）
- [x] [#20](https://github.com/kisschan/mogura-game/issues/20) 巣ごとの固定追い出し先マスを定義して追い出し処理に使う（2026-07-04 closed）
- [x] [#21](https://github.com/kisschan/mogura-game/issues/21) 強奪を移動時自動発動ではなく④強奪選択として実行する（2026-07-04 closed）
- [x] [#22](https://github.com/kisschan/mogura-game/issues/22) 強奪UIと強奪後の食べる/レンコウ結果を実装する（2026-07-04 closed）

### 未完了・Phase 6 対象
- [ ] [#23](https://github.com/kisschan/mogura-game/issues/23) エサカード13枚の逃走ダイス目と方向を確定し実装データを更新する
- [ ] [#34](https://github.com/kisschan/mogura-game/issues/34) Phase 6: 要件・実装差異の解消とリリース品質確認

### UI/UX 改善候補
- [ ] [#12](https://github.com/kisschan/mogura-game/issues/12) UIで文字が切れる問題を修正する
- [ ] [#13](https://github.com/kisschan/mogura-game/issues/13) ダンゴムシの幼虫になっている問題を修正する
- [ ] [#29](https://github.com/kisschan/mogura-game/issues/29) Android UIの色セマンティクスをトークン化しセットアップ選択表示を統一する
- [ ] [#30](https://github.com/kisschan/mogura-game/issues/30) Androidプレイ画面の手番・フェーズ認知をコンパクトHUD内で強化する
- [ ] [#31](https://github.com/kisschan/mogura-game/issues/31) Android盤面ハイライトの色・形状を掘る/移動/捕獲で明確に分離する
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
- [ ] [#23](https://github.com/kisschan/mogura-game/issues/23) エサカード個別データを確定後に更新する。
  - 設計者確認が必要なため、確定データ入手後に実装する。
  - 13枚それぞれの逃走目・方向をテストで固定する。

### Phase 5: 回帰確認
- [x] `:core:test` で盤面ルール、捕獲、強奪、巣防衛の回帰を確認する。
- [x] `:androidApp:testDebugUnitTest` でAndroid表示状態と操作可否を確認する。
- [x] `:desktop:test` でSwing表示ロジックと共通presenter利用箇所を確認する。
- [x] `PROGRESS.md` とコード内TODOを、解消した issue 番号に合わせて整理する。

### Phase 6: 要件・実装差異の解消とリリース品質確認
- [ ] [#34](https://github.com/kisschan/mogura-game/issues/34) を親 issue として、差異の棚卸しと対応範囲を管理する。
- [x] 要件定義書、`AGENT.md`、`CLAUDE.md`、`PROGRESS.md` と実装の差異を棚卸しする。
  - 要件7.1の旧「表向きタイル不可」テスト差異を解消済み。
  - coreロジックで確認できた実装可能な残差異はなし。`12-1` は要件定義書上の未確定事項として継続。
- [ ] core、Android、Swing の挙動差異と表示差異を確認し、要件に照らして修正対象を決める。
- [ ] 実プレイ上わかりにくい操作を洗い出し、要件変更が必要なものと表示補助で済むものを分ける。
- [ ] [#23](https://github.com/kisschan/mogura-game/issues/23) エサカード13枚の確定逃走目・方向データを反映し、テストで固定する。
- [ ] 差異解消後に `:core:test :androidApp:testDebugUnitTest :desktop:test` と、必要に応じて `:androidApp:bundleRelease` を実行する。

## 次の作業
- Android/Swing の実プレイ表示差異を要件に照らして確認し、必要な表示補助だけを実装する
- エサカード13枚の確定逃走目・方向データを入手したら [#23](https://github.com/kisschan/mogura-game/issues/23) を実装する
- 生成AAB/APKはGitHubへ上げず、Play Consoleへ直接アップロードする
