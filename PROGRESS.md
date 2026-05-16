# PROGRESS.md

最終更新: 2026-05-16

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

## テスト結果
- 最終実行日: 2026-05-16
- 実行コマンド: `.\gradlew.bat test --no-daemon`
- 結果: `BUILD SUCCESSFUL`

## 実装済みファイル
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
- `src/test/kotlin/com/moguru/game/model/BoardTest.kt`
- `src/test/kotlin/com/moguru/game/model/HoleTileTest.kt`
- `src/test/kotlin/com/moguru/game/model/FoodTest.kt`
- `src/test/kotlin/com/moguru/game/model/PlayerTest.kt`
- `src/test/kotlin/com/moguru/game/util/DiceTest.kt`
- `src/test/kotlin/com/moguru/game/engine/MovementEngineTest.kt`
- `src/test/kotlin/com/moguru/game/engine/TilePlacementEngineTest.kt`
- `src/test/kotlin/com/moguru/game/engine/GameEngineTest.kt`
- `src/test/kotlin/com/moguru/game/gui/MoguraGameControllerTest.kt`

## TODO / 未確定・要確認
- `Food.kt`: `// TODO: 【要確認】12-1` ケラは要件では3面逃走だが画像では3/4の2面のみ読めるため画像優先で仮実装
- `TilePlacementEngine.kt`: `// TODO: 【要確認】3-1` 隣接に裏向きタイルがない場合の山札タイル新規配置ルール（GUIでは「移動へ進む」で暫定対応済み）
- `TilePlacementEngine.kt`: `// TODO: 【要確認】3-2` 穴タイル配置先の選択ルール
- `GameEngine.kt`: `// TODO: 【要確認】3-3` 強奪を行うフェーズ
- `MovementEngine.kt`: `// TODO: 【要確認】3-4` 巣防衛の接続ルール
- `GameEngine.kt`: `// TODO: 【要確認】3-4` 巣防衛の詳細
- `GameEngine.kt`: `// TODO: 【要確認】3-5` 追い出し先の選択権
- `moguru_requirements_v2(3).txt`: `12-2` 未開示の追加ルール
- `moguru_requirements_v2(3).txt`: `13-6` 地上と地下の接続条件

## 次の作業
- 未確定 / 要確認ルールの確定後に仮実装を調整する
- 強奪フェーズは移動後自動発動の仮実装なので、`13-3` 確定後に調整する
