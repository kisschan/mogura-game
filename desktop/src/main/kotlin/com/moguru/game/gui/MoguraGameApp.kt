package com.moguru.game.gui

import com.moguru.game.engine.PlayerConfig
import com.moguru.game.engine.TurnPhase
import com.moguru.game.model.Board
import com.moguru.game.model.CellType
import com.moguru.game.model.FoodCard
import com.moguru.game.model.FoodType
import com.moguru.game.model.HoleTile
import com.moguru.game.model.Player
import com.moguru.game.model.Position
import com.moguru.game.model.Rotation
import com.moguru.game.model.TileShape
import com.moguru.game.presenter.*
import java.awt.BasicStroke
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.GridLayout
import java.awt.Image
import java.awt.Point
import java.awt.Rectangle
import java.awt.RenderingHints
import java.awt.Toolkit
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.ButtonGroup
import javax.swing.ImageIcon
import javax.swing.JButton
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTextArea
import javax.swing.JToggleButton
import javax.swing.SwingConstants
import javax.swing.SwingUtilities
import javax.swing.Timer
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private const val APP_TITLE = "モグる・タベる・イキのこる"

fun main() {
    SwingUtilities.invokeLater {
        MoguraGameFrame(MoguraGameController()).isVisible = true
    }
}

internal enum class BoardPaintLayer {
    HIGHLIGHT,
    DIG_DIRECTION,
    FOOD,
    PLAYERS,
    CURRENT_PLAYER_OUTLINE,
    HOVER_PREVIEW,
}

internal val boardPaintLayerOrder = listOf(
    BoardPaintLayer.HIGHLIGHT,
    BoardPaintLayer.DIG_DIRECTION,
    BoardPaintLayer.FOOD,
    BoardPaintLayer.PLAYERS,
    BoardPaintLayer.CURRENT_PLAYER_OUTLINE,
    BoardPaintLayer.HOVER_PREVIEW,
)

internal fun boardPaintRenderPlan(): List<BoardPaintLayer> = boardPaintLayerOrder

internal const val BOARD_HIGHLIGHT_FILL_ALPHA = 52
internal val DESKTOP_ROTATION_BUTTON_SIZE = Dimension(52, 40)
internal const val DESKTOP_SHOW_BUTTON_FOCUS = true
internal const val DESKTOP_CONFIRM_DIG_LABEL = "置く"

