package app.codexchatviewer

import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.GridBagLayout
import java.awt.RenderingHints
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.io.File
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextArea

data class ComponentBlockRange(
	val blockIndex: Int,
	val startOffset: Int,
	val endOffset: Int,
	val component: JComponent
)

data class ComponentRenderResult(
	val transcriptText: String,
	val headerRanges: List<TranscriptHeaderRange>,
	val blockRanges: List<ComponentBlockRange>
)

private const val messengerSafeHorizontalPadding = 56
private const val messengerMinimumAvailableWidth = 160

object MessengerChatRenderer {
	private const val separator = "========================================================================"
	private val bubbleFont = Font(Font.SANS_SERIF, Font.PLAIN, 14)
	private val labelFont = Font(Font.SANS_SERIF, Font.BOLD, 12)
	private val metaFont = Font(Font.SANS_SERIF, Font.PLAIN, 12)

	fun render(
		container: JPanel,
		file: File?,
		sessionId: String?,
		parsedChatLog: ParsedChatLog?,
		theme: ChatRenderTheme,
		viewportWidth: Int = 900,
		collapsedBlockIndexes: Set<Int> = emptySet(),
		onHeaderClicked: (Int) -> Unit = {}
	): ComponentRenderResult {
		configureContainer(container, theme)
		val layoutWidths = MessengerLayoutWidths.fromViewportWidth(viewportWidth)

		val transcript = StringBuilder()
		val headerRanges = mutableListOf<TranscriptHeaderRange>()
		val blockRanges = mutableListOf<ComponentBlockRange>()

		appendTranscriptLine(transcript, "Codex Chat Viewer")
		appendTranscriptLine(transcript, "")
		addNoticeCard(container, "Codex Chat Viewer", theme, layoutWidths)

		if (file == null || parsedChatLog == null) {
			appendTranscriptLine(transcript, "Ready.")
			appendTranscriptLine(transcript, "")
			appendTranscriptLine(transcript, "Default theme: ${theme.name}")
			addNoticeCard(container, "Ready.\nDefault theme: ${theme.name}", theme, layoutWidths)
			finish(container)
			return ComponentRenderResult(transcript.toString(), emptyList(), emptyList())
		}

		appendTranscriptLine(transcript, "File: ${file.name}")
		appendTranscriptLine(transcript, "Path: ${file.absolutePath}")
		appendTranscriptLine(transcript, "Session ID: ${sessionId ?: "Not detected"}")
		appendTranscriptLine(transcript, "")
		appendTranscriptLine(transcript, separator)
		appendTranscriptLine(transcript, "")
		addNoticeCard(
			container,
			"File: ${file.name}\nSession ID: ${sessionId ?: "Not detected"}",
			theme,
			layoutWidths
		)

		if (parsedChatLog.entries.isEmpty()) {
			appendTranscriptLine(transcript, "No renderable chat messages found in this JSONL file.")
			addNoticeCard(container, "No renderable chat messages found in this JSONL file.", theme, layoutWidths)
			if (parsedChatLog.observedEventCounts.isNotEmpty()) {
				appendTranscriptLine(transcript, "")
				appendTranscriptLine(transcript, "Observed event types:")
				val observed = parsedChatLog.observedEventCounts.entries
					.sortedByDescending { it.value }
					.take(8)
					.joinToString("\n") { (name, count) -> "- $name: $count" }
				appendTranscriptLine(transcript, observed)
				addNoticeCard(container, "Observed event types:\n$observed", theme, layoutWidths)
			}
			appendTranscriptLine(transcript, "")
		} else {
			val blocks = parsedChatLog.transcriptBlocks()
			blocks.forEachIndexed { index, block ->
				val blockStyle = theme.blockStyleFor(block.type)
				val isCollapsed = index in collapsedBlockIndexes
				val marker = if (isCollapsed) theme.toggleMarkers.collapsed else theme.toggleMarkers.expanded
				val headerText = "$marker ${block.label}"
				val blockStart = transcript.length
				appendTranscriptLine(transcript, headerText)
				val headerStart = blockStart
				val headerEnd = headerStart + headerText.length
				headerRanges += TranscriptHeaderRange(index, headerStart, headerEnd)
				if (!isCollapsed) {
					appendTranscriptLine(transcript, block.content)
				}
				if (index != blocks.lastIndex) {
					appendTranscriptLine(transcript, "")
				}

				val row = createBlockRow(
					block = block,
					headerText = headerText,
					isCollapsed = isCollapsed,
					blockStyle = blockStyle,
					layoutWidths = layoutWidths,
					onHeaderClicked = { onHeaderClicked(index) }
				)
				container.add(row)
				blockRanges += ComponentBlockRange(index, blockStart, transcript.length, row)
			}
			appendTranscriptLine(transcript, "")
			appendTranscriptLine(transcript, "")
		}

		appendTranscriptLine(transcript, separator)
		appendTranscriptLine(transcript, "")
		appendTranscriptLine(transcript, "Parsed candidates: ${parsedChatLog.parsedCandidates}")
		appendTranscriptLine(transcript, "Visible entries: ${parsedChatLog.entries.size}")
		appendTranscriptLine(transcript, "Ignored lines: ${parsedChatLog.ignoredLines}")
		appendTranscriptLine(transcript, "Malformed lines: ${parsedChatLog.malformedLines}")
		addNoticeCard(
			container,
			"Parsed candidates: ${parsedChatLog.parsedCandidates}\n" +
				"Visible entries: ${parsedChatLog.entries.size}\n" +
				"Ignored lines: ${parsedChatLog.ignoredLines}\n" +
				"Malformed lines: ${parsedChatLog.malformedLines}",
			theme,
			layoutWidths
		)

		finish(container)
		return ComponentRenderResult(transcript.toString().trimEnd(), headerRanges, blockRanges)
	}

