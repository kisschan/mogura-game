# PROGRESS.md

最終更新: 2026-03-05

## 参照元
- `C:\Users\USER\Downloads\claude_code_prompt.txt`
- `d:\moguraGame\CLAUDE.md`

## 実装スコープ進捗（claude_code_prompt準拠）
- [x] 1. データモデル（Board, HoleTile, Food, Player）
- [ ] 2. 穴タイル配置ロジック（TilePlacementEngine）
- [x] 3. 移動経路探索（MovementEngine）
- [ ] 4. ターン進行管理（GameEngine）

## 実装済みファイル
- `src/main/kotlin/com/moguru/game/model/Board.kt`
- `src/main/kotlin/com/moguru/game/model/HoleTile.kt`
- `src/main/kotlin/com/moguru/game/model/Food.kt`
- `src/main/kotlin/com/moguru/game/model/Player.kt`
- `src/main/kotlin/com/moguru/game/engine/MovementEngine.kt`
- `src/main/kotlin/com/moguru/game/util/Dice.kt`

## テスト状況
- 実行日時: 2026-03-05
- 実行コマンド: `./gradlew.bat clean test`
- 結果: `BUILD SUCCESSFUL`

### 既存テストクラス
- `src/test/kotlin/com/moguru/game/model/BoardTest.kt`
- `src/test/kotlin/com/moguru/game/model/HoleTileTest.kt`
- `src/test/kotlin/com/moguru/game/model/FoodTest.kt`
- `src/test/kotlin/com/moguru/game/model/PlayerTest.kt`
- `src/test/kotlin/com/moguru/game/engine/MovementEngineTest.kt`
- `src/test/kotlin/com/moguru/game/util/DiceTest.kt`

## 要件に対する達成状況（抜粋）
- [x] 盤面の有効マス数26
- [x] 穴タイル4種類・回転
- [x] 移動経路探索（接続時/非接続時、停止不可マス通過）
- [x] 体力管理（地下-1、地上-2、上限13、0で脱落）
- [ ] 捕獲判定（成功/逃走/盤外で逃走不可）
- [ ] 穴タイル配置ロジック（掘るアクション一式）
- [ ] ターン進行管理（DIG/MOVE/CAPTURE/DECIDE/END）
- [ ] 勝利条件判定（2〜3人:4点、4人:5点、全脱落ドロー）
- [ ] エサ補充・強奪・巣の防衛

## 未実装/次作業
1. `TilePlacementEngine.kt` の新規実装（山札・捨て札・掘る処理）
2. `GameEngine.kt` の新規実装（フェーズ遷移、体力減少、勝利判定）
3. 捕獲処理の実装（ダイス判定、逃走処理）
4. `GameEngineTest.kt` / `TilePlacementEngineTest.kt` の追加

## TODO（仕様未確定の反映）
- `Food.kt`: `// TODO: 【未確定】12-1`（逃走ダイス目・方向はダミーデータ）
- `MovementEngine.kt`: `// TODO: 【要確認】13-4`（巣マス接続ルールの仮実装）