class MoguraGameFrame(
    private val controller: MoguraGameController,
    private val backgroundMusic: BackgroundMusicPlayer = defaultBackgroundMusicPlayer(BACKGROUND_MUSIC_PATH),
) : JFrame(APP_TITLE) {
    private val assets = GuiAssets()
    private val boardPanel = BoardPanel(controller, assets, ::handleBoardClick)
    private val currentPlayerPanel = CurrentPlayerPanel(assets)
    private val deckSummaryPanel = DeckSummaryPanel(controller, assets)
    private val statusLabel = JLabel()
    private val diceLabel = JLabel("ダイス", SwingConstants.CENTER)
    private val logArea = JTextArea()
    private val digGuideButton = JButton("掘る候補")
    private val confirmDigButton = JButton(DESKTOP_CONFIRM_DIG_LABEL)
    private val moveGuideButton = JButton("移動候補")
    private val captureButton = JButton("捕獲")
    private val robButton = JButton("強奪")
    private val eatButton = JButton("食べる")
    private val carryButton = JButton("巣へ持ち帰る")
    private val skipButton = JButton("スキップ")
    private val endTurnButton = JButton("ターン終了")
    private val newGameButton = JButton("新しいゲーム")
    private val digTileChoiceButtons = DigTileChoice.entries.associateWith { choice ->
        JToggleButton(choice.label())
    }
    private val rotationButtons = Rotation.entries.associateWith { rotation ->
        JToggleButton(rotation.label())
    }

    init {
        defaultCloseOperation = EXIT_ON_CLOSE
        addWindowListener(
            object : WindowAdapter() {
                private var initialPromptShown = false

                override fun windowOpened(event: WindowEvent) {
                    if (!initialPromptShown) {
                        initialPromptShown = true
                        promptNewGame()
                    }
                }

                override fun windowClosing(event: WindowEvent) {
                    backgroundMusic.close()
                }
            },
        )
        minimumSize = Dimension(1000, 740)
        iconImage = assets.load("assets/images/ui/app_icon.png")

        val root = contentPane as JPanel
        root.layout = BorderLayout(10, 8)
        root.preferredSize = Dimension(1220, 960)
        root.border = BorderFactory.createEmptyBorder(8, 10, 8, 10)
        root.background = Color(0xFFF7E4)

        root.add(createTopPanel(), BorderLayout.NORTH)
        root.add(createPlayAreaPanel(), BorderLayout.CENTER)
        root.add(createLogPanel(), BorderLayout.SOUTH)

        newGameButton.addActionListener { promptNewGame() }
        digGuideButton.addActionListener { showCurrentPhaseHelp() }
        confirmDigButton.addActionListener { runAction { controller.confirmPendingDig() } }
        moveGuideButton.addActionListener { showCurrentPhaseHelp() }
        captureButton.addActionListener { runAction { captureSelectedOrPromptImmediately() } }
        robButton.addActionListener { runAction { robSelectedOrPrompt() } }
        eatButton.addActionListener { runAction { controller.eatPendingFood() } }
        carryButton.addActionListener { runAction { controller.carryPendingFood() } }
        skipButton.addActionListener { runAction { controller.skipPhase() } }
        endTurnButton.addActionListener { runAction { controller.finishTurn() } }

        rotationButtons[Rotation.DEG_0]?.isSelected = true

        pack()
        setLocationRelativeTo(null)

        Timer(150) {
            refresh()
        }.start()

    }

    private fun createTopPanel(): JPanel {
        val top = JPanel(BorderLayout(10, 0))
        top.preferredSize = Dimension(0, 52)
        top.background = Color(0xFFF7E4)

        val title = JLabel(APP_TITLE)
        title.font = title.font.deriveFont(Font.BOLD, 30f)
        title.foreground = Color(0x2E2115)

        newGameButton.preferredSize = Dimension(180, 44)
        newGameButton.font = newGameButton.font.deriveFont(Font.BOLD, 18f)
        newGameButton.background = Color(0xFFFDF6)
        newGameButton.border = BorderFactory.createLineBorder(Color(0x9A7A52), 2)

        top.add(title, BorderLayout.WEST)
        top.add(newGameButton, BorderLayout.EAST)

        return top
    }

    private fun createPlayAreaPanel(): JPanel {
        val panel = JPanel(BorderLayout(10, 0))
        panel.background = Color(0xFFF7E4)
        panel.add(boardPanel, BorderLayout.CENTER)
        panel.add(createSideScrollPane(), BorderLayout.EAST)
        return panel
    }

    private fun createSideScrollPane(): JScrollPane {
        val scroll = JScrollPane(createSidePanel())
        scroll.preferredSize = Dimension(370, 0)
        scroll.minimumSize = Dimension(330, 0)
        scroll.border = null
        scroll.horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
        scroll.verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
        scroll.viewport.background = Color(0xFFF7E4)
        return scroll
    }

    private fun createSidePanel(): JPanel {
        val side = JPanel()
        side.layout = BoxLayout(side, BoxLayout.Y_AXIS)
        side.background = Color(0xFFF7E4)

        currentPlayerPanel.alignmentX = Component.LEFT_ALIGNMENT
        side.add(currentPlayerPanel)
        side.add(Box.createVerticalStrut(6))

        val statusPanel = createStatusPanel()
        statusPanel.alignmentX = Component.LEFT_ALIGNMENT
        side.add(statusPanel)
        side.add(Box.createVerticalStrut(6))

        deckSummaryPanel.alignmentX = Component.LEFT_ALIGNMENT
        side.add(deckSummaryPanel)
        side.add(Box.createVerticalStrut(6))

        val digPanel = createDigChoicePanel()
        digPanel.alignmentX = Component.LEFT_ALIGNMENT
        side.add(digPanel)
        side.add(Box.createVerticalStrut(6))

        val dicePanel = createDicePanel()
        dicePanel.alignmentX = Component.LEFT_ALIGNMENT
        side.add(dicePanel)
        side.add(Box.createVerticalStrut(6))

        val actionPanel = createActionPanel()
        actionPanel.alignmentX = Component.LEFT_ALIGNMENT
        side.add(actionPanel)

        return side
    }

    private fun createStatusPanel(): JPanel {
        val statusPanel = JPanel(BorderLayout(4, 4))
        statusPanel.preferredSize = Dimension(0, 62)
        statusPanel.maximumSize = Dimension(Short.MAX_VALUE.toInt(), 62)
        statusPanel.background = Color(0xFFF8E8)
        statusPanel.border = panelBorder()
        statusPanel.add(sectionLabel("操作ヒント"), BorderLayout.NORTH)
        statusLabel.font = statusLabel.font.deriveFont(Font.BOLD, 12f)
        statusLabel.verticalAlignment = SwingConstants.TOP
        statusPanel.add(statusLabel, BorderLayout.CENTER)
        return statusPanel
    }

    private fun createDicePanel(): JPanel {
        val panel = JPanel(BorderLayout(4, 4))
        panel.preferredSize = Dimension(0, 74)
        panel.maximumSize = Dimension(Short.MAX_VALUE.toInt(), 74)
        panel.background = Color(0xFFF8E8)
        panel.border = panelBorder()
        panel.add(sectionLabel("直近のダイス"), BorderLayout.WEST)
        diceLabel.font = diceLabel.font.deriveFont(Font.BOLD, 14f)
        diceLabel.background = Color(0xFFF8E8)
        diceLabel.isOpaque = true
        panel.add(diceLabel, BorderLayout.CENTER)
        return panel
    }

    private fun createDigChoicePanel(): JPanel {
        val panel = JPanel(BorderLayout(4, 4))
        panel.preferredSize = Dimension(0, 132)
        panel.maximumSize = Dimension(Short.MAX_VALUE.toInt(), 132)
        panel.background = Color(0xFFF8E8)
        panel.border = panelBorder()

        val choices = JPanel(GridLayout(1, 2, 8, 0))
        choices.background = Color(0xFFF8E8)
        val digTileChoiceGroup = ButtonGroup()
        digTileChoiceButtons.forEach { (choice, button) ->
            styleChoiceButton(button)
            digTileChoiceGroup.add(button)
            choices.add(button)
            button.addActionListener {
                if (controller.pendingDigPlacement != null) {
                    runAction { controller.selectPendingDigTile(choice) }
                }
            }
        }

        val rotationPanel = JPanel(FlowLayout(FlowLayout.CENTER, 4, 0))
        rotationPanel.background = Color(0xFFF8E8)
        val rotationGroup = ButtonGroup()
        rotationButtons.forEach { (rotation, button) ->
            button.font = button.font.deriveFont(Font.BOLD, 11f)
            button.margin = java.awt.Insets(2, 5, 2, 5)
            button.preferredSize = DESKTOP_ROTATION_BUTTON_SIZE
            button.minimumSize = DESKTOP_ROTATION_BUTTON_SIZE
            button.isFocusPainted = DESKTOP_SHOW_BUTTON_FOCUS
            rotationGroup.add(button)
            rotationPanel.add(button)
            button.addActionListener {
                if (controller.pendingDigPlacement != null) {
                    runAction { controller.setPendingDigRotation(rotation) }
                }
            }
        }

        panel.add(choices, BorderLayout.CENTER)
        panel.add(rotationPanel, BorderLayout.SOUTH)
        return panel
    }

    private fun createActionPanel(): JPanel {
        val actionPanel = JPanel(GridLayout(3, 3, 6, 6))
        actionPanel.preferredSize = Dimension(0, 146)
        actionPanel.maximumSize = Dimension(Short.MAX_VALUE.toInt(), 146)
        actionPanel.background = Color(0xFFF7E4)

        styleActionButton(digGuideButton, "ハイライトされた穴タイルをクリック")
        styleActionButton(confirmDigButton, "選択中の穴タイルをこの向きで配置")
        styleActionButton(moveGuideButton, "ハイライトされた移動先マスをクリック")
        styleActionButton(captureButton, "現在地のエサを捕獲")
        styleActionButton(robButton, "相手の巣からエサを強奪")
        styleActionButton(eatButton, "保留中のエサを食べる")
        styleActionButton(carryButton, "保留中のエサを巣へ持ち帰る")
        styleActionButton(skipButton, "可能なフェーズをスキップ")
        styleActionButton(endTurnButton, "現在のターンを終了")

        actionPanel.add(digGuideButton)
        actionPanel.add(confirmDigButton)
        actionPanel.add(moveGuideButton)
        actionPanel.add(captureButton)
        actionPanel.add(robButton)
        actionPanel.add(eatButton)
        actionPanel.add(carryButton)
        actionPanel.add(skipButton)
        actionPanel.add(endTurnButton)
        return actionPanel
    }

    private fun createLogPanel(): JPanel {
        val panel = JPanel(BorderLayout(8, 0))
        panel.preferredSize = Dimension(0, 126)
        panel.maximumSize = Dimension(Short.MAX_VALUE.toInt(), 126)
        panel.background = Color(0xFFF8E8)
        panel.border = panelBorder()

        logArea.isEditable = false
        logArea.font = Font(Font.MONOSPACED, Font.PLAIN, 12)
        logArea.rows = 5
        logArea.lineWrap = true
        logArea.wrapStyleWord = true

        panel.add(sectionLabel("ログ"), BorderLayout.WEST)
        panel.add(wrapScroll(logArea, 116), BorderLayout.CENTER)
        return panel
    }

    private fun sectionLabel(text: String): JLabel {
        val label = JLabel(text)
        label.font = label.font.deriveFont(Font.BOLD, 13f)
        label.alignmentX = Component.LEFT_ALIGNMENT
        return label
    }

    private fun wrapScroll(component: Component, height: Int): JScrollPane {
        val scroll = JScrollPane(component)
        scroll.alignmentX = Component.LEFT_ALIGNMENT
        scroll.preferredSize = Dimension(320, height)
        scroll.maximumSize = Dimension(Short.MAX_VALUE.toInt(), height)
        return scroll
    }

    private fun panelBorder() =
        BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(Color(0xD0AD78), 2),
            BorderFactory.createEmptyBorder(6, 8, 6, 8),
        )

    private fun styleChoiceButton(button: JToggleButton) {
        button.font = button.font.deriveFont(Font.BOLD, 13f)
        button.verticalTextPosition = SwingConstants.BOTTOM
        button.horizontalTextPosition = SwingConstants.CENTER
        button.background = Color(0xFFFCF2)
        button.isOpaque = true
        button.isFocusPainted = DESKTOP_SHOW_BUTTON_FOCUS
    }

    private fun styleActionButton(button: JButton, tooltip: String) {
        button.icon = null
        button.toolTipText = tooltip
        button.font = button.font.deriveFont(Font.BOLD, 15f)
        button.margin = java.awt.Insets(2, 2, 2, 2)
        button.background = Color(0xFFF1D4)
        button.border = BorderFactory.createLineBorder(Color(0xA88455), 2)
        button.isOpaque = true
        button.isFocusPainted = DESKTOP_SHOW_BUTTON_FOCUS
    }

    private fun updateActionButtonStyle(button: JButton, active: Boolean, accent: Color = Color(0x35BC67)) {
        button.background = if (active) accent else Color(0xFFF1D4)
        button.foreground = if (active) Color(0x102F1B) else Color(0x2E2115)
        button.border = BorderFactory.createLineBorder(
            if (active) accent.darker() else Color(0xA88455),
            if (active) 3 else 2,
        )
    }

    private fun tileIcon(shape: TileShape?, size: Int): ImageIcon? =
        shape?.let { assets.tileImage(it) }
            ?.let { ImageIcon(it.getScaledInstance(size, size, Image.SCALE_SMOOTH)) }

    private fun promptNewGame() {
        val choices = arrayOf("2", "3", "4")
        val choice = JOptionPane.showInputDialog(
            this,
            "プレイヤー人数を選んでください",
            "新しいゲーム",
            JOptionPane.QUESTION_MESSAGE,
            null,
            choices,
            choices.first(),
        ) as? String ?: choices.first()
        val playerCount = choice.toInt()
        val remainingMoles = MoguraGameController.moleOptions.toMutableList()
        val remainingNests = MoguraGameController.nestPositions.toMutableList()
        val configs = mutableListOf<PlayerConfig>()

        repeat(playerCount) { index ->
            val moleLabels = remainingMoles.map { it.name }.toTypedArray()
            val moleChoice = JOptionPane.showInputDialog(
                this,
                "P${index + 1} のモグラを選んでください",
                "モグラ選択",
                JOptionPane.QUESTION_MESSAGE,
                null,
                moleLabels,
                moleLabels.first(),
            ) as? String ?: return
            val mole = remainingMoles.removeAt(moleLabels.indexOf(moleChoice).coerceAtLeast(0))

            val nestLabels = remainingNests.map(::nestChoiceLabel).toTypedArray()
            val nestChoice = JOptionPane.showInputDialog(
                this,
                "P${index + 1} の巣を選んでください",
                "巣選択",
                JOptionPane.QUESTION_MESSAGE,
                null,
                nestLabels,
                nestLabels.first(),
            ) as? String ?: return
            val nest = remainingNests.removeAt(nestLabels.indexOf(nestChoice).coerceAtLeast(0))
            configs.add(PlayerConfig(mole.name, nest, playerId = mole.playerId))
        }

        val startLabels = configs.mapIndexed { index, config ->
            "P${index + 1}: ${config.name}"
        }.toTypedArray()
        val startChoice = JOptionPane.showInputDialog(
            this,
            "先手プレイヤーを選んでください",
            "先手選択",
            JOptionPane.QUESTION_MESSAGE,
            null,
            startLabels,
            startLabels.first(),
        ) as? String ?: startLabels.first()
        val startPlayerIndex = startLabels.indexOf(startChoice).takeIf { it >= 0 } ?: 0

        controller.startNewGame(configs, startPlayerIndex)
        backgroundMusic.playLooping()
        refresh()
    }

    private fun handleBoardClick(position: Position) {
        val current = controller.engine ?: return
        val result = when (current.currentPhase) {
            TurnPhase.DIG -> controller.digAt(position, selectedRotation())
            TurnPhase.MOVE -> controller.moveTo(position)
            TurnPhase.CAPTURE -> {
                val player = controller.currentPlayer
                if (player?.position == position) {
                    captureSelectedOrPromptImmediately()
                } else {
                    GameActionResult(false, "捕獲するには現在のプレイヤーがいるマスをクリックしてください。")
                }
            }

            TurnPhase.DECIDE,
            TurnPhase.END,
            -> GameActionResult(false, "ターンを終えるには右側の操作ボタンを使ってください。")
        }

        handleActionResult(result)
    }

    private fun selectedRotation(): Rotation =
        rotationButtons.entries.firstOrNull { it.value.isSelected }?.key ?: Rotation.DEG_0

    private fun captureSelectedOrPromptImmediately(): GameActionResult {
        val targets = controller.playScreenUiState().captureTargets
        if (targets.size > 1) {
            val labels = targets.map { target ->
                "${target.index + 1}: ${if (target.isFaceDown) "?" else target.type.displayName()}"
            }.toTypedArray()
            val defaultLabel = targets.firstOrNull { it.selected }
                ?.let { labels.getOrNull(it.index) }
                ?: labels.first()
            val choice = JOptionPane.showInputDialog(
                this,
                "捕獲するエサを選んでください",
                "捕獲対象",
                JOptionPane.QUESTION_MESSAGE,
                null,
                labels,
                defaultLabel,
            ) as? String ?: return GameActionResult(false, "捕獲対象を選んでください。")
            val selectedIndex = labels.indexOf(choice)
            val selected = controller.selectCaptureTarget(selectedIndex)
            if (!selected.success) return selected
        }
        return controller.captureCurrentPositionImmediately()
    }

    private fun robSelectedOrPrompt(): GameActionResult {
        val targets = controller.playScreenUiState().robberyTargets
        if (targets.size > 1) {
            val labels = targets.map { target ->
                "${target.index + 1}: ${target.ownerName} の ${target.type.displayName()}"
            }.toTypedArray()
            val defaultLabel = targets.firstOrNull { it.selected }
                ?.let { labels.getOrNull(it.index) }
                ?: labels.first()
            val choice = JOptionPane.showInputDialog(
                this,
                "強奪するエサを選んでください",
                "強奪対象",
                JOptionPane.QUESTION_MESSAGE,
                null,
                labels,
                defaultLabel,
            ) as? String ?: return GameActionResult(false, "強奪対象を選んでください。")
            val selectedIndex = labels.indexOf(choice)
            val selected = controller.selectRobberyTarget(selectedIndex)
            if (!selected.success) return selected
        }
        return controller.robSelectedFood()
    }

    private fun runAction(action: () -> GameActionResult) {
        handleActionResult(action())
    }

    private fun handleActionResult(result: GameActionResult) {
        if (result.success) {
            controller.autoAdvanceWhileNoChoice()
        } else {
            Toolkit.getDefaultToolkit().beep()
            showStatus(result.message)
        }
        refresh()
    }

    private fun refresh() {
        val current = controller.engine
        val uiState = controller.playScreenUiState()
        val actions = uiState.actionAvailability
        val canAdvanceFromDig = controller.canAdvanceFromDigWithoutTargets()

        currentPlayerPanel.render(uiState.currentPlayer)
        val statusText = uiState.captureOutcome?.let(::desktopCaptureOutcomeStatus)
            ?: controller.pendingFoodDecision?.let { food ->
                val prefix = if (uiState.pendingDecisionSource == FoodDecisionSource.ROBBERY) "強奪した " else ""
                "${prefix}${food.type.displayName()} を食べるか、巣へ持ち帰るか選んでください。"
            } ?: if (canAdvanceFromDig) {
            "掘れる穴タイルがありません。移動へ進んでください。"
        } else if (actions.canRob) {
            "強奪するエサを選んでください。"
        } else if (controller.pendingDigPlacement != null) {
            val pending = controller.pendingDigPlacement!!
            val selected = controller.pendingDigTileChoice?.label() ?: DigTileChoice.REVEALED.label()
            val drawn = pending.drawnTile?.shape?.displayName() ?: "なし"
            desktopPendingDigStatus(selected, drawn)
        } else {
            phaseHelp(current?.currentPhase)
        }
        showStatus(statusText)

        captureButton.isEnabled = actions.canCapture
        confirmDigButton.isEnabled = canConfirmDesktopDig(
            hasPendingDig = controller.pendingDigPlacement != null,
            phase = actions.activePhase,
        )
        robButton.isEnabled = actions.canRob
        eatButton.isEnabled = actions.canEat
        carryButton.isEnabled = actions.canCarry
        skipButton.isEnabled = actions.canSkip
        skipButton.text = when {
            actions.activePhase == TurnPhase.END -> "ターン終了"
            actions.activePhase == TurnPhase.DIG && actions.canSkip -> "移動へ進む"
            else -> "スキップ"
        }
        endTurnButton.isEnabled = actions.canEndTurn
        refreshDigChoiceButtons(uiState.digCandidates)
        syncRotationButtons(uiState.digCandidates.any { it.enabled }, uiState.selectedRotation)
        refreshActionButtonStyles(actions)

        logArea.text = controller.logs.joinToString("\n")
        logArea.caretPosition = logArea.document.length

        val dice = uiState.lastDiceRoll
        val diceImage = dice?.let { assets.load("assets/images/dice/dice_$it.png") }
        diceLabel.icon = diceImage?.let { ImageIcon(it.getScaledInstance(54, 54, Image.SCALE_SMOOTH)) }
        diceLabel.text = if (diceImage == null) dice?.toString() ?: "ダイス" else ""

        deckSummaryPanel.repaint()
        boardPanel.repaint()
    }

    private fun refreshDigChoiceButtons(candidates: List<DigCandidateDisplay>) {
        val candidatesByChoice = candidates.associateBy { it.choice }
        digTileChoiceButtons.forEach { (choice, button) ->
            val candidate = candidatesByChoice[choice]
            val selected = candidate?.selected == true
            button.text = "<html><center>${candidate?.label ?: choice.label()}<br>${candidate?.shape?.displayName() ?: "-"}</center></html>"
            button.icon = tileIcon(candidate?.shape, 36)
            button.isEnabled = candidate?.enabled == true
            button.isSelected = selected
            button.border = BorderFactory.createLineBorder(
                if (selected) Color(0x158A45) else Color(0xD0AD78),
                if (selected) 3 else 2,
            )
        }
    }

    private fun refreshActionButtonStyles(actions: ActionAvailability) {
        val phase = actions.activePhase
        updateActionButtonStyle(digGuideButton, phase == TurnPhase.DIG)
        updateActionButtonStyle(confirmDigButton, confirmDigButton.isEnabled, Color(0x158A45))
        updateActionButtonStyle(moveGuideButton, phase == TurnPhase.MOVE)
        updateActionButtonStyle(captureButton, phase == TurnPhase.CAPTURE, Color(0xEB5757))
        updateActionButtonStyle(robButton, actions.canRob, Color(0xBB6BD9))
        updateActionButtonStyle(eatButton, actions.canEat, Color(0x6FCF97))
        updateActionButtonStyle(carryButton, actions.canCarry, Color(0x56CCF2))
        updateActionButtonStyle(skipButton, false)
        updateActionButtonStyle(endTurnButton, phase == TurnPhase.END)
    }

    private fun syncRotationButtons(hasPendingDig: Boolean, selectedRotation: Rotation) {
        rotationButtons.forEach { (rotation, button) ->
            button.isEnabled = hasPendingDig
            button.isSelected = rotation == selectedRotation
        }
    }

    private fun showStatus(message: String) {
        statusLabel.text = "<html><body style='width:300px'>${escapeHtml(message)}</body></html>"
    }

    private fun showCurrentPhaseHelp() {
        showStatus(desktopGuideStatusText(controller.playScreenUiState().captureOutcome, controller.engine?.currentPhase))
    }

    private fun escapeHtml(text: String): String =
        text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")

    private fun phaseHelp(phase: TurnPhase?): String = desktopPhaseHelp(phase)
}

