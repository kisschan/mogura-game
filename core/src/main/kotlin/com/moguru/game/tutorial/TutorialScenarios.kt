package com.moguru.game.tutorial

import com.moguru.game.engine.PlayerConfig
import com.moguru.game.engine.TurnPhase
import com.moguru.game.model.Direction.BOTTOM
import com.moguru.game.model.Direction.LEFT
import com.moguru.game.model.Direction.RIGHT
import com.moguru.game.model.Direction.TOP
import com.moguru.game.model.FoodType
import com.moguru.game.model.Position
import com.moguru.game.model.Rotation
import com.moguru.game.model.TileShape
import com.moguru.game.presenter.DigTileChoice
import com.moguru.game.presenter.MoguraGameController

/** The practice lessons defined in chapter 16 of the requirements. */
object TutorialScenarios {
    val basic: TutorialScenario by lazy { basicLesson() }
    val diceEscape: TutorialScenario by lazy { diceEscapeLesson() }
    val surfaceHealth: TutorialScenario by lazy { surfaceHealthLesson() }
    val robberyDefense: TutorialScenario by lazy { robberyLesson(TutorialLessonId.ROBBERY_DEFENSE) }
    val robberyCarry: TutorialScenario by lazy { robberyLesson(TutorialLessonId.ROBBERY_CARRY) }
    val nestEat: TutorialScenario by lazy { nestEatLesson() }

    val additional: List<TutorialScenario> get() = listOf(diceEscape, surfaceHealth, robberyDefense, robberyCarry, nestEat)
    val all: List<TutorialScenario> get() = listOf(basic) + additional

    fun of(lesson: TutorialLessonId): TutorialScenario = all.first { it.lesson == lesson }
}

private fun p(col: Int, row: Int) = Position(col, row)

private val S = TileShape.STRAIGHT
private val L = TileShape.L_SHAPE
private val T = TileShape.T_SHAPE
private val X = TileShape.CROSS

private const val MOGUO = 0
private const val MOGUTA = 1
private val NEST_A = p(0, 1)
private val NEST_B = p(5, 1)
private val NEST_C = p(0, 4)
private val NEST_D = p(5, 4)

private fun mole(id: Int, nest: Position) = PlayerConfig(MoguraGameController.playerNameForId(id), nest, playerId = id)

/** Board rows 2〜5 (columns 2〜5) followed by the draw pile from the top. */
private fun deal(vararg rows: List<TileShape>, pile: List<TileShape>): List<TileShape> = rows.flatMap { it } + pile

/** Hot zone food in board order, then the stock from the top. */
private fun foodOrder(vararg hotZone: FoodType): List<FoodType> {
    val deck = (List(4) { FoodType.BEETLE_LARVA } + List(3) { FoodType.EARTHWORM } +
        List(3) { FoodType.MOLE_CRICKET } + List(2) { FoodType.CENTIPEDE }).toMutableList()
    hotZone.forEach { deck.remove(it) }
    return hotZone.toList() + deck
}

private val learnerTurnOpen: (TutorialContext) -> Boolean = {
    it.learnersTurn && it.phase == TurnPhase.DIG && it.controller.pendingDigPlacement == null
}

private const val TAKE_ANY_TEXT = "好きなタイルと向きで「置く」"

/** Shown only once the capture attempt has turned the card over. */
private const val LARVA_REVEALED = "幼虫はダイス不要！「捕獲する」を押そう"

// ---------------------------------------------------------------------------------------------
// 基本編
// ---------------------------------------------------------------------------------------------

