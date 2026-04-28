package app.codexchatviewer

import java.io.File
import javax.swing.JTextPane
import javax.swing.text.SimpleAttributeSet
import javax.swing.text.StyleConstants
import javax.swing.text.StyledDocument

data class TranscriptHeaderRange(
	val blockIndex: Int,
	val startOffset: Int,
	val endOffset: Int
)

object ChatStyledRenderer {
	private const val separator = "========================================================================"
	private val defaultTheme = ChatRenderThemes.terminalStyle

	fun configure(viewer: JTextPane) {
		viewer.font = defaultTheme.viewerFont
		viewer.isEditable = false
		viewer.background = defaultTheme.backgroundColor
		viewer.foreground = defaultTheme.foregroundColor
	}

	fun render(viewer: JTextPane, file: File?, sessionId: String?, parsedChatLog: ParsedChatLog?) {
		render(viewer, file, sessionId, parsedChatLog, defaultTheme)
	}

	fun render(
		viewer: JTextPane,
		file: File?,
		sessionId: String?,
		parsedChatLog: ParsedChatLog?,
		theme: ChatRenderTheme,
		collapsedBlockIndexes: Set<Int> = emptySet(),
		resetCaretToTop: Boolean = true
	): List<TranscriptHeaderRange> {
		viewer.font = theme.viewerFont
		viewer.background = theme.backgroundColor
		viewer.foreground = theme.foregroundColor

		val document = viewer.styledDocument
		document.remove(0, document.length)
		val headerRanges = mutableListOf<TranscriptHeaderRange>()

		appendLine(document, "Codex Chat Viewer", theme.headerStyle)
		appendBlankLine(document, theme)

		if (file == null || parsedChatLog == null) {
			appendLine(document, "Ready.", theme.bodyStyle)
			appendBlankLine(document, theme)
			appendLine(document, "Default theme: ${theme.name}", theme.metadataStyle)
			return emptyList()
		}

		appendLine(document, "File: ${file.name}", theme.metadataStyle)
		appendLine(document, "Path: ${file.absolutePath}", theme.metadataStyle)
		appendLine(document, "Session ID: ${sessionId ?: "Not detected"}", theme.metadataStyle)
		appendBlankLine(document, theme)
		appendLine(document, separator, theme.separatorStyle)
		appendBlankLine(document, theme)

		if (parsedChatLog.entries.isEmpty()) {
			appendLine(document, "No renderable chat messages found in this JSONL file.", theme.bodyStyle)
			if (parsedChatLog.observedEventCounts.isNotEmpty()) {
				appendBlankLine(document, theme)
				appendLine(document, "Observed event types:", theme.metadataStyle)
				parsedChatLog.observedEventCounts.entries
					.sortedByDescending { it.value }
					.take(8)
					.forEach { (name, count) ->
						appendLine(document, "- $name: $count", theme.metadataStyle)
					}
			}
			appendBlankLine(document, theme)
		} else {
			val blocks = parsedChatLog.transcriptBlocks()
			blocks.forEachIndexed { index, block ->
				val blockStyle = theme.blockStyleFor(block.type)
				val isCollapsed = index in collapsedBlockIndexes
				val marker = if (isCollapsed) theme.toggleMarkers.collapsed else theme.toggleMarkers.expanded
				val headerText = "$marker ${block.label}"
				val cardWidth = cardWidthFor(headerText, block.content, blockStyle, isCollapsed)
				val renderedHeader = cardText(headerText, blockStyle, cardWidth)
				val headerStart = document.length
				appendLine(document, renderedHeader, blockStyle.labelStyle, blockStyle)
				headerRanges += TranscriptHeaderRange(
					blockIndex = index,
					startOffset = headerStart,
					endOffset = headerStart + renderedHeader.length
				)
				if (!isCollapsed) {
					appendLine(document, cardText(block.content, blockStyle, cardWidth), blockStyle.contentStyle, blockStyle)
				}
				if (index != blocks.lastIndex) {
					appendBlankLine(document, theme)
				}
			}
			appendBlankLine(document, theme)
			appendBlankLine(document, theme)
		}

		appendLine(document, separator, theme.separatorStyle)
		appendBlankLine(document, theme)
		appendLine(document, "Parsed candidates: ${parsedChatLog.parsedCandidates}", theme.metadataStyle)
		appendLine(document, "Visible entries: ${parsedChatLog.entries.size}", theme.metadataStyle)
		appendLine(document, "Ignored lines: ${parsedChatLog.ignoredLines}", theme.metadataStyle)
		appendLine(document, "Malformed lines: ${parsedChatLog.malformedLines}", theme.metadataStyle)
		if (resetCaretToTop) {
			viewer.caretPosition = 0
		}
		return headerRanges
	}