internal fun desktopCaptureOutcomeStatus(outcome: CaptureOutcomeDisplay): String {
    val prefix = outcome.diceRoll?.let { "ダイス: $it" } ?: "逃走なし"
    return "$prefix　${outcome.message}"
}

internal fun desktopGuideStatusText(outcome: CaptureOutcomeDisplay?, phase: TurnPhase?): String =
    outcome?.let(::desktopCaptureOutcomeStatus) ?: desktopPhaseHelp(phase)

internal fun canConfirmDesktopDig(hasPendingDig: Boolean, phase: TurnPhase?): Boolean =
    hasPendingDig && phase == TurnPhase.DIG

internal fun desktopPendingDigStatus(selected: String, drawn: String): String =
    "${selected}を選択中。山札: ${drawn}。回転を選び、操作パネルの「$DESKTOP_CONFIRM_DIG_LABEL」で配置してください。"

internal fun desktopPhaseHelp(phase: TurnPhase?): String = when (phase) {
        TurnPhase.DIG -> "ハイライトされた穴タイルをクリックしてください。"
        TurnPhase.MOVE -> "ハイライトされた移動可能マスをクリックしてください。"
        TurnPhase.CAPTURE -> "プレイヤーの足元にエサがあれば捕獲できます。"
        TurnPhase.DECIDE -> "食べる、巣へ持ち帰る、または強奪を選んでください。"
        TurnPhase.END -> "ターンを終了してください。"
        null -> "新しいゲームを開始してください。"
    }