	fun appendSystemNotice(container: JPanel, notice: String, theme: ChatRenderTheme, viewportWidth: Int = 900) {
		if (container.layout !is BoxLayout) {
			configureContainer(container, theme)
		}
		addNoticeCard(container, notice, theme, MessengerLayoutWidths.fromViewportWidth(viewportWidth))
		finish(container)
	}

	private fun configureContainer(container: JPanel, theme: ChatRenderTheme) {
		container.removeAll()
		container.layout = BoxLayout(container, BoxLayout.Y_AXIS)
		container.background = theme.backgroundColor
		container.isOpaque = true
		container.border = BorderFactory.createEmptyBorder(10, 10, 14, 10)
	}

	private fun createBlockRow(
		block: TranscriptBlock,
		headerText: String,
		isCollapsed: Boolean,
		blockStyle: ChatBlockStyle,
		layoutWidths: MessengerLayoutWidths,
		onHeaderClicked: () -> Unit
	): JComponent {
		val row = JPanel(BorderLayout())
		row.name = "messenger-row-${block.type.name}"
		row.isOpaque = false
		row.border = BorderFactory.createEmptyBorder(5, 4, 5, 4)

		val bubbleWidth = layoutWidths.widthFor(block.type)
		val bubble = RoundedCardPanel(blockStyle.contentStyle.backgroundColor ?: Color.WHITE, bubbleWidth)
		bubble.name = "messenger-bubble-${block.type.name}"
		bubble.layout = BorderLayout(0, 6)
		bubble.border = BorderFactory.createEmptyBorder(
			if (isSecondaryBlock(block.type)) 6 else 8,
			if (isSecondaryBlock(block.type)) 10 else 12,
			if (isSecondaryBlock(block.type)) 7 else 9,
			if (isSecondaryBlock(block.type)) 10 else 12
		)

		val header = JLabel(headerText)
		header.name = "messenger-header-${block.type.name}"
		header.font = labelFont
		header.foreground = blockStyle.labelStyle.color
		header.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
		header.addMouseListener(object : MouseAdapter() {
			override fun mouseClicked(event: MouseEvent) {
				if (event.button == MouseEvent.BUTTON1) {
					onHeaderClicked()
				}
			}
		})
		bubble.add(header, BorderLayout.NORTH)

		if (!isCollapsed) {
			val content = JTextArea(block.content)
			content.name = "messenger-content-${block.type.name}"
			content.font = if (block.type == RenderedEntryKind.TOOL_CALL || block.type == RenderedEntryKind.TOOL_RESULT) {
				Font(Font.MONOSPACED, Font.PLAIN, 12)
			} else {
				bubbleFont
			}
			content.foreground = blockStyle.contentStyle.color
			content.isEditable = false
			content.isOpaque = false
			content.lineWrap = true
			content.wrapStyleWord = true
			content.columns = columnsFor(block.type, bubbleWidth)
			content.border = BorderFactory.createEmptyBorder(0, 0, 0, 0)
			content.alignmentX = Component.LEFT_ALIGNMENT
			bubble.add(content, BorderLayout.CENTER)
		}

		when (blockStyle.alignment) {
			ChatBlockAlignment.RIGHT -> row.add(bubble, BorderLayout.EAST)
			ChatBlockAlignment.LEFT -> row.add(bubble, BorderLayout.WEST)
			ChatBlockAlignment.CENTER -> {
				val center = JPanel(GridBagLayout())
				center.isOpaque = false
				center.add(bubble)
				row.add(center, BorderLayout.CENTER)
			}
		}

		row.maximumSize = Dimension(Int.MAX_VALUE, row.preferredSize.height)
		return row
	}

