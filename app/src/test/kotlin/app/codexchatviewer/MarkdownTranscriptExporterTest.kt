package app.codexchatviewer

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

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
}
