# PROGRESS.md

最終更新: 2026-03-06

## 実装スコープ進捗（claude_code_prompt準拠）
- [x] 1. データモデル（Board, HoleTile, Food, Player）
- [x] 2. 穴タイル配置ロジック（TilePlacementEngine）
- [x] 3. 移動経路探索（MovementEngine）
- [x] 4. ターン進行管理（GameEngine）

## テスト状況
- 実行日時: 2026-03-06
- テスト数: 101
- 失敗: 0
- 結果: `BUILD SUCCESSFUL`

## 実装済みファイル
### プロダクションコード
- `src/main/kotlin/com/moguru/game/model/Board.kt` — 盤面（6x5、有効26マス）
- `src/main/kotlin/com/moguru/game/model/HoleTile.kt` — 穴タイル4種類、回転
- `src/main/kotlin/com/moguru/game/model/Food.kt` — エサ5種類、逃走マップ
- `src/main/kotlin/com/moguru/game/model/Player.kt` — 体力/得点/連行/巣貯蔵
- `src/main/kotlin/com/moguru/game/util/Dice.kt` — DiceRoller/Shufflerインターフェース
- `src/main/kotlin/com/moguru/game/engine/MovementEngine.kt` — BFS経路探索、BoardState
- `src/main/kotlin/com/moguru/game/engine/TilePlacementEngine.kt` — 山札/捨て札/配置
- `src/main/kotlin/com/moguru/game/engine/GameEngine.kt` — ターン進行、捕獲、勝利判定

### テストコード
- `src/test/kotlin/com/moguru/game/model/BoardTest.kt` (11テスト)
- `src/test/kotlin/com/moguru/game/model/HoleTileTest.kt` (16テスト)
- `src/test/kotlin/com/moguru/game/model/FoodTest.kt` (13テスト)
- `src/test/kotlin/com/moguru/game/model/PlayerTest.kt` (12テスト)
- `src/test/kotlin/com/moguru/game/util/DiceTest.kt` (5テスト)
- `src/test/kotlin/com/moguru/game/engine/MovementEngineTest.kt` (9テスト)
- `src/test/kotlin/com/moguru/game/engine/TilePlacementEngineTest.kt` (8テスト)
- `src/test/kotlin/com/moguru/game/engine/GameEngineTest.kt` (27テスト)

## 要件に対する達成状況
- [x] 盤面の有効マス数26
- [x] 穴タイル4種類・回転パターン
- [x] 移動経路探索（接続時/非接続時、停止不可マス通過）
- [x] 捕獲判定（成功/逃走/盤外で逃走不可/逃走先にエサがある場合）
- [x] 穴タイル配置ロジック（山札/捨て札/掘るアクション）
- [x] ターン進行管理（DIG/MOVE/CAPTURE/DECIDE/END）
- [x] 体力管理（地下-1、地上-2、上限13、0で脱落）
- [x] 勝利条件判定（2〜3人:4点、4人:5点、全脱落ドロー）
- [x] エサ補充（ホットゾーン空で即時、表向きエサも対応）
- [x] 強奪（相手の巣が留守の時のみ）
- [x] 巣の防衛（在巣時侵入阻止、帰還時追い出し）

## 修正済みバグ
- P1: `attemptCaptureAt`で逃走先が盤外/無効マスの場合に捕獲成功を返すよう修正
- P2: `replenishFood`で表向きエサが残るスロットも捨て札→補充するよう修正
- P2: `checkGameOver`でplayersが空の時にFINISHEDにならないよう修正
- P1: `attemptCaptureAt`で逃走先に別のエサがある場合に上書きせず捕獲成功を返すよう修正

## TODO（仕様未確定）
- `Food.kt`: `// TODO: 【未確定】12-1`（逃走ダイス目・方向はダミーデータ）
- `MovementEngine.kt`: `// TODO: 【要確認】13-4`（巣マス接続ルール）
- `TilePlacementEngine.kt`: `// TODO: 【要確認】13-1`（隣接裏タイルなし時の処理）
- `TilePlacementEngine.kt`: `// TODO: 【要確認】13-2`（配置先の選択ルール）
- `GameEngine.kt`: `// TODO: 【要確認】13-3`（強奪フェーズ）
- `GameEngine.kt`: `// TODO: 【要確認】13-4`（巣防衛の詳細）
- `GameEngine.kt`: `// TODO: 【要確認】13-5`（追い出し先の選択権）

## 次作業
- UI層（Jetpack Compose）の実装（別スコープ）
- 未確定事項の設計者確認後の反映
- 追加ルール（12-2: 未開示ルール）の対応