private class DeckSummaryPanel(
    private val controller: MoguraGameController,
    private val assets: GuiAssets,
) : JPanel() {
    init {
        preferredSize = Dimension(0, 154)
        maximumSize = Dimension(Short.MAX_VALUE.toInt(), 154)
        background = Color(0xFFF8E8)
        border = BorderFactory.createLineBorder(Color(0x6E4827), 3)
        isOpaque = true
    }

    override fun paintComponent(graphics: Graphics) {
        super.paintComponent(graphics)
        val g = graphics as Graphics2D
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)

        val summary = controller.playScreenUiState().deckSummary
        val gap = 10
        val groupWidth = width - gap * 2
        val groupHeight = (height - gap * 3) / 2
        drawDeckGroup(
            g = g,
            rect = Rectangle(gap, gap, groupWidth, groupHeight),
            deckLabel = "穴タイル山札",
            discardLabel = "捨て札",
            backImage = assets.load("assets/images/tiles/tile_back.png"),
            deckCount = summary.tileDrawCount,
            discardCount = summary.tileDiscardCount,
        )
        drawDeckGroup(
            g = g,
            rect = Rectangle(gap, gap * 2 + groupHeight, groupWidth, groupHeight),
            deckLabel = "エサ山",
            discardLabel = "捨て札",
            backImage = assets.load("assets/images/foods/food_card_back.png"),
            deckCount = summary.foodDrawCount,
            discardCount = summary.foodDiscardCount,
        )
    }

    private fun drawDeckGroup(
        g: Graphics2D,
        rect: Rectangle,
        deckLabel: String,
        discardLabel: String,
        backImage: BufferedImage?,
        deckCount: Int,
        discardCount: Int,
    ) {
        g.color = Color(0xFFF3D6)
        g.fillRoundRect(rect.x, rect.y, rect.width, rect.height, 10, 10)
        g.color = Color(0xD0AD78)
        g.stroke = BasicStroke(2f)
        g.drawRoundRect(rect.x, rect.y, rect.width, rect.height, 10, 10)

        g.color = Color(0x2E2115)
        g.font = font.deriveFont(Font.BOLD, 13f)
        g.drawString(deckLabel, rect.x + 12, rect.y + 18)
        g.drawString(discardLabel, rect.x + rect.width - 86, rect.y + 18)

        val cardSize = min(38, rect.height - 24)
        val deckX = rect.x + 14
        val deckY = rect.y + 22
        drawStack(g, backImage, deckX, deckY, cardSize, deckCount)

        g.font = font.deriveFont(Font.BOLD, 26f)
        g.drawString(deckCount.toString(), rect.x + 76, rect.y + 52)

        val discardX = rect.x + rect.width - 82
        val discardRect = Rectangle(discardX, deckY + 2, cardSize - 6, cardSize - 6)
        if (discardCount > 0) {
            drawCard(g, backImage, discardRect)
        } else {
            drawEmptyCard(g, discardRect)
        }
        g.font = font.deriveFont(Font.BOLD, 14f)
        val countText = discardCount.toString()
        val badgeSize = 24
        val badgeX = rect.x + rect.width - 34
        val badgeY = rect.y + rect.height - 30
        g.color = Color(0xFFFDF6)
        g.fillOval(badgeX, badgeY, badgeSize, badgeSize)
        g.color = Color(0x6E4827)
        g.drawOval(badgeX, badgeY, badgeSize, badgeSize)
        g.color = Color(0x2E2115)
        g.drawString(
            countText,
            badgeX + (badgeSize - g.fontMetrics.stringWidth(countText)) / 2,
            badgeY + 19,
        )
    }

    private fun drawStack(g: Graphics2D, image: BufferedImage?, x: Int, y: Int, size: Int, count: Int) {
        if (count <= 0) {
            drawEmptyCard(g, Rectangle(x, y, size, size))
            return
        }
        val visibleCards = min(3, count)
        repeat(visibleCards) { index ->
            val offset = (visibleCards - index - 1) * 7
            drawCard(g, image, Rectangle(x + offset, y + offset / 2, size, size))
        }
    }

    private fun drawEmptyCard(g: Graphics2D, rect: Rectangle) {
        g.color = Color(0xFFF8E8)
        g.fillRoundRect(rect.x, rect.y, rect.width, rect.height, 6, 6)
        g.color = Color(0xC7A16D)
        g.stroke = BasicStroke(2f)
        g.drawRoundRect(rect.x, rect.y, rect.width, rect.height, 6, 6)
    }

    private fun drawCard(g: Graphics2D, image: BufferedImage?, rect: Rectangle) {
        g.color = Color(0xF7E3BD)
        g.fillRoundRect(rect.x, rect.y, rect.width, rect.height, 6, 6)
        if (image != null) {
            g.drawImage(image, rect.x, rect.y, rect.width, rect.height, null)
        }
        g.color = Color(0x6F4B25)
        g.stroke = BasicStroke(2f)
        g.drawRoundRect(rect.x, rect.y, rect.width, rect.height, 6, 6)
    }
}