private fun basicLesson(): TutorialScenario {
    val dig = p(4, 4)
    val chapters = listOf(
        // 第1章: 駒・巣・体力 → 掘る → 移動
        TutorialChapter(
            stage = 1,
            explanations = listOf(
                TutorialExplanation(
                    "intro", 1,
                    listOf(
                        "青い駒が君のモグラ。左の巣から出発",
                        "体力は13。手番を終えるたびに減るよ",
                        "エサを巣へ持ち帰ると得点になるよ",
                        "手番は掘る→いどう→捕獲の順だよ",
                    ),
                    setOf(NEST_A), learnerTurnOpen,
                ),
                TutorialExplanation(
                    "move", 3,
                    listOf("道がつながったマスへ移動できるよ", "道が続けば何マスでも進めるよ"),
                ) { it.learnersTurn && it.phase == TurnPhase.MOVE },
                TutorialExplanation("opponent", 3, listOf("次は相手役モグタの番。見ていてね")) { !it.learnersTurn },
            ),
        ) { context ->
            context.guidedDig(
                2, p(1, 1), "黄色い枠の2列2行を掘ろう", "巣とつながる向きに回して「置く」",
                sides = setOf(LEFT, RIGHT), placeRefusal = "左右に道が開く向きにしよう",
            ) ?: context.guidedMove(3, p(1, 1), "青い枠を選び「このマスへ移動」")
        },
        // 第2章: タイルを選んで掘る → 移動
        TutorialChapter(
            stage = 2,
            explanations = listOf(
                TutorialExplanation(
                    "choose", 2,
                    listOf("めくったタイルか山札の1枚を選ぶよ", "選ばなかった方は捨て札になるよ"),
                ) { it.learnersTurn && it.controller.pendingDigPlacement != null },
            ),
        ) { context ->
            context.guidedDig(
                2, p(2, 1), "黄色い枠の3列2行を掘ろう", "山札のL字を選んで「置く」",
                sides = setOf(LEFT, BOTTOM), placeRefusal = "山札のL字で左と下を開こう",
                placeControls = setOf(TutorialControl.DIG_TILE_DRAWN, TutorialControl.CONFIRM_DIG),
            ) ?: context.guidedMove(3, p(2, 1), "青い枠の3列2行へ移動しよう")
        },
        // 第3章: 捕獲 → タベる → 手番終了の消費
        TutorialChapter(
            stage = 4,
            explanations = listOf(
                TutorialExplanation(
                    "capture", 4,
                    listOf("エサのマスに止まると捕獲できるよ", "捕獲は移動のあとにだけできるよ"),
                    setOf(p(2, 2)),
                ) { it.learnersTurn && it.phase == TurnPhase.CAPTURE && it.controller.pendingCaptureRoll == null },
                TutorialExplanation(
                    "captured", 5,
                    listOf("ほかのエサはダイスを振って判定するよ", "捕まえたらタベるかレンコウを選ぶ", "タベると回復、レンコウは得点用"),
                ) { it.controller.pendingFoodDecision != null },
                TutorialExplanation("eaten", 5, listOf("タベて体力が11から12に回復したよ")) {
                    TutorialAction.Eat in it.accepted
                },
                TutorialExplanation("cost", 5, listOf("手番を終えたので体力が1減ったよ", "回復と消費は別々に起きるよ")) {
                    !it.learnersTurn
                },
            ),
        ) { context ->
            context.guidedDig(
                4, p(2, 2), "黄色い枠の3列3行を掘ろう", "上と右が開く向きで「置く」",
                sides = setOf(TOP, RIGHT), placeRefusal = "上と右に道が開く向きにしよう",
            ) ?: context.guidedMove(4, p(2, 2), "青い枠の3列3行へ移動しよう")
                ?: context.guidedCapture(
                    4, "赤い枠のエサを「捕獲」しよう", rollText = "ダイスを振って判定しよう",
                    revealedText = LARVA_REVEALED,
                )
                ?: context.guidedDecision(5, "「タベる」で体力を回復しよう", eat = true, carry = false, refusal = "今回はタベて回復してみよう")
        },
        // 第4章: 捕獲 → レンコウ
        TutorialChapter(
            stage = 6,
            explanations = listOf(
                TutorialExplanation(
                    "carry", 6,
                    listOf("レンコウで巣まで運ぶと得点だよ", "捕まえただけでは得点にならない"),
                ) { it.controller.pendingFoodDecision != null },
                TutorialExplanation(
                    "carrying", 6,
                    listOf("運ぶ間はほかのエサを捕獲できない", "途中でタベたり捨てたりもできない"),
                ) { it.learner.isCarrying },
            ),
        ) { context ->
            context.guidedDig(
                6, p(3, 2), "黄色い枠の4列3行を掘ろう", "左右が開く向きで「置く」",
                sides = setOf(LEFT, RIGHT), placeRefusal = "左右に道が開く向きにしよう",
            ) ?: context.guidedMove(6, p(3, 2), "青い枠の4列3行へ移動しよう")
                ?: context.guidedCapture(
                    6, "赤い枠のエサを「捕獲」しよう", rollText = "ダイスを振って判定しよう",
                    revealedText = LARVA_REVEALED,
                )
                ?: context.guidedDecision(6, "「レンコウ」で巣へ運ぼう", eat = false, carry = true, refusal = "今回はレンコウしてみよう")
        },
        // 第5章: 帰巣で初得点。巣のエサはタベずに手番終了
        TutorialChapter(
            stage = 6,
            explanations = listOf(
                TutorialExplanation("carry-turn", 6, listOf("運ぶ間も掘る・移動はいつも通り"), showsWhen = learnerTurnOpen),
                TutorialExplanation(
                    "far", 6, listOf("巣まで道がつながったよ", "道が続けば何マスでも進めるよ"), setOf(NEST_A),
                ) { it.learnersTurn && it.phase == TurnPhase.MOVE },
                TutorialExplanation(
                    "scored", 6,
                    listOf("巣に着いて1点！これが得点だよ", "巣のエサをタベるとその点を失う", "今回はタベずに「手番終了」しよう"),
                ) { it.learner.score >= 1 },
            ),
        ) { context ->
            context.guidedDig(6, p(4, 2), "5列3行を掘ろう。置き方は自由", TAKE_ANY_TEXT)
                ?: context.guidedMove(6, NEST_A, "青い枠の巣まで移動しよう")
                ?: context.guidedFinish(
                    6, "タベずに「手番終了」を押そう", "巣のエサをタベると1点を失うよ",
                    setOf(TurnPhase.DECIDE, TurnPhase.END),
                )
        },
        // 第6章: 自力で掘って移動
        TutorialChapter(
            stage = 7,
            explanations = listOf(
                TutorialExplanation(
                    "self", 7, listOf("山札が尽きて捨て札を混ぜ直したよ", "最後は自分で掘って移動しよう"),
                    showsWhen = learnerTurnOpen,
                ),
            ),
            completesWhen = { context -> context.accepted.any { it is TutorialAction.Move } },
        ) { context -> context.selfPractice() },
    )
    val solution = listOf(
        listOf(
            TutorialAction.SelectDigCell(p(1, 1)), TutorialAction.RotateDigTile(Rotation.DEG_90), TutorialAction.ConfirmDig,
            TutorialAction.SelectMoveTarget(p(1, 1)), TutorialAction.Move(p(1, 1)),
        ),
        listOf(
            TutorialAction.SelectDigCell(p(2, 1)), TutorialAction.SelectDigTile(DigTileChoice.DRAWN), TutorialAction.ConfirmDig,
            TutorialAction.SelectMoveTarget(p(2, 1)), TutorialAction.Move(p(2, 1)),
        ),
        listOf(
            TutorialAction.SelectDigCell(p(2, 2)), TutorialAction.RotateDigTile(Rotation.DEG_180), TutorialAction.ConfirmDig,
            TutorialAction.SelectMoveTarget(p(2, 2)), TutorialAction.Move(p(2, 2)),
            TutorialAction.Capture, TutorialAction.ResolveCapture, TutorialAction.Eat,
        ),
        listOf(
            TutorialAction.SelectDigCell(p(3, 2)), TutorialAction.RotateDigTile(Rotation.DEG_90), TutorialAction.ConfirmDig,
            TutorialAction.SelectMoveTarget(p(3, 2)), TutorialAction.Move(p(3, 2)),
            TutorialAction.SelectCaptureTarget(0), TutorialAction.Capture, TutorialAction.ResolveCapture, TutorialAction.Carry,
        ),
        listOf(
            TutorialAction.SelectDigCell(p(4, 2)), TutorialAction.ConfirmDig,
            TutorialAction.SelectMoveTarget(NEST_A), TutorialAction.Move(NEST_A), TutorialAction.FinishTurn,
        ),
        listOf(
            TutorialAction.SelectDigCell(p(1, 1)), TutorialAction.ConfirmDig,
            TutorialAction.SelectMoveTarget(p(2, 1)), TutorialAction.Move(p(2, 1)),
        ),
    )
    return TutorialScenario(
        lesson = TutorialLessonId.BASIC,
        version = 1,
        title = "基本編",
        summary = "掘る・いどう・捕獲・タベる・レンコウ",
        learner = mole(MOGUO, NEST_A),
        opponent = mole(MOGUTA, NEST_D),
        tiles = BASIC_TILES,
        foods = foodOrder(),
        rolls = emptyList(),
        prelude = emptyList(),
        chapters = chapters,
        opponentScript = digAndStay(dig),
        solution = solution,
        completionText = "練習クリア！本番は2〜3人4点、4人5点で勝ち",
        stageCount = 7,
    )
}

