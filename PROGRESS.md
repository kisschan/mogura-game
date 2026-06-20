# PROGRESS.md

最終更新: 2026-06-20

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

## テスト結果
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
- `Food.kt`: `// TODO: 【要確認】12-1` ケラは要件では3面逃走だが画像では3/4の2面のみ読めるため画像優先で仮実装
- `TilePlacementEngine.kt`: `// TODO: 【要確認】3-1` 隣接に裏向きタイルがない場合の山札タイル新規配置ルール（GUIでは「移動へ進む」で暫定対応済み）
- `TilePlacementEngine.kt`: `// TODO: 【要確認】3-2` 穴タイル配置先の選択ルール（めくったタイル / 山札タイルの選択はGUIで実装済み）
- `GameEngine.kt`: `// TODO: 【要確認】3-3` 強奪を行うフェーズ
- `MovementEngine.kt`: `// TODO: 【要確認】3-4` 巣防衛の接続ルール
- `GameEngine.kt`: `// TODO: 【要確認】3-4` 巣防衛の詳細
- `GameEngine.kt`: `// TODO: 【要確認】3-5` 追い出し先の選択権
- `moguru_requirements_v2(3).txt`: `12-2` 未開示の追加ルール
- `moguru_requirements_v2(3).txt`: `13-6` 地上と地下の接続条件

## 2026-06-20 コードレビューで起票した未実装 issue
- [#16](https://github.com/kisschan/mogura-game/issues/16) セットアップでモグラ・巣・先手をプレイヤーが自由に選べるようにする
- [#17](https://github.com/kisschan/mogura-game/issues/17) 逃走先に別のエサがある場合でも逃走できるようにする
- [#18](https://github.com/kisschan/mogura-game/issues/18) エサの逃走先が巣マスの場合は逃走失敗として捕獲成功にする
- [#19](https://github.com/kisschan/mogura-game/issues/19) 巣マスを通過不可にし、防衛中の巣へ侵入できないようにする
- [#20](https://github.com/kisschan/mogura-game/issues/20) 巣ごとの固定追い出し先マスを定義して追い出し処理に使う
- [#21](https://github.com/kisschan/mogura-game/issues/21) 強奪を移動時自動発動ではなく④強奪選択として実行する
- [#22](https://github.com/kisschan/mogura-game/issues/22) 強奪UIと強奪後の食べる/レンコウ結果を実装する
- [#23](https://github.com/kisschan/mogura-game/issues/23) エサカード13枚の逃走ダイス目と方向を確定し実装データを更新する

## 未実装解消ロードマップ
### Phase 1: 盤面ルールの土台を先に直す
- [ ] [#17](https://github.com/kisschan/mogura-game/issues/17) 1マス複数エサを表現できる盤面データへ変更する。
  - `foodPositions: Map<Position, FoodCard>` 前提を見直す。
  - coreの捕獲・補充・表示用DTOを同時に更新する。
  - 旧仕様テスト `逃走先に別のエサがある場合は捕獲成功` を新仕様へ置き換える。
- [ ] [#18](https://github.com/kisschan/mogura-game/issues/18) 巣マスへのエサ逃走を禁止する。
  - `CellType.NEST` を逃走不可条件に入れる。
  - 巣マスにエサが生成されないことをテストで固定する。

### Phase 2: 巣と移動の不変条件を確定する
- [ ] [#19](https://github.com/kisschan/mogura-game/issues/19) 巣マスを通過不可にする。
  - BFSで巣マス到達後に探索を継続しない。
  - 防衛中の巣は停止先にも中継先にも含めない。
- [ ] [#20](https://github.com/kisschan/mogura-game/issues/20) 巣ごとの固定追い出し先を実装する。
  - 4つの巣に対する固定追い出し先座標を定義する。
  - 追い出し先に駒やエサがある場合の扱いを要件定義書と同期する。

### Phase 3: 強奪を正式フローへ置き換える
- [ ] [#21](https://github.com/kisschan/mogura-game/issues/21) 強奪を移動時自動発動から④選択式へ変更する。
  - 他人の巣に入った手番では強奪不可。
  - 次の自分の手番以降に強奪可能になる状態を保持する。
- [ ] [#22](https://github.com/kisschan/mogura-game/issues/22) 強奪専用UIと強奪後の結果処理を実装する。
  - 通常捕獲由来と強奪由来の pending decision を型で分ける。
  - 強奪後の「食べる」は回復、「レンコウ」は自分の巣へ移して得点化する。
  - Android/Swing で「強奪」表示を追加する。

### Phase 4: セットアップとカードデータを仕上げる
- [ ] [#16](https://github.com/kisschan/mogura-game/issues/16) セットアップ自由選択を実装する。
  - モグラ選択、巣選択、スタートプレイヤー選択を core/presenter/UI に通す。
  - 固定配置・固定先手の仮実装をテストから外す。
- [ ] [#23](https://github.com/kisschan/mogura-game/issues/23) エサカード個別データを確定後に更新する。
  - 設計者確認が必要なため、確定データ入手後に実装する。
  - 13枚それぞれの逃走目・方向をテストで固定する。

### Phase 5: 回帰確認
- [ ] `:core:test` で盤面ルール、捕獲、強奪、巣防衛の回帰を確認する。
- [ ] `:androidApp:testDebugUnitTest` でAndroid表示状態と操作可否を確認する。
- [ ] `:desktop:test` でSwing表示ロジックと共通presenter利用箇所を確認する。
- [ ] `PROGRESS.md` とコード内TODOを、解消した issue 番号に合わせて整理する。

## 次の作業
- 未実装解消ロードマップの Phase 1 から着手する
- 未確定 / 要確認ルールの確定後に仮実装を調整する
- 強奪フェーズは移動後自動発動の仮実装なので、[#21](https://github.com/kisschan/mogura-game/issues/21) で正式フローへ置き換える
- 内部テスト前にローカルでupload keyを作成し、Git管理外の `keystore.properties` を設定して署名済みAABを作成する。生成AAB/APKはGitHubへ上げず、Play Consoleへ直接アップロードする