private class CurrentPlayerPanel(
    private val assets: GuiAssets,
) : JPanel(BorderLayout(10, 0)) {
    private val portrait = CurrentPlayerPortrait()
    private val titleLabel = JLabel("-")
    private val phaseLabel = JLabel("フェーズ: -")
    private val statsLabel = JLabel("体力: -  点: -")
    private val carriedFoodLabel = JLabel("所持: -")

    init {
        preferredSize = Dimension(320, CURRENT_PLAYER_PANEL_HEIGHT)
        maximumSize = Dimension(Short.MAX_VALUE.toInt(), CURRENT_PLAYER_PANEL_HEIGHT)
        background = Color(0xFFF7D6)
        border = BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(Color(0xF2C94C), 2),
            BorderFactory.createEmptyBorder(8, 8, 8, 8),
        )
        isOpaque = true

        titleLabel.font = titleLabel.font.deriveFont(Font.BOLD, 18f)
        phaseLabel.font = phaseLabel.font.deriveFont(Font.BOLD, 14f)
        statsLabel.font = statsLabel.font.deriveFont(13f)
        carriedFoodLabel.font = carriedFoodLabel.font.deriveFont(13f)

        val textPanel = JPanel()
        textPanel.layout = BoxLayout(textPanel, BoxLayout.Y_AXIS)
        textPanel.background = background
        textPanel.add(titleLabel)
        textPanel.add(Box.createVerticalStrut(4))
        textPanel.add(phaseLabel)
        textPanel.add(Box.createVerticalStrut(2))
        textPanel.add(statsLabel)
        textPanel.add(Box.createVerticalStrut(2))
        textPanel.add(carriedFoodLabel)

        add(portrait, BorderLayout.WEST)
        add(textPanel, BorderLayout.CENTER)
    }

    fun render(display: CurrentPlayerDisplay) {
        titleLabel.text = display.titleText
        phaseLabel.text = display.phaseText
        statsLabel.text = "${display.healthText}  ${display.scoreText}"
        carriedFoodLabel.text = display.carriedFoodText
        portrait.render(
            image = display.playerId?.let(assets::playerImage),
            fallbackText = display.titleText.take(1).takeUnless { it == "-" } ?: "-",
            hasPlayer = display.playerId != null,
        )
    }
}

private class CurrentPlayerPortrait : JPanel() {
    private var image: BufferedImage? = null
    private var fallbackText: String = "-"
    private var hasPlayer: Boolean = false

    init {
        val size = CURRENT_PLAYER_PORTRAIT_PANEL_SIZE
        preferredSize = Dimension(size, size)
        minimumSize = Dimension(size, size)
        maximumSize = Dimension(size, size)
        isOpaque = false
    }

    fun render(image: BufferedImage?, fallbackText: String, hasPlayer: Boolean) {
        this.image = image
        this.fallbackText = fallbackText
        this.hasPlayer = hasPlayer
        repaint()
    }

    override fun paintComponent(graphics: Graphics) {
        super.paintComponent(graphics)
        val g = graphics as Graphics2D
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)

        val size = currentPlayerPortraitDrawSize(min(width, height))
        val rect = Rectangle((width - size) / 2, (height - size) / 2, size, size)
        val playerImage = image
        if (playerImage != null) {
            val source = visibleImageBounds(playerImage)
            g.drawImage(
                playerImage,
                rect.x,
                rect.y,
                rect.x + rect.width,
                rect.y + rect.height,
                source.x,
                source.y,
                source.x + source.width,
                source.y + source.height,
                null,
            )
        } else {
            g.color = Color(0xF1E1B8)
            g.fillOval(rect.x, rect.y, rect.width, rect.height)
            g.color = Color(0x4D3926)
            g.font = g.font.deriveFont(Font.BOLD, 22f)
            val textWidth = g.fontMetrics.stringWidth(fallbackText)
            val textY = rect.y + (rect.height - g.fontMetrics.height) / 2 + g.fontMetrics.ascent
            g.drawString(fallbackText, rect.x + (rect.width - textWidth) / 2, textY)
        }

        g.color = if (hasPlayer) Color(0xFFE66D) else Color(0x9A8C7A)
        g.stroke = BasicStroke(if (hasPlayer) 4f else 2f)
        g.drawOval(rect.x, rect.y, rect.width, rect.height)
    }
}