private val BASIC_TILES = deal(
    listOf(S, S, L, L),
    listOf(L, L, S, T),
    listOf(S, L, S, T),
    listOf(S, L, S, S),
    pile = listOf(L, T, L, X, S, T, L, X, S, L),
)

/** 基本編の第1〜4章の操作。巣のエサの練習では相手役の手番と合わせて自動で再生する。 */
private fun basicOpening(): List<TutorialAction> {
    val opponent = listOf(TutorialAction.SelectDigCell(p(4, 4)), TutorialAction.ConfirmDig)
    return listOf(
        TutorialAction.SelectDigCell(p(1, 1)), TutorialAction.RotateDigTile(Rotation.DEG_90), TutorialAction.ConfirmDig,
        TutorialAction.Move(p(1, 1)),
    ) + opponent + listOf(
        TutorialAction.SelectDigCell(p(2, 1)), TutorialAction.SelectDigTile(DigTileChoice.DRAWN), TutorialAction.ConfirmDig,
        TutorialAction.Move(p(2, 1)),
    ) + opponent + listOf(
        TutorialAction.SelectDigCell(p(2, 2)), TutorialAction.RotateDigTile(Rotation.DEG_180), TutorialAction.ConfirmDig,
        TutorialAction.Move(p(2, 2)), TutorialAction.Capture, TutorialAction.ResolveCapture, TutorialAction.Eat,
    ) + opponent + listOf(
        TutorialAction.SelectDigCell(p(3, 2)), TutorialAction.RotateDigTile(Rotation.DEG_90), TutorialAction.ConfirmDig,
        TutorialAction.Move(p(3, 2)), TutorialAction.Capture, TutorialAction.ResolveCapture, TutorialAction.Carry,
    ) + opponent
}

