package app.codexchatviewer

import java.io.File
import javax.swing.JTextPane
import javax.swing.text.SimpleAttributeSet
import javax.swing.text.StyleConstants
import javax.swing.text.StyledDocument

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

	fun render(viewer: JTextPane, file: File?, sessionId: String?, parsedChatLog: ParsedChatLog?, theme: ChatRenderTheme) {
		val document = viewer.styledDocument
		document.remove(0, document.length)

		appendLine(document, "Codex Chat Viewer", theme.headerStyle)
		appendBlankLine(document, theme)

		if (file == null || parsedChatLog == null) {
			appendLine(document, "Ready.", theme.bodyStyle)
			appendBlankLine(document, theme)
			appendLine(document, "Default theme: ${theme.name}", theme.metadataStyle)
			return
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
				appendLine(document, block.label, blockStyle.labelStyle)
				appendLine(document, block.content, blockStyle.contentStyle)
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
		viewer.caretPosition = 0
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
		appendLine(document, RenderedEntryKind.SYSTEM.label, blockStyle.labelStyle)
		appendLine(document, notice, blockStyle.contentStyle)
	}

	private fun appendBlankLine(document: StyledDocument, theme: ChatRenderTheme) {
		appendLine(document, "", theme.bodyStyle)
	}

	private fun appendLine(document: StyledDocument, text: String, textStyle: ChatTextStyle) {
		val attributes = SimpleAttributeSet().apply {
			StyleConstants.setForeground(this, textStyle.color)
			StyleConstants.setBold(this, textStyle.bold)
			StyleConstants.setFontFamily(this, textStyle.fontFamily)
			StyleConstants.setFontSize(this, textStyle.fontSize)
		}
		document.insertString(document.length, "$text\n", attributes)
	}
}