class BoardPanel(
    private val controller: MoguraGameController,
    private val assets: GuiAssets,
    private val onCellClicked: (Position) -> Unit,
) : JPanel() {
    private var hoveredFoodPosition: Position? = null

    init {
        preferredSize = Dimension(860, 820)
        minimumSize = Dimension(620, 700)
        background = Color(0xD9C7A8)

        addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(event: MouseEvent) {
                imagePoint(event.point)?.let { point ->
                    positionAt(point)?.let(onCellClicked)
                }
            }
        })
        addMouseMotionListener(object : MouseAdapter() {
            override fun mouseMoved(event: MouseEvent) {
                updateHoveredFoodPosition(event.point)
            }
        })
        addMouseListener(object : MouseAdapter() {
            override fun mouseExited(event: MouseEvent) {
                updateHoveredFoodPosition(null)
            }
        })
    }

    override fun paintComponent(graphics: Graphics) {
        super.paintComponent(graphics)
        val g = graphics as Graphics2D
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)

        val rect = imageRect()
        assets.load("assets/images/board/board_main.png")?.let {
            g.drawImage(it, rect.x, rect.y, rect.width, rect.height, null)
        } ?: run {
            g.color = Color(0xC69A63)
            g.fillRoundRect(rect.x, rect.y, rect.width, rect.height, 20, 20)
        }

        val current = controller.engine ?: return
        drawBoardHungerMeters(g, current.players, controller.currentPlayer)
        val highlights = highlights()

        for (row in 0 until Board.ROWS) {
            for (col in 0 until Board.COLS) {
                val position = Position(col, row)
                val cell = current.board.getCell(position) ?: continue
                if (cell.type == CellType.INVALID) continue

                val cellRect = cellRect(position) ?: continue
                current.boardState.getTile(position)?.let { tile ->
                    drawTile(g, tile, cellRect)
                }
            }
        }

        drawBoardLayers(g, highlights)
    }

    private fun drawBoardLayers(g: Graphics2D, highlights: Set<Position>) {
        boardPaintRenderPlan().forEach { layer ->
            when (layer) {
                BoardPaintLayer.HIGHLIGHT -> drawHighlights(g, highlights)
                BoardPaintLayer.DIG_DIRECTION -> drawDigDirectionArrows(g, highlights)
                BoardPaintLayer.FOOD -> drawFoods(g)
                BoardPaintLayer.PLAYERS -> drawPlayers(g)
                BoardPaintLayer.CURRENT_PLAYER_OUTLINE -> drawCurrentPlayerOutline(g)
                BoardPaintLayer.HOVER_PREVIEW -> drawHoveredFoodPreview(g)
            }
        }
    }

    private fun drawHighlights(g: Graphics2D, highlights: Set<Position>) {
        val current = controller.engine ?: return
        highlights.forEach { position ->
            val cellRect = cellRect(position) ?: return@forEach
            drawHighlight(g, cellRect, current.currentPhase)
        }
    }

    private fun drawBoardHungerMeters(g: Graphics2D, players: List<Player>, currentPlayer: Player?) {
        if (players.isEmpty()) return
        val meterRect = boardHungerMeterRect(imageRect())
        val meterImage = assets.hungerMeterImage()
        if (meterImage != null) {
            g.drawImage(meterImage, meterRect.x, meterRect.y, meterRect.width, meterRect.height, null)
        } else {
            drawPlaceholder(g, meterRect, "腹減り", Color(0xF3D48C))
        }

        val orderedPlayers = if (currentPlayer != null && currentPlayer in players) {
            players.filter { it != currentPlayer } + currentPlayer
        } else {
            players
        }
        val markerSize = (meterRect.height * HUNGER_MARKER_SCALE).roundToInt()
        val centers = hungerMeterMarkerCenters(
            healths = orderedPlayers.map { it.health },
            maxHealth = Player.MAX_HEALTH,
            rect = meterRect,
        )

        val markerRects = hungerMeterMarkerRects(centers, markerSize, meterRect)

        orderedPlayers.zip(markerRects).forEach { (player, markerRect) ->
            drawHungerMarker(g, player, markerRect, player == currentPlayer)
        }
    }

    private fun drawHungerMarker(
        g: Graphics2D,
        player: Player,
        markerRect: Rectangle,
        isCurrent: Boolean,
    ) {
        val oldStroke = g.stroke
        val playerImage = assets.playerImage(player.id)
        if (playerImage != null) {
            g.drawImage(playerImage, markerRect.x, markerRect.y, markerRect.width, markerRect.height, null)
        } else {
            drawPlaceholder(g, markerRect, player.name.take(1), playerColor(player.id))
        }

        g.color = if (isCurrent) Color(0xFFE66D) else playerColor(player.id)
        g.stroke = BasicStroke(if (isCurrent) 4f else 2.5f)
        g.drawOval(markerRect.x, markerRect.y, markerRect.width, markerRect.height)
        g.stroke = oldStroke
    }

    private fun highlights(): Set<Position> {
        val current = controller.engine ?: return emptySet()
        return when (current.currentPhase) {
            TurnPhase.DIG -> controller.pendingDigPlacement?.let { setOf(it.position) }
                ?: controller.digTargets().toSet()
            TurnPhase.MOVE -> controller.moveTargets()
            TurnPhase.CAPTURE -> if (controller.canCapture()) {
                setOfNotNull(controller.currentPlayer?.position)
            } else {
                emptySet()
            }

            TurnPhase.DECIDE,
            TurnPhase.END,
            -> emptySet()
        }
    }

    private fun drawTile(g: Graphics2D, tile: HoleTile, rect: Rectangle) {
        val image = if (tile.isFaceDown) {
            assets.load("assets/images/tiles/tile_back.png")
        } else {
            assets.tileImage(tile.shape)
        }

        if (image == null) {
            drawPlaceholder(g, rect, tile.shape.displayName(), Color(0xB8864F))
            return
        }

        val padding = (min(rect.width, rect.height) * 0.08).roundToInt()
        val drawRect = Rectangle(
            rect.x + padding,
            rect.y + padding,
            rect.width - padding * 2,
            rect.height - padding * 2,
        )
        val rotation = if (tile.isFaceDown) Rotation.DEG_0 else rotationFor(tile)
        drawRotatedImage(g, image, drawRect, rotation)
    }

    private fun drawPlayers(g: Graphics2D) {
        val current = controller.engine ?: return
        current.players
            .filter { !it.isEliminated }
            .groupBy { it.position }
            .forEach { (position, players) ->
                val rect = cellRect(position) ?: return@forEach
                players.zip(playerTokenRects(rect, players.size)).forEach { (player, tokenRect) ->
                    val image = assets.playerImage(player.id)
                    if (image != null) {
                        drawImage(g, image, tokenRect, assets.visibleBounds(image))
                    } else {
                        drawPlaceholder(g, tokenRect, player.name.take(1), playerColor(player.id))
                    }
                    drawPlayerNameBadge(g, tokenRect, player.name)
                }
            }
    }

    private fun drawCurrentPlayerOutline(g: Graphics2D) {
        val current = controller.engine ?: return
        val currentPlayer = controller.currentPlayer?.takeUnless { it.isEliminated } ?: return
        val rect = cellRect(currentPlayer.position) ?: return
        val players = current.players
            .filter { !it.isEliminated && it.position == currentPlayer.position }
        val tokenRect = players
            .zip(playerTokenRects(rect, players.size))
            .firstOrNull { (player, _) -> player == currentPlayer }
            ?.second
            ?: return

        g.color = Color(0xFFE66D)
        g.stroke = BasicStroke(4f)
        g.drawOval(tokenRect.x, tokenRect.y, tokenRect.width, tokenRect.height)
    }

    private fun drawFoods(g: Graphics2D) {
        val current = controller.engine ?: return
        current.foodPositions.forEach { (position, foods) ->
            val cellRect = cellRect(position) ?: return@forEach
            foods.asReversed().forEachIndexed { reversedIndex, food ->
                val stackIndex = foods.lastIndex - reversedIndex
                drawFood(g, food, cellRect, foodCardScaleForPhase(current.currentPhase), stackIndex, foods.size)
            }
        }
    }

    private fun drawFood(
        g: Graphics2D,
        food: FoodCard,
        cellRect: Rectangle,
        scale: Double,
        stackIndex: Int,
        stackSize: Int,
    ) {
        val image = if (food.isFaceDown) {
            assets.load("assets/images/foods/food_card_back.png")
        } else {
            assets.foodImage(food.type)
        }
        drawSmallImage(
            g = g,
            image = image,
            rect = cellRect,
            anchor = Anchor.BOTTOM_RIGHT,
            scale = scale,
            stackIndex = stackIndex,
            stackSize = stackSize,
        )
    }

    private fun drawSmallImage(
        g: Graphics2D,
        image: BufferedImage?,
        rect: Rectangle,
        anchor: Anchor,
        scale: Double,
        stackIndex: Int = 0,
        stackSize: Int = 1,
    ) {
        val smallRect = when (anchor) {
            Anchor.BOTTOM_RIGHT -> foodCardRect(rect, scale, stackIndex = stackIndex, stackSize = stackSize)
        }
        if (image != null) {
            g.drawImage(image, smallRect.x, smallRect.y, smallRect.width, smallRect.height, null)
        } else {
            drawPlaceholder(g, smallRect, "エサ", Color(0x8FBC8F))
        }
    }

    private fun drawHoveredFoodPreview(g: Graphics2D) {
        val current = controller.engine ?: return
        val position = hoveredFoodPosition ?: return
        val food = current.foodAt(position) ?: return
        val cellRect = cellRect(position) ?: return
        val image = if (food.isFaceDown) {
            assets.load("assets/images/foods/food_card_back.png")
        } else {
            assets.foodImage(food.type)
        }
        val previewRect = foodPreviewRect(cellRect, imageRect())
        g.color = Color(0x2F251B)
        g.stroke = BasicStroke(3f)
        g.drawRoundRect(previewRect.x - 3, previewRect.y - 3, previewRect.width + 6, previewRect.height + 6, 8, 8)
        if (image != null) {
            g.drawImage(image, previewRect.x, previewRect.y, previewRect.width, previewRect.height, null)
        } else {
            drawPlaceholder(g, previewRect, "エサ", Color(0x8FBC8F))
        }
    }

    private fun drawHighlight(g: Graphics2D, rect: Rectangle, phase: TurnPhase) {
        val color = when (phase) {
            TurnPhase.DIG -> Color(0xF2C94C)
            TurnPhase.MOVE -> Color(0x6FCF97)
            TurnPhase.CAPTURE -> Color(0xEB5757)
            TurnPhase.DECIDE,
            TurnPhase.END,
            -> Color(0x56CCF2)
        }
        g.color = Color(color.red, color.green, color.blue, BOARD_HIGHLIGHT_FILL_ALPHA)
        g.fillRect(rect.x + 4, rect.y + 4, rect.width - 8, rect.height - 8)
        g.color = color
        g.stroke = BasicStroke(4f)
        g.drawRect(rect.x + 4, rect.y + 4, rect.width - 8, rect.height - 8)
    }

    private fun drawDigDirectionArrows(g: Graphics2D, highlights: Set<Position>) {
        val current = controller.engine ?: return
        if (current.currentPhase != TurnPhase.DIG || controller.pendingDigPlacement != null) return
        val player = controller.currentPlayer ?: return
        if (highlights.isEmpty()) return

        val fromRect = cellRect(player.position) ?: return
        highlights.forEach { target ->
            val toRect = cellRect(target) ?: return@forEach
            drawArrow(g, fromRect, toRect)
        }
    }

    private fun drawArrow(g: Graphics2D, fromRect: Rectangle, toRect: Rectangle) {
        val startX = fromRect.centerX.toInt()
        val startY = fromRect.centerY.toInt()
        val endX = toRect.centerX.toInt()
        val endY = toRect.centerY.toInt()
        val dx = endX - startX
        val dy = endY - startY
        val length = kotlin.math.hypot(dx.toDouble(), dy.toDouble())
        if (length == 0.0) return

        val unitX = dx / length
        val unitY = dy / length
        val inset = min(fromRect.width, fromRect.height) * 0.28
        val arrowStartX = (startX + unitX * inset).roundToInt()
        val arrowStartY = (startY + unitY * inset).roundToInt()
        val arrowEndX = (endX - unitX * inset).roundToInt()
        val arrowEndY = (endY - unitY * inset).roundToInt()

        drawArrowLine(g, arrowStartX, arrowStartY, arrowEndX, arrowEndY, Color(0x3A2A12), 9f, 24.0)
        drawArrowLine(g, arrowStartX, arrowStartY, arrowEndX, arrowEndY, Color(0xFFD54F), 5f, 18.0)
    }

    private fun drawArrowLine(
        g: Graphics2D,
        startX: Int,
        startY: Int,
        endX: Int,
        endY: Int,
        color: Color,
        strokeWidth: Float,
        headLength: Double,
    ) {
        val oldStroke = g.stroke
        g.color = color
        g.stroke = BasicStroke(strokeWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
        g.drawLine(startX, startY, endX, endY)

        val angle = kotlin.math.atan2((endY - startY).toDouble(), (endX - startX).toDouble())
        val baseX = endX - headLength * kotlin.math.cos(angle)
        val baseY = endY - headLength * kotlin.math.sin(angle)
        val headWidth = headLength * 0.7
        val leftX = (baseX + headWidth * kotlin.math.cos(angle + Math.PI / 2.0)).roundToInt()
        val leftY = (baseY + headWidth * kotlin.math.sin(angle + Math.PI / 2.0)).roundToInt()
        val rightX = (baseX + headWidth * kotlin.math.cos(angle - Math.PI / 2.0)).roundToInt()
        val rightY = (baseY + headWidth * kotlin.math.sin(angle - Math.PI / 2.0)).roundToInt()
        g.fillPolygon(
            java.awt.Polygon(
                intArrayOf(endX, leftX, rightX),
                intArrayOf(endY, leftY, rightY),
                3,
            ),
        )
        g.stroke = oldStroke
    }

    private fun imagePoint(point: Point): Point? {
        val rect = imageRect()
        if (!rect.contains(point)) return null
        val sourceX = ((point.x - rect.x).toDouble() / rect.width * SOURCE_WIDTH).roundToInt()
        val sourceY = ((point.y - rect.y).toDouble() / rect.height * SOURCE_HEIGHT).roundToInt()
        return Point(sourceX, sourceY)
    }

    private fun updateHoveredFoodPosition(point: Point?) {
        val current = controller.engine
        val next = if (current == null || point == null) {
            null
        } else {
            current.foodPositions.keys.firstOrNull { position ->
                val foods = current.foodsAt(position)
                val cellRect = cellRect(position) ?: return@firstOrNull false
                foods.indices.any { index ->
                    val cardRect = foodCardRect(
                        cellRect = cellRect,
                        scale = foodCardScaleForPhase(current.currentPhase),
                        stackIndex = index,
                        stackSize = foods.size,
                    )
                    cardRect.contains(point)
                }
            }
        }
        if (hoveredFoodPosition != next) {
            hoveredFoodPosition = next
            repaint()
        }
    }

    private fun positionAt(point: Point): Position? {
        if (point.x < GRID_LEFT || point.x > GRID_RIGHT || point.y < GRID_TOP || point.y > GRID_BOTTOM) {
            return null
        }
        val col = floor((point.x - GRID_LEFT).toDouble() / CELL_WIDTH).toInt()
        val row = floor((point.y - GRID_TOP).toDouble() / CELL_HEIGHT).toInt()
        val position = Position(col, row)
        val cell = controller.engine?.board?.getCell(position) ?: return null
        return if (cell.type == CellType.INVALID) null else position
    }

    private fun cellRect(position: Position): Rectangle? {
        val current = controller.engine
        val cell = current?.board?.getCell(position) ?: return null
        if (cell.type == CellType.INVALID) return null

        val imageRect = imageRect()
        val x = imageRect.x + ((GRID_LEFT + position.col * CELL_WIDTH) / SOURCE_WIDTH * imageRect.width).roundToInt()
        val y = imageRect.y + ((GRID_TOP + position.row * CELL_HEIGHT) / SOURCE_HEIGHT * imageRect.height).roundToInt()
        val width = (CELL_WIDTH / SOURCE_WIDTH * imageRect.width).roundToInt()
        val height = (CELL_HEIGHT / SOURCE_HEIGHT * imageRect.height).roundToInt()
        return Rectangle(x, y, width, height)
    }

    private fun imageRect(): Rectangle {
        val scale = min(width.toDouble() / SOURCE_WIDTH, height.toDouble() / SOURCE_HEIGHT)
        val drawWidth = (SOURCE_WIDTH * scale).roundToInt()
        val drawHeight = (SOURCE_HEIGHT * scale).roundToInt()
        return Rectangle((width - drawWidth) / 2, (height - drawHeight) / 2, drawWidth, drawHeight)
    }

    companion object {
        private const val SOURCE_WIDTH = 1086.0
        private const val SOURCE_HEIGHT = 1448.0
        private const val GRID_LEFT = 48.0
        private const val GRID_TOP = 488.0
        private const val GRID_RIGHT = 1038.0
        private const val GRID_BOTTOM = 1364.0
        private const val CELL_WIDTH = (GRID_RIGHT - GRID_LEFT) / Board.COLS
        private const val CELL_HEIGHT = (GRID_BOTTOM - GRID_TOP) / Board.ROWS
        private const val HUNGER_MARKER_SCALE = 0.42
    }
}