/** The last basic chapter: any legal dig that joins the learner's cell, then a real move. */
private fun TutorialContext.selfPractice(): TutorialStep? {
    if (!learnersTurn) return null
    return when (phase) {
        TurnPhase.DIG -> if (controller.pendingDigPlacement == null) {
            val targets = controller.digTargets()
            TutorialStep(7, "掘る場所を選ぼう") { action ->
                if (action is TutorialAction.SelectDigCell && action.position in targets) null else "黄色い枠のマスから選ぼう"
            }
        } else {
            TutorialStep(7, "道がつながる向きで「置く」", controls = setOf(TutorialControl.CONFIRM_DIG)) { action ->
                when (action) {
                    is TutorialAction.SelectDigTile, is TutorialAction.RotateDigTile -> null
                    TutorialAction.ConfirmDig -> if (previewJoinsLearner()) null else "今いる場所と道がつながる向きにしよう"
                    else -> PLACE_FIRST
                }
            }
        }
        TurnPhase.MOVE -> {
            val targets = controller.moveTargets()
            TutorialStep(7, "移動先を選んで「このマスへ移動」", controls = setOf(TutorialControl.MOVE)) { action ->
                val target = when (action) {
                    is TutorialAction.SelectMoveTarget -> action.position
                    is TutorialAction.Move -> action.position
                    else -> null
                }
                if (target != null && target in targets) null else "今いる場所以外へ移動してみよう"
            }
        }
        else -> null
    }
}

// ---------------------------------------------------------------------------------------------
// 追加レッスン 1: ダイスと逃走
// ---------------------------------------------------------------------------------------------

