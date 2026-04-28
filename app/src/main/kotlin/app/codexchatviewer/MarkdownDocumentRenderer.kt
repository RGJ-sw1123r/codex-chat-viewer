package app.codexchatviewer

import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Font
import java.awt.GridBagLayout
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

object MarkdownDocumentRenderer {
	private const val separator = "========================================================================"
	private const val safeHorizontalPadding = 72
	private const val minDocumentWidth = 640
	private const val minAvailableDocumentWidth = 180
	private const val maxDocumentWidth = 980
	private const val sectionHorizontalChrome = 36
	private val titleFont = Font(Font.SANS_SERIF, Font.BOLD, 20)
	private val sectionFont = Font(Font.SANS_SERIF, Font.BOLD, 14)
	private val bodyFont = Font(Font.SANS_SERIF, Font.PLAIN, 14)
	private val metaFont = Font(Font.SANS_SERIF, Font.PLAIN, 12)
	private val codeFont = Font(Font.MONOSPACED, Font.PLAIN, 12)

	fun render(
		container: JPanel,
		file: File?,
		sessionId: String?,
		parsedChatLog: ParsedChatLog?,
		theme: ChatRenderTheme,
		viewportWidth: Int = 900,
		collapsedBlockIndexes: Set<Int> = emptySet(),
		onHeaderClicked: (Int) -> Unit = {}
	): MessengerRenderResult {
		configureContainer(container, theme)
		val documentWidth = documentWidthFor(viewportWidth)
		val document = createDocumentColumn(documentWidth, theme)

		val transcript = StringBuilder()
		val headerRanges = mutableListOf<TranscriptHeaderRange>()
		val blockRanges = mutableListOf<MessengerBlockRange>()

		appendTranscriptLine(transcript, "Codex Chat Viewer")
		appendTranscriptLine(transcript, "")
		document.add(createTitleSection("Codex Chat Viewer", theme))

		if (file == null || parsedChatLog == null) {
			appendTranscriptLine(transcript, "Ready.")
			appendTranscriptLine(transcript, "")
			appendTranscriptLine(transcript, "Default theme: ${theme.name}")
			document.add(createNoticeSection("Ready.\nDefault theme: ${theme.name}", theme, documentWidth))
			addDocument(container, document)
			return MessengerRenderResult(transcript.toString(), emptyList(), emptyList())
		}

		appendTranscriptLine(transcript, "File: ${file.name}")
		appendTranscriptLine(transcript, "Path: ${file.absolutePath}")
		appendTranscriptLine(transcript, "Session ID: ${sessionId ?: "Not detected"}")
		appendTranscriptLine(transcript, "")
		appendTranscriptLine(transcript, separator)
		appendTranscriptLine(transcript, "")
		document.add(createNoticeSection("File: ${file.name}\nSession ID: ${sessionId ?: "Not detected"}", theme, documentWidth))

		if (parsedChatLog.entries.isEmpty()) {
			appendTranscriptLine(transcript, "No renderable chat messages found in this JSONL file.")
			document.add(createNoticeSection("No renderable chat messages found in this JSONL file.", theme, documentWidth))
			if (parsedChatLog.observedEventCounts.isNotEmpty()) {
				appendTranscriptLine(transcript, "")
				appendTranscriptLine(transcript, "Observed event types:")
				val observed = parsedChatLog.observedEventCounts.entries
					.sortedByDescending { it.value }
					.take(8)
					.joinToString("\n") { (name, count) -> "- $name: $count" }
				appendTranscriptLine(transcript, observed)
				document.add(createNoticeSection("Observed event types:\n$observed", theme, documentWidth))
			}
			appendTranscriptLine(transcript, "")
		} else {
			val blocks = parsedChatLog.transcriptBlocks()
			blocks.forEachIndexed { index, block ->
				val isCollapsed = index in collapsedBlockIndexes
				val marker = if (isCollapsed) theme.toggleMarkers.collapsed else theme.toggleMarkers.expanded
				val headerText = "$marker ${block.label}"
				val blockStart = transcript.length
				appendTranscriptLine(transcript, headerText)
				headerRanges += TranscriptHeaderRange(index, blockStart, blockStart + headerText.length)
				if (!isCollapsed) {
					appendTranscriptLine(transcript, block.content)
				}
				if (index != blocks.lastIndex) {
					appendTranscriptLine(transcript, "")
				}

				val section = createBlockSection(
					block = block,
					headerText = headerText,
					isCollapsed = isCollapsed,
					theme = theme,
					documentWidth = documentWidth,
					onHeaderClicked = { onHeaderClicked(index) }
				)
				document.add(section)
				blockRanges += MessengerBlockRange(index, blockStart, transcript.length, section)
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
		document.add(
			createNoticeSection(
				"Parsed candidates: ${parsedChatLog.parsedCandidates}\n" +
					"Visible entries: ${parsedChatLog.entries.size}\n" +
					"Ignored lines: ${parsedChatLog.ignoredLines}\n" +
					"Malformed lines: ${parsedChatLog.malformedLines}",
				theme,
				documentWidth
			)
		)

		addDocument(container, document)
		return MessengerRenderResult(transcript.toString().trimEnd(), headerRanges, blockRanges)
	}

	fun appendSystemNotice(container: JPanel, notice: String, theme: ChatRenderTheme, viewportWidth: Int = 900) {
		if (container.layout !is BoxLayout) {
			configureContainer(container, theme)
		}
		val documentWidth = documentWidthFor(viewportWidth)
		val document = createDocumentColumn(documentWidth, theme)
		document.add(createNoticeSection(notice, theme, documentWidth))
		addDocument(container, document)
	}

	private fun configureContainer(container: JPanel, theme: ChatRenderTheme) {
		container.removeAll()
		container.layout = BoxLayout(container, BoxLayout.Y_AXIS)
		container.background = theme.backgroundColor
		container.isOpaque = true
		container.border = BorderFactory.createEmptyBorder(18, 18, 24, 18)
	}

	private fun createDocumentColumn(width: Int, theme: ChatRenderTheme): JPanel {
		return FixedWidthPanel(width).apply {
			layout = BoxLayout(this, BoxLayout.Y_AXIS)
			background = theme.backgroundColor
			isOpaque = false
			alignmentX = Component.CENTER_ALIGNMENT
		}
	}

	private fun createTitleSection(title: String, theme: ChatRenderTheme): JComponent {
		val label = JLabel(title)
		label.font = titleFont
		label.foreground = theme.headerStyle.color
		val section = JPanel(BorderLayout())
		section.isOpaque = false
		section.border = BorderFactory.createEmptyBorder(0, 4, 16, 4)
		section.add(label, BorderLayout.CENTER)
		section.maximumSize = Dimension(Int.MAX_VALUE, section.preferredSize.height)
		return section
	}

	private fun createNoticeSection(text: String, theme: ChatRenderTheme, documentWidth: Int): JComponent {
		return createSection(
			name = "markdown-notice-section",
			headerText = "Note",
			content = text,
			style = theme.blockStyleFor(RenderedEntryKind.SYSTEM),
			type = RenderedEntryKind.SYSTEM,
			isCollapsed = false,
			documentWidth = documentWidth,
			onHeaderClicked = null
		)
	}

	private fun createBlockSection(
		block: TranscriptBlock,
		headerText: String,
		isCollapsed: Boolean,
		theme: ChatRenderTheme,
		documentWidth: Int,
		onHeaderClicked: () -> Unit
	): JComponent {
		return createSection(
			name = "markdown-section-${block.type.name}",
			headerText = headerText,
			content = block.content,
			style = theme.blockStyleFor(block.type),
			type = block.type,
			isCollapsed = isCollapsed,
			documentWidth = documentWidth,
			onHeaderClicked = onHeaderClicked
		)
	}

	private fun createSection(
		name: String,
		headerText: String,
		content: String,
		style: ChatBlockStyle,
		type: RenderedEntryKind,
		isCollapsed: Boolean,
		documentWidth: Int,
		onHeaderClicked: (() -> Unit)?
	): JComponent {
		val section = JPanel(BorderLayout(0, 8))
		section.name = name
		section.background = style.contentStyle.backgroundColor ?: Color.WHITE
		section.isOpaque = true
		section.border = BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(1, accentBorderWidth(type), 1, 1, style.labelStyle.color),
			BorderFactory.createEmptyBorder(12, 14, 12, 14)
		)

		val header = JLabel(headerText)
		header.name = name.replace("section", "header")
		header.font = sectionFont
		header.foreground = style.labelStyle.color
		if (onHeaderClicked != null) {
			header.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
			header.addMouseListener(object : MouseAdapter() {
				override fun mouseClicked(event: MouseEvent) {
					if (event.button == MouseEvent.BUTTON1) {
						onHeaderClicked()
					}
				}
			})
		}
		section.add(header, BorderLayout.NORTH)

		if (!isCollapsed) {
			val textWidth = (documentWidth - sectionHorizontalChrome).coerceAtLeast(120)
			val area = WrappingTextArea(content, textWidth)
			area.name = name.replace("section", "content")
			area.font = if (isTechnicalBlock(type)) codeFont else if (isMetaBlock(type)) metaFont else bodyFont
			area.foreground = style.contentStyle.color
			area.isEditable = false
			area.isOpaque = false
			area.lineWrap = true
			area.wrapStyleWord = !isTechnicalBlock(type)
			area.border = BorderFactory.createEmptyBorder(0, 0, 0, 0)
			section.add(area, BorderLayout.CENTER)
		}

		section.maximumSize = Dimension(Int.MAX_VALUE, Int.MAX_VALUE)
		val wrapper = JPanel(BorderLayout())
		wrapper.isOpaque = false
		wrapper.border = BorderFactory.createEmptyBorder(0, 0, 12, 0)
		wrapper.add(section, BorderLayout.CENTER)
		wrapper.maximumSize = Dimension(Int.MAX_VALUE, Int.MAX_VALUE)
		return wrapper
	}

	private fun addDocument(container: JPanel, document: JPanel) {
		val row = JPanel(GridBagLayout())
		row.isOpaque = false
		row.add(document)
		row.maximumSize = Dimension(Int.MAX_VALUE, Int.MAX_VALUE)
		container.add(row)
		container.add(Box.createVerticalGlue())
		container.revalidate()
		container.repaint()
	}

	private fun documentWidthFor(viewportWidth: Int): Int {
		val available = (viewportWidth - safeHorizontalPadding).coerceAtLeast(minAvailableDocumentWidth)
		val desired = (viewportWidth * 0.82).toInt().coerceIn(minDocumentWidth, maxDocumentWidth)
		return desired.coerceAtMost(available)
	}

	private fun accentBorderWidth(type: RenderedEntryKind): Int {
		return if (isTechnicalBlock(type) || isMetaBlock(type)) 4 else 3
	}

	private fun isTechnicalBlock(type: RenderedEntryKind): Boolean {
		return type == RenderedEntryKind.TOOL_CALL || type == RenderedEntryKind.TOOL_RESULT
	}

	private fun isMetaBlock(type: RenderedEntryKind): Boolean {
		return when (type) {
			RenderedEntryKind.SYSTEM,
			RenderedEntryKind.CONTEXT,
			RenderedEntryKind.TASK -> true
			else -> false
		}
	}

	private fun appendTranscriptLine(transcript: StringBuilder, text: String) {
		transcript.append(text).append('\n')
	}
}

private class FixedWidthPanel(
	private val fixedWidth: Int
) : JPanel() {
	override fun getPreferredSize(): Dimension {
		val preferred = super.getPreferredSize()
		return Dimension(fixedWidth, preferred.height)
	}

	override fun getMaximumSize(): Dimension {
		return Dimension(fixedWidth, Int.MAX_VALUE)
	}
}

private class WrappingTextArea(
	text: String,
	private val fixedWidth: Int
) : JTextArea(text) {
	override fun getPreferredSize(): Dimension {
		setSize(Dimension(fixedWidth, Short.MAX_VALUE.toInt()))
		val preferred = super.getPreferredSize()
		return Dimension(fixedWidth, preferred.height)
	}

	override fun getMaximumSize(): Dimension {
		return Dimension(fixedWidth, Int.MAX_VALUE)
	}
}