	fun appendSystemNotice(viewer: JTextPane, notice: String) {
		appendSystemNotice(viewer, notice, defaultTheme)
	}

	fun appendSystemNotice(viewer: JTextPane, notice: String, theme: ChatRenderTheme) {
		val document = viewer.styledDocument
		if (document.length > 0) {
			appendBlankLine(document, theme)
		}
		val blockStyle = theme.blockStyleFor(RenderedEntryKind.SYSTEM)
		val cardWidth = cardWidthFor(RenderedEntryKind.SYSTEM.label, notice, blockStyle, isCollapsed = false)
		appendLine(document, cardText(RenderedEntryKind.SYSTEM.label, blockStyle, cardWidth), blockStyle.labelStyle, blockStyle)
		appendLine(document, cardText(notice, blockStyle, cardWidth), blockStyle.contentStyle, blockStyle)
	}

	private fun appendBlankLine(document: StyledDocument, theme: ChatRenderTheme) {
		appendLine(document, "", theme.bodyStyle)
	}

	private fun appendLine(
		document: StyledDocument,
		text: String,
		textStyle: ChatTextStyle,
		blockStyle: ChatBlockStyle? = null
	) {
		val attributes = SimpleAttributeSet().apply {
			StyleConstants.setForeground(this, textStyle.color)
			StyleConstants.setBold(this, textStyle.bold)
			StyleConstants.setFontFamily(this, textStyle.fontFamily)
			StyleConstants.setFontSize(this, textStyle.fontSize)
			textStyle.backgroundColor?.let { backgroundColor ->
				StyleConstants.setBackground(this, backgroundColor)
			}
		}
		val startOffset = document.length
		document.insertString(startOffset, "$text\n", attributes)
		document.setParagraphAttributes(
			startOffset,
			document.length - startOffset,
			paragraphAttributes(blockStyle),
			false
		)
	}

	private fun cardWidthFor(
		headerText: String,
		contentText: String,
		blockStyle: ChatBlockStyle,
		isCollapsed: Boolean
	): Int? {
		if (blockStyle.horizontalPadding <= 0) {
			return null
		}

		val contentLines = if (isCollapsed) emptyList() else contentText.renderLines()
		val widestLine = (listOf(headerText) + contentLines)
			.maxOfOrNull { it.length }
			?: headerText.length
		return maxOf(blockStyle.minimumCardWidth, widestLine)
	}

	private fun cardText(text: String, blockStyle: ChatBlockStyle, cardWidth: Int?): String {
		if (cardWidth == null || blockStyle.horizontalPadding <= 0) {
			return text
		}

		val padding = " ".repeat(blockStyle.horizontalPadding)
		return text.renderLines()
			.joinToString("\n") { line ->
				padding + line.padEnd(cardWidth) + padding
			}
	}

	private fun String.renderLines(): List<String> {
		return replace("\r\n", "\n")
			.replace('\r', '\n')
			.split('\n')
	}

	private fun paragraphAttributes(blockStyle: ChatBlockStyle?): SimpleAttributeSet {
		return SimpleAttributeSet().apply {
			val alignment = when (blockStyle?.alignment ?: ChatBlockAlignment.LEFT) {
				ChatBlockAlignment.LEFT -> StyleConstants.ALIGN_LEFT
				ChatBlockAlignment.RIGHT -> StyleConstants.ALIGN_RIGHT
				ChatBlockAlignment.CENTER -> StyleConstants.ALIGN_CENTER
			}
			StyleConstants.setAlignment(this, alignment)
			StyleConstants.setLeftIndent(this, blockStyle?.leftIndent ?: 0f)
			StyleConstants.setRightIndent(this, blockStyle?.rightIndent ?: 0f)
			StyleConstants.setFirstLineIndent(this, blockStyle?.firstLineIndent ?: 0f)
			StyleConstants.setSpaceAbove(this, blockStyle?.spaceAbove ?: 0f)
			StyleConstants.setSpaceBelow(this, blockStyle?.spaceBelow ?: 0f)
		}
	}
}
