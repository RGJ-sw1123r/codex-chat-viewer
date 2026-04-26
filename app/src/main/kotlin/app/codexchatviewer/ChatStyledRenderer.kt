package app.codexchatviewer

import java.awt.Color
import java.awt.Font
import java.io.File
import javax.swing.JTextPane
import javax.swing.text.SimpleAttributeSet
import javax.swing.text.StyleConstants
import javax.swing.text.StyledDocument

object ChatStyledRenderer {
	private const val separator = "========================================================================"

	private val backgroundColor = Color(20, 20, 20)
	private val headerColor = Color(180, 180, 180)
	private val metadataColor = Color(150, 150, 150)
	private val defaultTextColor = Color(230, 230, 230)
	private val youColor = Color(255, 196, 107)
	private val codexColor = Color(120, 200, 255)
	private val contextColor = Color(144, 196, 164)
	private val taskColor = Color(196, 168, 255)
	private val toolCallColor = Color(166, 201, 132)
	private val toolResultColor = Color(180, 180, 180)
	private val systemColor = Color(135, 135, 135)

	fun configure(viewer: JTextPane) {
		viewer.font = Font(Font.MONOSPACED, Font.PLAIN, 14)
		viewer.isEditable = false
		viewer.background = backgroundColor
		viewer.foreground = defaultTextColor
	}

	fun render(viewer: JTextPane, file: File?, sessionId: String?, parsedChatLog: ParsedChatLog?) {
		val document = viewer.styledDocument
		document.remove(0, document.length)

		appendLine(document, "Codex Chat Viewer", headerColor, bold = true)
		appendBlankLine(document)

		if (file == null || parsedChatLog == null) {
			appendLine(document, "Ready.", defaultTextColor)
			appendBlankLine(document)
			appendLine(document, "Default theme: Terminal Style", metadataColor)
			return
		}

		appendLine(document, "File: ${file.name}", metadataColor)
		appendLine(document, "Path: ${file.absolutePath}", metadataColor)
		appendLine(document, "Session ID: ${sessionId ?: "Not detected"}", metadataColor)
		appendBlankLine(document)
		appendLine(document, separator, systemColor)
		appendBlankLine(document)

		if (parsedChatLog.entries.isEmpty()) {
			appendLine(document, "No renderable chat messages found in this JSONL file.", defaultTextColor)
			if (parsedChatLog.observedEventCounts.isNotEmpty()) {
				appendBlankLine(document)
				appendLine(document, "Observed event types:", metadataColor)
				parsedChatLog.observedEventCounts.entries
					.sortedByDescending { it.value }
					.take(8)
					.forEach { (name, count) ->
						appendLine(document, "- $name: $count", metadataColor)
					}
			}
			appendBlankLine(document)
		} else {
			parsedChatLog.entries.forEachIndexed { index, entry ->
				appendLine(document, entry.kind.label, colorFor(entry.kind), bold = true)
				appendLine(document, entry.content, defaultTextColor)
				if (index != parsedChatLog.entries.lastIndex) {
					appendBlankLine(document)
				}
			}
			appendBlankLine(document)
			appendBlankLine(document)
		}

		appendLine(document, separator, systemColor)
		appendBlankLine(document)
		appendLine(document, "Parsed candidates: ${parsedChatLog.parsedCandidates}", metadataColor)
		appendLine(document, "Visible entries: ${parsedChatLog.entries.size}", metadataColor)
		appendLine(document, "Ignored lines: ${parsedChatLog.ignoredLines}", metadataColor)
		appendLine(document, "Malformed lines: ${parsedChatLog.malformedLines}", metadataColor)
		viewer.caretPosition = 0
	}

	fun appendSystemNotice(viewer: JTextPane, notice: String) {
		val document = viewer.styledDocument
		if (document.length > 0) {
			appendBlankLine(document)
		}
		appendLine(document, RenderedEntryKind.SYSTEM.label, colorFor(RenderedEntryKind.SYSTEM), bold = true)
		appendLine(document, notice, defaultTextColor)
	}

	private fun colorFor(kind: RenderedEntryKind): Color {
		return when (kind) {
			RenderedEntryKind.CONTEXT -> contextColor
			RenderedEntryKind.TASK -> taskColor
			RenderedEntryKind.YOU -> youColor
			RenderedEntryKind.CODEX -> codexColor
			RenderedEntryKind.TOOL_CALL -> toolCallColor
			RenderedEntryKind.TOOL_RESULT -> toolResultColor
			RenderedEntryKind.SYSTEM -> systemColor
		}
	}

	private fun appendBlankLine(document: StyledDocument) {
		appendLine(document, "", defaultTextColor)
	}

	private fun appendLine(document: StyledDocument, text: String, color: Color, bold: Boolean = false) {
		val style = SimpleAttributeSet().apply {
			StyleConstants.setForeground(this, color)
			StyleConstants.setBold(this, bold)
			StyleConstants.setFontFamily(this, Font.MONOSPACED)
			StyleConstants.setFontSize(this, 14)
		}
		document.insertString(document.length, "$text\n", style)
	}
}