fun playerTokenRects(cellRect: Rectangle, playerCount: Int): List<Rectangle> {
    require(playerCount >= 0) { "playerCount must not be negative" }
    require(playerCount <= PLAYER_TOKEN_MAX_STACKED) {
        "playerCount must not exceed $PLAYER_TOKEN_MAX_STACKED"
    }

    val base = min(cellRect.width, cellRect.height)
    val centerX = cellRect.centerX
    val centerY = cellRect.centerY
    val stackOffset = (base * PLAYER_TOKEN_STACK_OFFSET_RATIO).roundToInt()
        .coerceAtLeast(PLAYER_TOKEN_MIN_STACK_OFFSET)
    val offsets = playerTokenOffsets(playerCount, stackOffset)
    val maxOffsetX = offsets.maxOfOrNull { kotlin.math.abs(it.x) } ?: 0
    val maxOffsetY = offsets.maxOfOrNull { kotlin.math.abs(it.y) } ?: 0
    val size = (base * PLAYER_TOKEN_SCALE).roundToInt()
        .coerceAtMost(cellRect.width - maxOffsetX * 2)
        .coerceAtMost(cellRect.height - maxOffsetY * 2)
    return offsets.map { offset ->
        Rectangle(
            (centerX - size / 2.0 + offset.x).roundToInt(),
            (centerY - size / 2.0 + offset.y).roundToInt(),
            size,
            size,
        )
    }
}

private fun playerTokenOffsets(playerCount: Int, stackOffset: Int): List<Point> =
    when (playerCount) {
        0 -> emptyList()
        1 -> listOf(Point(0, 0))
        2 -> listOf(Point(-stackOffset, -stackOffset), Point(stackOffset, stackOffset))
        3 -> listOf(Point(-stackOffset, -stackOffset), Point(stackOffset, -stackOffset), Point(0, stackOffset))
        else -> listOf(
            Point(-stackOffset, -stackOffset),
            Point(stackOffset, -stackOffset),
            Point(-stackOffset, stackOffset),
            Point(stackOffset, stackOffset),
        ).take(playerCount)
    }

internal fun foodImagePath(type: FoodType): String = when (type) {
    FoodType.BEETLE_LARVA -> "assets/images/foods/food_beetle_larva.jpg"
    FoodType.EARTHWORM -> "assets/images/foods/food_earthworm.jpg"
    FoodType.MOLE_CRICKET -> "assets/images/foods/food_mole_cricket.jpg"
    FoodType.CENTIPEDE -> "assets/images/foods/food_centipede.jpg"
    FoodType.FROG -> "assets/images/foods/food_frog.jpg"
}

class GuiAssets {
    private val cache = mutableMapOf<String, BufferedImage?>()
    private val visibleBoundsCache = mutableMapOf<BufferedImage, Rectangle>()

    fun load(path: String): BufferedImage? =
        cache.getOrPut(path) {
            val file = File(path)
            if (file.exists()) {
                ImageIO.read(file)
            } else {
                val resourcePath = path.removePrefix("assets/").replace('\\', '/')
                Thread.currentThread().contextClassLoader.getResourceAsStream(resourcePath)?.use(ImageIO::read)
            }
        }

