# PROGRESS.md

最終更新: 2026-05-22

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

## テスト結果
- 最終実行日: 2026-05-22
- 実行コマンド: `.\gradlew.bat clean build`
- 結果: `BUILD SUCCESSFUL`

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

## 次の作業
- 未確定 / 要確認ルールの確定後に仮実装を調整する
- 強奪フェーズは移動後自動発動の仮実装なので、`13-3` 確定後に調整する