private fun diceEscapeLesson(): TutorialScenario {
    val chapters = listOf(
        TutorialChapter(
            stage = 1,
            explanations = listOf(
                TutorialExplanation(
                    "intro", 1,
                    listOf("ダイスと逃走を練習しよう", "練習用にダイスの目を決めてあるよ", "まずエサのマスまで道を進めよう"),
                    showsWhen = learnerTurnOpen,
                ),
                TutorialExplanation(
                    "escaped", 2,
                    listOf("出目2は下の矢印。エサが下へ逃げた", "逃げたエサは表向きで残るよ", "次の手番に追いかけよう"),
                    setOf(p(2, 4)),
                ) { it.learnersTurn && it.lastCaptureEscaped() },
            ),
        ) { context ->
            context.guidedDig(
                1, p(2, 3), "黄色い枠の3列4行を掘ろう", "上下が開く向きで「置く」",
                sides = setOf(TOP, BOTTOM), placeRefusal = "上下に道が開く向きにしよう",
            ) ?: context.guidedMove(1, p(2, 3), "青い枠の3列4行へ移動しよう")
                ?: context.guidedCapture(
                    2, "赤い枠のエサを「捕獲」しよう", rollText = "ミミズは1と2で逃げる。ダイスを振ろう",
                    revealedText = "「捕獲する」で確定しよう",
                )
                ?: context.guidedFinish(2, "「手番終了」を押そう", "今回は「手番終了」を押そう", setOf(TurnPhase.END))
        },
        TutorialChapter(
            stage = 2,
            explanations = listOf(
                TutorialExplanation("chase", 2, listOf("逃げたミミズを追いかけよう"), setOf(p(2, 4)), learnerTurnOpen),
                TutorialExplanation(
                    "blocked", 3,
                    listOf("出目2でも下は盤の外。逃げられない", "逃げ先がない時は捕獲成功だよ"),
                ) { it.controller.pendingFoodDecision != null },
            ),
            completesWhen = { context -> TutorialAction.Eat in context.accepted || TutorialAction.Carry in context.accepted },
        ) { context ->
            context.guidedDig(2, p(2, 2), "黄色い枠の3列3行を掘ろう", TAKE_ANY_TEXT)
                ?: context.guidedMove(2, p(2, 4), "青い枠の3列5行へ移動しよう")
                ?: context.guidedCapture(
                    3, "ミミズを「捕獲」しよう", rollText = "下は盤の外。ダイスを振ろう",
                    revealedText = "「捕獲する」で確定しよう",
                )
                ?: context.guidedDecision(3, "タベるかレンコウを選ぼう", eat = true, carry = true, refusal = "タベるかレンコウを選ぼう")
        },
    )
    val opponentTurn = listOf(TutorialAction.SelectDigCell(p(4, 1)), TutorialAction.ConfirmDig)
    return TutorialScenario(
        lesson = TutorialLessonId.DICE_ESCAPE,
        version = 1,
        title = "ダイスと逃走",
        summary = "出目と矢印、逃げられない時の捕獲",
        learner = mole(MOGUO, NEST_C),
        opponent = mole(MOGUTA, NEST_B),
        tiles = deal(
            listOf(L, S, L, S),
            listOf(L, T, S, L),
            listOf(S, S, L, T),
            listOf(S, L, S, L),
            pile = listOf(L, S, L, T, L, S, X, T, S, X),
        ),
        foods = foodOrder(FoodType.BEETLE_LARVA, FoodType.BEETLE_LARVA, FoodType.EARTHWORM, FoodType.BEETLE_LARVA),
        rolls = listOf(2, 2),
        prelude = listOf(
            TutorialAction.SelectDigCell(p(1, 4)), TutorialAction.RotateDigTile(Rotation.DEG_90), TutorialAction.ConfirmDig,
            TutorialAction.Move(p(1, 4)),
        ) + opponentTurn + listOf(
            TutorialAction.SelectDigCell(p(2, 4)), TutorialAction.RotateDigTile(Rotation.DEG_90), TutorialAction.ConfirmDig,
            TutorialAction.Move(p(2, 4)),
        ) + opponentTurn,
        chapters = chapters,
        opponentScript = digAndStay(p(4, 1)),
        solution = listOf(
            listOf(
                TutorialAction.SelectDigCell(p(2, 3)), TutorialAction.ConfirmDig,
                TutorialAction.SelectMoveTarget(p(2, 3)), TutorialAction.Move(p(2, 3)),
                TutorialAction.Capture, TutorialAction.RollDice, TutorialAction.ResolveCapture, TutorialAction.FinishTurn,
            ),
            listOf(
                TutorialAction.SelectDigCell(p(2, 2)), TutorialAction.ConfirmDig,
                TutorialAction.SelectMoveTarget(p(2, 4)), TutorialAction.Move(p(2, 4)),
                TutorialAction.Capture, TutorialAction.RollDice, TutorialAction.ResolveCapture, TutorialAction.Eat,
            ),
        ),
        completionText = "ダイスと逃走のレッスンクリア！",
        stageCount = 3,
    )
}

// ---------------------------------------------------------------------------------------------
// 追加レッスン 2: 地上と体力
// ---------------------------------------------------------------------------------------------