    fun tileImage(shape: TileShape): BufferedImage? = load(
        when (shape) {
            TileShape.STRAIGHT -> "assets/images/tiles/tile_straight.png"
            TileShape.L_SHAPE -> "assets/images/tiles/tile_l_shape.png"
            TileShape.T_SHAPE -> "assets/images/tiles/tile_t_shape.png"
            TileShape.CROSS -> "assets/images/tiles/tile_cross.png"
        },
    )

    fun foodImage(type: FoodType): BufferedImage? = load(foodImagePath(type))

    fun playerImage(id: Int): BufferedImage? = load(
        when (id) {
            0 -> "assets/images/players/player_moguo_blue.png"
            1 -> "assets/images/players/player_moguta_orange.png"
            2 -> "assets/images/players/player_mogumi_pink.png"
            else -> "assets/images/players/player_moguka_yellow.png"
        },
    )

    fun hungerMeterImage(): BufferedImage? =
        cache.getOrPut("transparent:assets/images/ui/hunger_meter_reference.jpg") {
            load("assets/images/ui/hunger_meter_reference.jpg")?.let(::makeWhiteTransparent)
        }

    fun visibleBounds(image: BufferedImage): Rectangle =
        visibleBoundsCache.getOrPut(image) { visibleImageBounds(image) }
}

private const val PLAYER_TOKEN_SCALE = 0.95
private const val PLAYER_TOKEN_STACK_OFFSET_RATIO = 0.10
private const val PLAYER_TOKEN_MIN_STACK_OFFSET = 8
private const val PLAYER_TOKEN_MAX_STACKED = 4
private const val BACKGROUND_MUSIC_PATH = "assets/audio/burrowed_logic.mp3"

private enum class Anchor {
    BOTTOM_RIGHT,
}

private fun rotationFor(tile: HoleTile): Rotation =
    Rotation.entries.firstOrNull { rotation ->
        HoleTile(tile.shape).rotate(rotation).openSides == tile.openSides
    } ?: Rotation.DEG_0

private fun drawRotatedImage(
    g: Graphics2D,
    image: BufferedImage,
    rect: Rectangle,
    rotation: Rotation,
) {
    val oldTransform = g.transform
    val centerX = rect.centerX
    val centerY = rect.centerY
    g.rotate(Math.toRadians(rotation.steps * 90.0), centerX, centerY)
    g.drawImage(image, rect.x, rect.y, rect.width, rect.height, null)
    g.transform = oldTransform
}

private fun drawImage(g: Graphics2D, image: BufferedImage, destination: Rectangle, source: Rectangle) {
    g.drawImage(
        image,
        destination.x,
        destination.y,
        destination.x + destination.width,
        destination.y + destination.height,
        source.x,
        source.y,
        source.x + source.width,
        source.y + source.height,
        null,
    )
}

fun makeWhiteTransparent(source: BufferedImage): BufferedImage {
    val result = BufferedImage(source.width, source.height, BufferedImage.TYPE_INT_ARGB)
    for (y in 0 until source.height) {
        for (x in 0 until source.width) {
            val rgb = source.getRGB(x, y)
            val red = rgb shr 16 and 0xFF
            val green = rgb shr 8 and 0xFF
            val blue = rgb and 0xFF
            val minChannel = min(red, min(green, blue))
            val maxChannel = max(red, max(green, blue))
            val alpha = when {
                minChannel >= 248 && maxChannel - minChannel <= 10 -> 0
                minChannel >= 232 && maxChannel - minChannel <= 22 ->
                    (((248 - minChannel) / 16.0) * 255).roundToInt().coerceIn(0, 255)
                else -> 255
            }
            result.setRGB(x, y, (alpha shl 24) or (rgb and 0x00FFFFFF))
        }
    }
    return result
}

private fun drawPlaceholder(g: Graphics2D, rect: Rectangle, text: String, color: Color) {
    g.color = color
    g.fillRoundRect(rect.x, rect.y, rect.width, rect.height, 10, 10)
    g.color = Color(0x2F2F2F)
    g.drawRoundRect(rect.x, rect.y, rect.width, rect.height, 10, 10)
    g.font = g.font.deriveFont(Font.BOLD, 11f)
    g.drawString(text.take(10), rect.x + 6, rect.y + rect.height / 2)
}

private fun drawPlayerNameBadge(g: Graphics2D, rect: Rectangle, name: String) {
    val oldFont = g.font
    g.font = oldFont.deriveFont(Font.BOLD, 11f)
    val metrics = g.fontMetrics
    val label = playerNameBadgeLabelForMetrics(g, name, rect.width)
    val badge = playerNameBadgeRectForMetrics(g, rect, label)

    g.color = Color(0x2E, 0x21, 0x15, 210)
    g.fillRoundRect(badge.x, badge.y, badge.width, badge.height, 8, 8)
    g.color = Color.WHITE
    g.drawString(label, badge.x + (badge.width - metrics.stringWidth(label)) / 2, badge.y + metrics.ascent)
    g.font = oldFont
}

internal fun playerNameBadgeRect(rect: Rectangle, label: String): Rectangle {
    val badgeHeight = (rect.height * PLAYER_NAME_BADGE_HEIGHT_RATIO).roundToInt()
        .coerceIn(14, 18)
        .coerceAtMost(rect.height)
    val badgeWidth = (label.length * PLAYER_NAME_BADGE_APPROX_CHAR_WIDTH + 10)
        .coerceAtLeast(24)
        .coerceAtMost(rect.width)
    return Rectangle(
        rect.x + (rect.width - badgeWidth) / 2,
        rect.y + rect.height - badgeHeight - 2,
        badgeWidth,
        badgeHeight,
    )
}

internal fun playerNameBadgeLabel(name: String, tokenWidth: Int): String {
    val maxChars = ((tokenWidth - 10) / PLAYER_NAME_BADGE_APPROX_CHAR_WIDTH).coerceAtLeast(1)
    if (name.length <= maxChars) return name
    return name.take((maxChars - 1).coerceAtLeast(0)) + "…"
}

internal fun playerNameBadgeRectForMetrics(g: Graphics2D, rect: Rectangle, label: String): Rectangle {
    val badgeHeight = (g.fontMetrics.height + 2)
        .coerceAtLeast(14)
        .coerceAtMost((rect.height * PLAYER_NAME_BADGE_HEIGHT_RATIO).roundToInt().coerceAtLeast(14))
        .coerceAtMost(rect.height)
    val badgeWidth = (g.fontMetrics.stringWidth(label) + 10)
        .coerceAtLeast(24)
        .coerceAtMost(rect.width)
    return Rectangle(
        rect.x + (rect.width - badgeWidth) / 2,
        rect.y + rect.height - badgeHeight - 2,
        badgeWidth,
        badgeHeight,
    )
}

internal fun playerNameBadgeLabelForMetrics(g: Graphics2D, name: String, tokenWidth: Int): String {
    val maxWidth = (tokenWidth - 10).coerceAtLeast(1)
    if (g.fontMetrics.stringWidth(name) <= maxWidth) return name
    val ellipsis = "…"
    val available = maxWidth - g.fontMetrics.stringWidth(ellipsis)
    if (available <= 0) return ellipsis
    var end = name.length
    while (end > 0 && g.fontMetrics.stringWidth(name.take(end)) > available) {
        end--
    }
    return name.take(end) + ellipsis
}

private fun elideText(g: Graphics2D, text: String, maxWidth: Int): String {
    if (g.fontMetrics.stringWidth(text) <= maxWidth) return text
    val ellipsis = "…"
    val available = maxWidth - g.fontMetrics.stringWidth(ellipsis)
    if (available <= 0) return ellipsis
    var end = text.length
    while (end > 0 && g.fontMetrics.stringWidth(text.take(end)) > available) {
        end--
    }
    return text.take(end) + ellipsis
}

private fun playerColor(id: Int): Color = when (id) {
    0 -> Color(0x4E8BD8)
    1 -> Color(0xF2994A)
    2 -> Color(0xE88DB5)
    else -> Color(0xF2C94C)
}

private const val PLAYER_NAME_BADGE_HEIGHT_RATIO = 0.24
private const val PLAYER_NAME_BADGE_APPROX_CHAR_WIDTH = 14

private fun nestChoiceLabel(position: Position): String = when (position) {
    Position(0, 1) -> "巣A (1,2)"
    Position(5, 1) -> "巣B (6,2)"
    Position(0, 4) -> "巣C (1,5)"
    Position(5, 4) -> "巣D (6,5)"
    else -> "(${position.col + 1},${position.row + 1})"
}