	private fun addNoticeCard(container: JPanel, text: String, theme: ChatRenderTheme, layoutWidths: MessengerLayoutWidths) {
		val blockStyle = theme.blockStyleFor(RenderedEntryKind.SYSTEM)
		val cardWidth = layoutWidths.meta
		val card = RoundedCardPanel(blockStyle.contentStyle.backgroundColor ?: Color(231, 238, 244), cardWidth)
		card.name = "messenger-notice-card"
		card.layout = BorderLayout()
		card.border = BorderFactory.createEmptyBorder(6, 10, 6, 10)

		val area = JTextArea(text)
		area.font = metaFont
		area.foreground = blockStyle.contentStyle.color
		area.isEditable = false
		area.isOpaque = false
		area.lineWrap = true
		area.wrapStyleWord = true
		area.columns = columnsFor(RenderedEntryKind.SYSTEM, cardWidth)
		card.add(area, BorderLayout.CENTER)

		val row = JPanel(GridBagLayout())
		row.name = "messenger-notice-row"
		row.isOpaque = false
		row.border = BorderFactory.createEmptyBorder(4, 4, 6, 4)
		row.add(card)
		row.maximumSize = Dimension(Int.MAX_VALUE, row.preferredSize.height)
		container.add(row)
	}

	private fun finish(container: JPanel) {
		container.add(Box.createVerticalGlue())
		container.revalidate()
		container.repaint()
	}

	private fun columnsFor(type: RenderedEntryKind, bubbleWidth: Int): Int {
		val horizontalInsets = if (isSecondaryBlock(type)) 20 else 24
		val approximateCharacterWidth = if (type == RenderedEntryKind.TOOL_CALL || type == RenderedEntryKind.TOOL_RESULT) 7 else 8
		return ((bubbleWidth - horizontalInsets) / approximateCharacterWidth).coerceIn(24, 96)
	}

	private fun MessengerLayoutWidths.widthFor(type: RenderedEntryKind): Int {
		return when (type) {
			RenderedEntryKind.YOU, RenderedEntryKind.CODEX -> message
			RenderedEntryKind.TOOL_CALL, RenderedEntryKind.TOOL_RESULT -> tool
			RenderedEntryKind.SYSTEM, RenderedEntryKind.CONTEXT, RenderedEntryKind.TASK -> meta
		}
	}

	private fun isSecondaryBlock(type: RenderedEntryKind): Boolean {
		return when (type) {
			RenderedEntryKind.TOOL_CALL,
			RenderedEntryKind.TOOL_RESULT,
			RenderedEntryKind.SYSTEM,
			RenderedEntryKind.CONTEXT,
			RenderedEntryKind.TASK -> true
			RenderedEntryKind.YOU,
			RenderedEntryKind.CODEX -> false
		}
	}

	private fun appendTranscriptLine(transcript: StringBuilder, text: String) {
		transcript.append(text).append('\n')
	}
}

data class MessengerLayoutWidths(
	val message: Int,
	val meta: Int,
	val tool: Int
) {
	companion object {
		fun fromViewportWidth(viewportWidth: Int): MessengerLayoutWidths {
			val safeWidth = (viewportWidth - messengerSafeHorizontalPadding).coerceAtLeast(messengerMinimumAvailableWidth)
			return MessengerLayoutWidths(
				message = clampedWidth(viewportWidth, ratio = 0.46, min = 500, max = 720, safeWidth = safeWidth),
				meta = clampedWidth(viewportWidth, ratio = 0.54, min = 600, max = 820, safeWidth = safeWidth),
				tool = clampedWidth(viewportWidth, ratio = 0.58, min = 640, max = 900, safeWidth = safeWidth)
			)
		}

		private fun clampedWidth(viewportWidth: Int, ratio: Double, min: Int, max: Int, safeWidth: Int): Int {
			val target = (viewportWidth * ratio).toInt().coerceIn(min, max)
			return target.coerceAtMost(safeWidth)
		}
	}
}

private class RoundedCardPanel(
	private val fillColor: Color,
	private val fixedWidth: Int
) : JPanel() {
	override fun getPreferredSize(): Dimension {
		val preferred = super.getPreferredSize()
		return Dimension(fixedWidth, preferred.height)
	}

	override fun getMinimumSize(): Dimension {
		val minimum = super.getMinimumSize()
		return Dimension(messengerMinimumAvailableWidth.coerceAtMost(fixedWidth), minimum.height)
	}

	override fun getMaximumSize(): Dimension {
		return Dimension(fixedWidth, Int.MAX_VALUE)
	}

	override fun paintComponent(graphics: Graphics) {
		val g2 = graphics.create() as Graphics2D
		try {
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
			g2.color = fillColor
			g2.fillRoundRect(0, 0, width, height, 18, 18)
		} finally {
			g2.dispose()
		}
		super.paintComponent(graphics)
	}

	override fun isOpaque(): Boolean {
		return false
	}
}
