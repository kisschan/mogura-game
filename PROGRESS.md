# PROGRESS.md

最終更新: 2026-05-15

## 現在の状況
- [x] 盤面、穴タイル、エサ、プレイヤーのモデル実装
- [x] 移動、タイル配置、ターン進行のエンジン実装
- [x] 文字化けしていたソースとドキュメントの修正
- [x] Swing GUI の追加
- [x] 要件定義書 `moguru_requirements_v2(3).txt` に合わせた捕獲後選択・地上移動・補充ルールの修正
- [x] 強奪後も即連行せず `タベる / レンコウ` 選択に入るよう修正

## テスト結果
- 最終実行日: 2026-05-15
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
- `Food.kt`: `// TODO: 【未確定】2-1` エサカードの逃走ダイス目と方向
- `TilePlacementEngine.kt`: `// TODO: 【要確認】3-1` 隣接に裏向きタイルがない場合の掘る処理
- `TilePlacementEngine.kt`: `// TODO: 【要確認】3-2` 穴タイル配置先の選択ルール
- `GameEngine.kt`: `// TODO: 【要確認】3-3` 強奪を行うフェーズ
- `MovementEngine.kt`: `// TODO: 【要確認】3-4` 巣防衛の接続ルール
- `GameEngine.kt`: `// TODO: 【要確認】3-4` 巣防衛の詳細
- `GameEngine.kt`: `// TODO: 【要確認】3-5` 追い出し先の選択権
- `moguru_requirements_v2(3).txt`: `12-1` 各エサカードの具体的な逃走ダイス目と方向
- `moguru_requirements_v2(3).txt`: `12-2` 未開示の追加ルール
- `moguru_requirements_v2(3).txt`: `13-6` 地上と地下の接続条件

## 次の作業
- 未確定 / 要確認ルールの確定後に仮実装を調整する
- 強奪フェーズは移動後自動発動の仮実装なので、`13-3` 確定後に調整する
