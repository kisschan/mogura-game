package com.moguru.game.gui

import com.moguru.game.engine.GameState
import com.moguru.game.engine.TurnPhase
import com.moguru.game.model.Board
import com.moguru.game.model.CellType
import com.moguru.game.model.FoodType
import com.moguru.game.model.HoleTile
import com.moguru.game.model.Player
import com.moguru.game.model.Position
import com.moguru.game.model.Rotation
import com.moguru.game.model.TileShape
import java.awt.BasicStroke
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
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
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.ButtonGroup
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

fun main() {
    SwingUtilities.invokeLater {
        MoguraGameFrame(MoguraGameController()).isVisible = true
    }
}

class MoguraGameFrame(
    private val controller: MoguraGameController,
) : JFrame("モグラゲーム") {
    private val assets = GuiAssets()
    private val boardPanel = BoardPanel(controller, assets, ::handleBoardClick)
    private val currentPlayerPanel = CurrentPlayerPanel(assets)
    private val statusLabel = JLabel()
    private val diceLabel = JLabel("ダイス", SwingConstants.CENTER)
    private val logArea = JTextArea()
    private val captureButton = JButton("捕獲")
    private val eatButton = JButton("タベる")
    private val carryButton = JButton("レンコウ")
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
        minimumSize = Dimension(1120, 820)
        iconImage = assets.load("assets/images/ui/app_icon.png")

        contentPane.layout = BorderLayout(12, 12)
        (contentPane as JPanel).border = BorderFactory.createEmptyBorder(12, 12, 12, 12)
        contentPane.background = Color(0xF4F0E8)

        contentPane.add(boardPanel, BorderLayout.CENTER)
        contentPane.add(createSidePanel(), BorderLayout.EAST)

        newGameButton.addActionListener { promptNewGame() }
        captureButton.addActionListener { runAction { controller.captureCurrentPosition() } }
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

        promptNewGame()
    }

    private fun createSidePanel(): JPanel {
        val side = JPanel()
        side.layout = BoxLayout(side, BoxLayout.Y_AXIS)
        side.preferredSize = Dimension(330, 760)
        side.background = Color(0xF4F0E8)

        val header = JLabel("モグラゲーム")
        header.font = header.font.deriveFont(Font.BOLD, 24f)
        header.alignmentX = Component.LEFT_ALIGNMENT

        statusLabel.font = statusLabel.font.deriveFont(13f)

        logArea.isEditable = false
        logArea.font = Font(Font.MONOSPACED, Font.PLAIN, 12)
        logArea.rows = 12
        logArea.lineWrap = true
        logArea.wrapStyleWord = true

        diceLabel.preferredSize = Dimension(96, 96)
        diceLabel.maximumSize = Dimension(96, 96)
        diceLabel.border = BorderFactory.createLineBorder(Color(0x9A8C7A))
        diceLabel.background = Color.WHITE
        diceLabel.isOpaque = true

        val digTileChoicePanel = JPanel()
        digTileChoicePanel.layout = GridLayout(0, 1, 4, 4)
        digTileChoicePanel.background = Color(0xF4F0E8)
        digTileChoicePanel.alignmentX = Component.LEFT_ALIGNMENT

        val digTileChoiceGroup = ButtonGroup()
        digTileChoiceButtons.values.forEach { button ->
            button.font = button.font.deriveFont(12f)
            digTileChoiceGroup.add(button)
            digTileChoicePanel.add(button)
        }
        digTileChoiceButtons.forEach { (choice, button) ->
            button.addActionListener {
                if (controller.pendingDigPlacement != null) {
                    runAction { controller.selectPendingDigTile(choice) }
                }
            }
        }

        val rotationPanel = JPanel()
        rotationPanel.layout = BoxLayout(rotationPanel, BoxLayout.X_AXIS)
        rotationPanel.background = Color(0xF4F0E8)
        rotationPanel.alignmentX = Component.LEFT_ALIGNMENT

        val rotationGroup = ButtonGroup()
        rotationButtons.values.forEach { button ->
            button.font = button.font.deriveFont(11f)
            rotationGroup.add(button)
            rotationPanel.add(button)
        }
        rotationButtons.forEach { (rotation, button) ->
            button.addActionListener {
                if (controller.pendingDigPlacement != null) {
                    runAction { controller.setPendingDigRotation(rotation) }
                }
            }
        }

        val actionPanel = JPanel()
        actionPanel.layout = GridLayout(0, 2, 6, 6)
        actionPanel.background = Color(0xF4F0E8)
        actionPanel.alignmentX = Component.LEFT_ALIGNMENT
        actionPanel.add(captureButton)
        actionPanel.add(eatButton)
        actionPanel.add(carryButton)
        actionPanel.add(skipButton)
        actionPanel.add(endTurnButton)

        newGameButton.alignmentX = Component.LEFT_ALIGNMENT
        currentPlayerPanel.alignmentX = Component.LEFT_ALIGNMENT
        statusLabel.alignmentX = Component.LEFT_ALIGNMENT
        diceLabel.alignmentX = Component.LEFT_ALIGNMENT

        side.add(header)
        side.add(Box.createVerticalStrut(10))
        side.add(newGameButton)
        side.add(Box.createVerticalStrut(16))
        side.add(currentPlayerPanel)
        side.add(Box.createVerticalStrut(8))
        side.add(statusLabel)
        side.add(Box.createVerticalStrut(12))
        side.add(sectionLabel("配置するタイル"))
        side.add(digTileChoicePanel)
        side.add(Box.createVerticalStrut(12))
        side.add(sectionLabel("掘るタイルの回転"))
        side.add(rotationPanel)
        side.add(Box.createVerticalStrut(12))
        side.add(sectionLabel("操作"))
        side.add(actionPanel)
        side.add(Box.createVerticalStrut(12))
        side.add(sectionLabel("直近のダイス"))
        side.add(diceLabel)
        side.add(Box.createVerticalStrut(12))
        side.add(sectionLabel("ログ"))
        side.add(wrapScroll(logArea, 320))

        return side
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

        controller.startNewGame(choice.toInt())
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
                    controller.captureCurrentPosition()
                } else {
                    GameActionResult(false, "捕獲するには現在のプレイヤーがいるマスをクリックしてください。")
                }
            }

            TurnPhase.DECIDE,
            TurnPhase.END,
            -> GameActionResult(false, "ターンを終えるには右側の操作ボタンを使ってください。")
        }

        if (!result.success) {
            Toolkit.getDefaultToolkit().beep()
            statusLabel.text = result.message
        }
        refresh()
    }

    private fun selectedRotation(): Rotation =
        rotationButtons.entries.firstOrNull { it.value.isSelected }?.key ?: Rotation.DEG_0

    private fun runAction(action: () -> GameActionResult) {
        val result = action()
        if (!result.success) {
            Toolkit.getDefaultToolkit().beep()
            statusLabel.text = result.message
        }
        refresh()
    }

    private fun refresh() {
        val current = controller.engine
        val player = controller.currentPlayer
        val isPlaying = current?.gameState == GameState.PLAYING
        val hasPendingDecision = current?.currentPhase == TurnPhase.DECIDE && controller.pendingFoodDecision != null
        val hasPendingDig = current?.currentPhase == TurnPhase.DIG && controller.pendingDigPlacement != null
        val canAdvanceFromDig = controller.canAdvanceFromDigWithoutTargets()

        currentPlayerPanel.render(currentPlayerDisplay(player, current?.currentPhase))
        statusLabel.text = controller.pendingFoodDecision?.let { food ->
            "${food.type.displayName()} をタベるかレンコウしてください。"
        } ?: controller.pendingDigPlacement?.let { pending ->
            val selected = controller.pendingDigTileChoice?.label() ?: DigTileChoice.REVEALED.label()
            val drawn = pending.drawnTile?.shape?.displayName() ?: "なし"
            "${selected}を選択中。山札: ${drawn}。回転を選び、同じマスをもう一度クリックしてください。"
        } ?: if (canAdvanceFromDig) {
            "掘れる裏向きタイルがありません。移動へ進んでください。"
        } else {
            phaseHelp(current?.currentPhase)
        }

        captureButton.isEnabled = isPlaying && controller.canCapture()
        eatButton.isEnabled = isPlaying && hasPendingDecision
        carryButton.isEnabled = isPlaying && hasPendingDecision
        skipButton.isEnabled = isPlaying && !hasPendingDecision &&
            (current?.currentPhase != TurnPhase.DIG || canAdvanceFromDig)
        skipButton.text = when {
            current?.currentPhase == TurnPhase.END -> "ターン終了"
            canAdvanceFromDig -> "移動へ進む"
            else -> "スキップ"
        }
        endTurnButton.isEnabled = isPlaying && current?.currentPhase != TurnPhase.DIG && !hasPendingDecision
        digTileChoiceButtons.forEach { (choice, button) ->
            button.text = digTileChoiceText(choice, controller.pendingDigPlacement)
            button.isEnabled = hasPendingDig && (choice != DigTileChoice.DRAWN || controller.pendingDigPlacement?.drawnTile != null)
            button.isSelected = controller.pendingDigTileChoice == choice
        }
        syncRotationButtons(hasPendingDig)

        logArea.text = controller.logs.joinToString("\n")
        logArea.caretPosition = logArea.document.length

        val dice = controller.lastDiceRoll
        val diceImage = dice?.let { assets.load("assets/images/dice/dice_$it.png") }
        diceLabel.icon = diceImage?.let { javax.swing.ImageIcon(it.getScaledInstance(80, 80, Image.SCALE_SMOOTH)) }
        diceLabel.text = if (diceImage == null) dice?.toString() ?: "ダイス" else ""

        boardPanel.repaint()
    }

    private fun syncRotationButtons(hasPendingDig: Boolean) {
        val selectedRotation = rotationSelectionForPendingDig(hasPendingDig, controller.pendingDigRotation)
        rotationButtons.forEach { (rotation, button) ->
            button.isEnabled = hasPendingDig
            button.isSelected = rotation == selectedRotation
        }
    }

    private fun phaseHelp(phase: TurnPhase?): String = when (phase) {
        TurnPhase.DIG -> "ハイライトされた裏向きタイルをクリックしてください。"
        TurnPhase.MOVE -> "ハイライトされた移動可能マスをクリックしてください。"
        TurnPhase.CAPTURE -> "プレイヤーの足元にエサがあれば捕獲できます。"
        TurnPhase.DECIDE -> "タベるかレンコウを選んでください。"
        TurnPhase.END -> "ターンを終了してください。"
        null -> "新しいゲームを開始してください。"
    }

    private fun digTileChoiceText(choice: DigTileChoice, pending: PendingDigPlacement?): String {
        val shape = when (choice) {
            DigTileChoice.REVEALED -> pending?.revealedTile?.shape?.displayName()
            DigTileChoice.DRAWN -> pending?.drawnTile?.shape?.displayName()
        }
        return "${choice.label()}: ${shape ?: "-"}"
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
        preferredSize = Dimension(720, 900)
        minimumSize = Dimension(560, 700)
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

                current.foodPositions[position]?.let { food ->
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
                        scale = foodCardScaleForPhase(current.currentPhase),
                    )
                }

                if (position in highlights) {
                    drawHighlight(g, cellRect, current.currentPhase)
                }
            }
        }

        drawDigDirectionArrows(g, highlights)
        drawPlayers(g)
        drawHoveredFoodPreview(g)
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

        val markerRects = hungerMeterMarkerRects(centers, markerSize)

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
                        g.drawImage(image, tokenRect.x, tokenRect.y, tokenRect.width, tokenRect.height, null)
                    } else {
                        drawPlaceholder(g, tokenRect, player.name.take(1), playerColor(player.id))
                    }

                    if (player == controller.currentPlayer) {
                        g.color = Color(0xFFE66D)
                        g.stroke = BasicStroke(4f)
                        g.drawOval(tokenRect.x, tokenRect.y, tokenRect.width, tokenRect.height)
                    }
                }
            }
    }

    private fun drawSmallImage(
        g: Graphics2D,
        image: BufferedImage?,
        rect: Rectangle,
        anchor: Anchor,
        scale: Double,
    ) {
        val smallRect = when (anchor) {
            Anchor.BOTTOM_RIGHT -> foodCardRect(rect, scale)
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
        val food = current.foodPositions[position] ?: return
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
        g.color = Color(color.red, color.green, color.blue, 92)
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
                val cellRect = cellRect(position) ?: return@firstOrNull false
                val cardRect = foodCardRect(cellRect, foodCardScaleForPhase(current.currentPhase))
                cardRect.contains(point)
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

class GuiAssets {
    private val cache = mutableMapOf<String, BufferedImage?>()

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

    fun foodImage(type: FoodType): BufferedImage? = load(
        when (type) {
            FoodType.BEETLE_LARVA -> "assets/images/foods/food_beetle_larva.png"
            FoodType.EARTHWORM -> "assets/images/foods/food_earthworm.png"
            FoodType.MOLE_CRICKET -> "assets/images/foods/food_mole_cricket.png"
            FoodType.CENTIPEDE -> "assets/images/foods/food_centipede.png"
            FoodType.FROG -> "assets/images/foods/food_frog.png"
        },
    )

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
}

private const val PLAYER_TOKEN_SCALE = 0.95
private const val PLAYER_TOKEN_STACK_OFFSET_RATIO = 0.10
private const val PLAYER_TOKEN_MIN_STACK_OFFSET = 8
private const val PLAYER_TOKEN_MAX_STACKED = 4

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

private fun playerColor(id: Int): Color = when (id) {
    0 -> Color(0x4E8BD8)
    1 -> Color(0xF2994A)
    2 -> Color(0xE88DB5)
    else -> Color(0xF2C94C)
}
