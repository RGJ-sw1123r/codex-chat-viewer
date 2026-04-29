package app.codexchatviewer

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import javax.swing.JPanel

class MarkdownTranscriptExporterTest {
	@Test
	fun exportsMetadataAndVisibleEntriesOnly() {
		val markdown = MarkdownTranscriptExporter.export(
			sourceFile = File("""C:\logs\rollout-test.jsonl"""),
			sessionId = "session-123",
			chatLog = ParsedChatLog(
				entries = listOf(
					RenderedEntry(RenderedEntryKind.TASK, "Task or prompt instructions loaded"),
					RenderedEntry(RenderedEntryKind.YOU, "Inspect the export output"),
					RenderedEntry(RenderedEntryKind.CODEX, "Using cached filtered entries"),
					RenderedEntry(RenderedEntryKind.TOOL_RESULT, "Exit code: 0")
				),
				parsedCandidates = 4,
				ignoredLines = 0,
				malformedLines = 0,
				observedEventCounts = emptyMap()
			)
		)

		assertTrue(markdown.contains("# Codex Chat Viewer Export"))
		assertTrue(markdown.contains("Source file: rollout-test.jsonl"))
		assertTrue(markdown.contains("""Path: C:\logs\rollout-test.jsonl"""))
		assertTrue(markdown.contains("Session ID: session-123"))
		assertTrue(markdown.contains("```text"))
		assertTrue(markdown.contains("[TASK]\nTask or prompt instructions loaded"))
		assertTrue(markdown.contains("[YOU]\nInspect the export output"))
		assertTrue(markdown.contains("[CODEX]\nUsing cached filtered entries"))
		assertTrue(markdown.contains("[TOOL RESULT]\nExit code: 0"))
		assertFalse(markdown.contains("[TOOL CALL]"))
	}

	@Test
	fun exportsEntriesFromFilteredParsedChatLog() {
		val fullLog = ParsedChatLog(
			entries = listOf(
				RenderedEntry(RenderedEntryKind.YOU, "Keep this"),
				RenderedEntry(RenderedEntryKind.CODEX, "Keep this too"),
				RenderedEntry(RenderedEntryKind.TOOL_CALL, "Do not export this tool call"),
				RenderedEntry(RenderedEntryKind.TOOL_RESULT, "Hide this tool result")
			),
			parsedCandidates = 4,
			ignoredLines = 0,
			malformedLines = 0,
			observedEventCounts = emptyMap()
		)

		val filteredLog = fullLog.filtered(
			ChatEntryFilter(
				showYou = true,
				showCodex = true,
				showToolCall = false,
				showToolResult = false,
				showMeta = false
			)
		)
		val markdown = MarkdownTranscriptExporter.export(
			sourceFile = File("""C:\logs\rollout-filtered.jsonl"""),
			sessionId = "session-filtered",
			chatLog = filteredLog
		)

		assertTrue(markdown.contains("[YOU]\nKeep this"))
		assertTrue(markdown.contains("[CODEX]\nKeep this too"))
		assertFalse(markdown.contains("[TOOL CALL]"))
		assertFalse(markdown.contains("[TOOL RESULT]"))
	}

	@Test
	fun exportUsesFullContentOfProvidedLogWithoutCollapseMarkers() {
		val fullLog = ParsedChatLog(
			entries = listOf(
				RenderedEntry(RenderedEntryKind.YOU, "Full content stays exportable"),
				RenderedEntry(RenderedEntryKind.CODEX, "Assistant content also stays exportable")
			),
			parsedCandidates = 2,
			ignoredLines = 0,
			malformedLines = 0,
			observedEventCounts = emptyMap()
		)
		val collapsedRender = MarkdownDocumentRenderer.render(
			container = JPanel(),
			file = File("rollout-collapsed.jsonl"),
			sessionId = "session-collapse",
			parsedChatLog = fullLog,
			theme = ChatRenderThemes.markdownStyle,
			collapsedBlockIndexes = setOf(0, 1)
		)

		val markdown = MarkdownTranscriptExporter.export(
			sourceFile = File("""C:\logs\rollout-collapsed.jsonl"""),
			sessionId = "session-collapse",
			chatLog = fullLog
		)

		assertTrue(collapsedRender.transcriptText.contains("> [YOU]"))
		assertTrue(collapsedRender.transcriptText.contains("> [CODEX]"))
		assertFalse(collapsedRender.transcriptText.contains("Full content stays exportable"))
		assertTrue(markdown.contains("[YOU]\nFull content stays exportable"))
		assertTrue(markdown.contains("[CODEX]\nAssistant content also stays exportable"))
		assertFalse(markdown.contains("\nv [YOU]"))
		assertFalse(markdown.contains("\n> [YOU]"))
		assertFalse(markdown.contains("\n[v] [YOU]"))
		assertFalse(markdown.contains("\n[>] [YOU]"))
	}
}
