# PROGRESS.md

最終更新: 2026-03-09

## 現在の状況
- [x] 盤面、穴タイル、エサ、プレイヤーのモデル実装
- [x] 移動、タイル配置、ターン進行のエンジン実装
- [x] 文字化けしていたソースとドキュメントの修正

## テスト結果
- 最終実行日: 2026-03-09
- 実行コマンド: `./gradlew.bat test`
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
- `src/test/kotlin/com/moguru/game/model/BoardTest.kt`
- `src/test/kotlin/com/moguru/game/model/HoleTileTest.kt`
- `src/test/kotlin/com/moguru/game/model/FoodTest.kt`
- `src/test/kotlin/com/moguru/game/model/PlayerTest.kt`
- `src/test/kotlin/com/moguru/game/util/DiceTest.kt`
- `src/test/kotlin/com/moguru/game/engine/MovementEngineTest.kt`
- `src/test/kotlin/com/moguru/game/engine/TilePlacementEngineTest.kt`
- `src/test/kotlin/com/moguru/game/engine/GameEngineTest.kt`

## TODO / 未確定・要確認
- `Food.kt`: `// TODO: 【未確定】2-1` エサカードの逃走ダイス目と方向
- `TilePlacementEngine.kt`: `// TODO: 【要確認】3-1` 隣接に裏向きタイルがない場合の掘る処理
- `TilePlacementEngine.kt`: `// TODO: 【要確認】3-2` 穴タイル配置先の選択ルール
- `GameEngine.kt`: `// TODO: 【要確認】3-3` 強奪を行うフェーズ
- `MovementEngine.kt`: `// TODO: 【要確認】3-4` 巣防衛の接続ルール
- `GameEngine.kt`: `// TODO: 【要確認】3-4` 巣防衛の詳細
- `GameEngine.kt`: `// TODO: 【要確認】3-5` 追い出し先の選択権

## 次の作業
- テストを再実行してビルド結果を確定する
- 未確定 / 要確認ルールの確定後に仮実装を調整する