private fun surfaceHealthLesson(): TutorialScenario {
    val chapters = listOf(
        TutorialChapter(
            stage = 1,
            explanations = listOf(
                TutorialExplanation(
                    "intro", 1, listOf("地上と体力の減り方を練習しよう", "一番上の段は地上だよ"),
                    showsWhen = learnerTurnOpen,
                ),
                TutorialExplanation("under", 1, listOf("地下で手番を終えたので体力−1")) { !it.learnersTurn },
            ),
        ) { context ->
            context.guidedDig(
                1, p(1, 1), "黄色い枠の2列2行を掘ろう", "上と左右が開く向きで「置く」",
                sides = setOf(TOP, LEFT, RIGHT), placeRefusal = "上と左右に道が開く向きにしよう",
            ) ?: context.guidedMove(1, p(1, 1), "青い枠の2列2行へ移動しよう")
        },
        TutorialChapter(
            stage = 2,
            explanations = listOf(
                TutorialExplanation(
                    "exit", 2, listOf("穴が上に開くと地上へ出られるよ"), setOf(p(1, 0)),
                ) { it.learnersTurn && it.phase == TurnPhase.MOVE },
                TutorialExplanation(
                    "surface", 2, listOf("地上で手番を終えると体力−2！", "地上は減りが大きいので注意"),
                ) { !it.learnersTurn },
            ),
        ) { context ->
            context.guidedDig(2, p(2, 1), "黄色い枠の3列2行を掘ろう", TAKE_ANY_TEXT)
                ?: context.guidedMove(2, p(1, 0), "地上の2列1行へ出よう")
        },
        TutorialChapter(
            stage = 3,
            explanations = listOf(
                TutorialExplanation("back", 3, listOf("地下に戻ると減り方は−1に戻るよ")) { !it.learnersTurn },
            ),
            completesWhen = { context -> context.shown("back") },
        ) { context ->
            context.guidedDig(
                3, p(1, 1), "黄色い枠の2列2行を掘ろう", "上が開く向きで「置く」",
                sides = setOf(TOP), placeRefusal = "上に道が開く向きにしよう",
            ) ?: context.guidedMove(3, p(1, 1), "地下の2列2行へ戻ろう")
        },
    )
    return TutorialScenario(
        lesson = TutorialLessonId.SURFACE_HEALTH,
        version = 1,
        title = "地上と体力",
        summary = "地下は−1、地上は−2",
        learner = mole(MOGUO, NEST_A),
        opponent = mole(MOGUTA, NEST_D),
        tiles = deal(
            listOf(T, L, S, L),
            listOf(S, L, S, L),
            listOf(L, S, L, S),
            listOf(S, L, T, S),
            pile = listOf(L, S, X, T, S, L, X, T, S, L),
        ),
        foods = foodOrder(),
        rolls = emptyList(),
        prelude = emptyList(),
        chapters = chapters,
        opponentScript = digAndStay(p(4, 4)),
        solution = listOf(
            listOf(
                TutorialAction.SelectDigCell(p(1, 1)), TutorialAction.ConfirmDig,
                TutorialAction.SelectMoveTarget(p(1, 1)), TutorialAction.Move(p(1, 1)),
            ),
            listOf(
                TutorialAction.SelectDigCell(p(2, 1)), TutorialAction.ConfirmDig,
                TutorialAction.SelectMoveTarget(p(1, 0)), TutorialAction.Move(p(1, 0)),
            ),
            listOf(
                TutorialAction.SelectDigCell(p(1, 1)), TutorialAction.ConfirmDig,
                TutorialAction.SelectMoveTarget(p(1, 1)), TutorialAction.Move(p(1, 1)),
            ),
        ),
        completionText = "地上と体力のレッスンクリア！",
        stageCount = 3,
    )
}

// ---------------------------------------------------------------------------------------------
// 追加レッスン 3・4: 強奪と防衛 / 強奪後のレンコウ
// ---------------------------------------------------------------------------------------------

/**
 * Both robbery lessons start alike: the opponent has carried a larva home and left its nest
 * again, and the learner waits one move away. They differ from the robbery on.
 */
private fun robberyLesson(lesson: TutorialLessonId): TutorialScenario {
    val defense = lesson == TutorialLessonId.ROBBERY_DEFENSE
    val opponentNest = NEST_C
    val evicted = p(1, 4)
    val second = if (defense) {
        TutorialChapter(
            stage = 2,
            explanations = listOf(
                TutorialExplanation(
                    "ready", 2, listOf("次の自分の番。強奪の準備ができた", "掘ったら「移動しない」で巣に残る"),
                    showsWhen = learnerTurnOpen,
                ),
                TutorialExplanation("robbed", 2, listOf("強奪成功！すぐタベるかレンコウ")) {
                    it.controller.pendingFoodDecision != null
                },
                TutorialExplanation(
                    "evicted", 3, listOf("持ち主が戻ると侵入者は追い出される", "自分が巣にいれば相手は入れない"),
                    setOf(evicted),
                ) { it.learner.position == evicted && it.opponent.position == opponentNest },
            ),
            completesWhen = { context -> context.shown("evicted") },
        ) { context -> context.robberyTurn(defense = true) }
    } else {
        TutorialChapter(
            stage = 2,
            explanations = listOf(
                TutorialExplanation(
                    "ready", 2, listOf("次の自分の番。強奪の準備ができた", "掘ったら「移動しない」で巣に残る"),
                    showsWhen = learnerTurnOpen,
                ),
                TutorialExplanation(
                    "sent", 3,
                    listOf("奪ったエサはすぐ自分の巣へ。1点！", "モグラは相手の巣に残ったままだよ", "捕まえたエサは巣まで運ぶ必要あり"),
                    setOf(opponentNest),
                ) { it.learner.score >= 1 },
            ),
            completesWhen = { context -> context.shown("sent") },
        ) { context -> context.robberyTurn(defense = false) }
    }
    val chapters = listOf(
        TutorialChapter(
            stage = 1,
            explanations = listOf(
                TutorialExplanation(
                    "intro", 1,
                    listOf("モグタが巣にエサを1つ貯めたよ", "モグタは巣を留守にしている", "留守の巣に入るとエサを奪える"),
                    setOf(opponentNest), learnerTurnOpen,
                ),
                TutorialExplanation(
                    "entered", 1,
                    listOf("相手の巣に入った手番は奪えない", "次の自分の番まで巣に残ろう"),
                    setOf(opponentNest),
                ) { it.learner.position == opponentNest },
            ),
        ) { context ->
            context.guidedDig(1, p(1, 1), "黄色い枠の2列2行を掘ろう", TAKE_ANY_TEXT)
                ?: context.guidedMove(1, opponentNest, "青い枠のモグタの巣へ入ろう")
        },
        second,
    )
    val last = if (defense) TutorialAction.Eat else TutorialAction.Carry
    return TutorialScenario(
        lesson = lesson,
        version = 1,
        title = if (defense) "強奪と防衛" else "強奪後のレンコウ",
        summary = if (defense) "入った次の番に強奪、持ち主の帰巣" else "奪ったエサはすぐ自分の巣へ",
        learner = mole(MOGUO, NEST_A),
        opponent = mole(MOGUTA, opponentNest),
        tiles = deal(
            listOf(L, S, L, S),
            listOf(S, L, S, L),
            listOf(T, S, S, L),
            listOf(L, S, L, T),
            pile = listOf(L, S, S, T, L, X, S, L, T, X),
        ),
        foods = foodOrder(),
        rolls = emptyList(),
        prelude = ROBBERY_PRELUDE,
        chapters = chapters,
        opponentScript = digAndStay(p(3, 3)) { context -> if (context.chapter == 0) null else opponentNest },
        solution = listOf(
            listOf(
                TutorialAction.SelectDigCell(p(1, 1)), TutorialAction.ConfirmDig,
                TutorialAction.SelectMoveTarget(opponentNest), TutorialAction.Move(opponentNest),
            ),
            listOf(
                TutorialAction.SelectDigCell(p(1, 4)), TutorialAction.ConfirmDig, TutorialAction.Skip,
                TutorialAction.SelectRobberyTarget(0), TutorialAction.Rob, last,
            ),
        ),
        completionText = if (defense) "強奪と防衛のレッスンクリア！" else "強奪後のレンコウのレッスンクリア！",
        stageCount = 3,
    )
}

/** The learner's second robbery turn: dig, stay in the nest, rob, then decide. */
private fun TutorialContext.robberyTurn(defense: Boolean): TutorialStep? =
    guidedDig(
        2, p(1, 4), "黄色い枠の2列5行を掘ろう", "上と左が開く向きで「置く」",
        sides = setOf(TOP, LEFT), placeRefusal = "上と左に道が開く向きにしよう",
    ) ?: guidedStay(2, "「移動しない」で巣に残ろう", "強奪するには巣に残ろう")
        ?: guidedRobbery(2, "エサを選んで「強奪」しよう")
        ?: if (defense) {
            guidedDecision(2, "タベるかレンコウを選ぼう", eat = true, carry = true, refusal = "タベるかレンコウを選ぼう")
        } else {
            guidedDecision(3, "「レンコウ」でエサを巣へ送ろう", eat = false, carry = true, refusal = "今回はレンコウしてみよう")
        }

/**
 * モグオは(2,2)・(2,3)まで道を作って待つ。モグタは(2,5)・(2,4)から(3,4)の幼虫を捕まえて巣へ運び、
 * 再び(3,4)へ出る。どちらも合法手だけで進める。
 */
private val ROBBERY_PRELUDE: List<TutorialAction> = listOf(
    // モグオ1
    TutorialAction.SelectDigCell(p(1, 1)), TutorialAction.ConfirmDig, TutorialAction.Move(p(1, 1)),
    // モグタ1
    TutorialAction.SelectDigCell(p(1, 4)), TutorialAction.RotateDigTile(Rotation.DEG_90), TutorialAction.ConfirmDig,
    TutorialAction.Move(p(1, 4)),
    // モグオ2
    TutorialAction.SelectDigCell(p(1, 2)), TutorialAction.ConfirmDig, TutorialAction.Move(p(1, 2)),
    // モグタ2
    TutorialAction.SelectDigCell(p(1, 3)), TutorialAction.RotateDigTile(Rotation.DEG_90), TutorialAction.ConfirmDig,
    TutorialAction.Move(p(1, 3)),
    // モグオ3
    TutorialAction.SelectDigCell(p(1, 1)), TutorialAction.ConfirmDig, TutorialAction.Skip,
    // モグタ3: 幼虫を捕まえてレンコウ
    TutorialAction.SelectDigCell(p(2, 3)), TutorialAction.RotateDigTile(Rotation.DEG_90), TutorialAction.ConfirmDig,
    TutorialAction.Move(p(2, 3)), TutorialAction.Capture, TutorialAction.ResolveCapture, TutorialAction.Carry,
    // モグオ4
    TutorialAction.SelectDigCell(p(1, 1)), TutorialAction.ConfirmDig, TutorialAction.Skip,
    // モグタ4: 帰巣して1点。巣のエサはタベない
    TutorialAction.SelectDigCell(p(3, 3)), TutorialAction.ConfirmDig, TutorialAction.Move(NEST_C), TutorialAction.Skip,
    // モグオ5
    TutorialAction.SelectDigCell(p(1, 1)), TutorialAction.ConfirmDig, TutorialAction.Skip,
    // モグタ5: 巣を出る
    TutorialAction.SelectDigCell(p(1, 4)), TutorialAction.ConfirmDig, TutorialAction.Move(p(2, 3)),
)

// ---------------------------------------------------------------------------------------------
// 追加レッスン 5: 巣のエサをタベる
// ---------------------------------------------------------------------------------------------

private fun nestEatLesson(): TutorialScenario {
    val chapters = listOf(
        TutorialChapter(
            stage = 1,
            explanations = listOf(
                TutorialExplanation(
                    "intro", 1, listOf("巣のエサをタベる練習だよ", "まずエサを巣まで運ぼう"), setOf(NEST_A), learnerTurnOpen,
                ),
                TutorialExplanation(
                    "home", 2, listOf("巣に着いて1点！", "巣に戻った直後は巣のエサをタベられる"),
                ) { it.learner.score >= 1 },
                TutorialExplanation(
                    "lost", 3, listOf("体力は回復したが1点を失った", "得点を守るなら巣のエサは残そう"),
                ) { TutorialAction.Eat in it.accepted },
            ),
            completesWhen = { context -> context.shown("lost") },
        ) { context ->
            context.guidedDig(1, p(4, 2), "黄色い枠の5列3行を掘ろう", TAKE_ANY_TEXT)
                ?: context.guidedMove(1, NEST_A, "青い枠の巣まで移動しよう")
                ?: context.nestEat()
        },
    )
    return TutorialScenario(
        lesson = TutorialLessonId.NEST_EAT,
        version = 1,
        title = "巣のエサをタベる",
        summary = "巣のエサを食べると得点を失う",
        learner = mole(MOGUO, NEST_A),
        opponent = mole(MOGUTA, NEST_D),
        tiles = BASIC_TILES,
        foods = foodOrder(),
        rolls = emptyList(),
        prelude = basicOpening(),
        chapters = chapters,
        opponentScript = digAndStay(p(4, 4)),
        solution = listOf(
            listOf(
                TutorialAction.SelectDigCell(p(4, 2)), TutorialAction.ConfirmDig,
                TutorialAction.SelectMoveTarget(NEST_A), TutorialAction.Move(NEST_A), TutorialAction.Eat,
            ),
        ),
        completionText = "巣のエサのレッスンクリア！",
        stageCount = 3,
    )
}

/** Eating from the learner's own nest, right after coming home. */
private fun TutorialContext.nestEat(): TutorialStep? {
    if (!learnersTurn || phase != TurnPhase.DECIDE || controller.pendingFoodDecision != null) return null
    if (!controller.playScreenUiState().actionAvailability.canEat) return null
    return TutorialStep(2, "「タベる」で巣のエサを食べよう", setOf(NEST_A), setOf(TutorialControl.EAT)) { action ->
        if (action == TutorialAction.Eat) null else "今回はタベてみよう"
    }
}
