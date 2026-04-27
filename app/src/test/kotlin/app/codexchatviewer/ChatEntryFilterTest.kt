package app.codexchatviewer

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ChatEntryFilterTest {
	@Test
	fun parsedEntriesCanBeFilteredWithoutReparsing() {
		val parsed = sampleParsedChatLog()

		val filtered = parsed.filtered(ChatEntryFilter(showYou = false))

		assertEquals(parsed.parsedCandidates, filtered.parsedCandidates)
		assertEquals(parsed.ignoredLines, filtered.ignoredLines)
		assertEquals(parsed.malformedLines, filtered.malformedLines)
		assertEquals(7, parsed.entries.size)
		assertEquals(6, filtered.entries.size)
	}

	@Test
	fun turningOffYouHidesYouEntries() {
		val filtered = sampleParsedChatLog().filtered(ChatEntryFilter(showYou = false))

		assertTrue(filtered.entries.none { it.kind == RenderedEntryKind.YOU })
		assertTrue(filtered.entries.any { it.kind == RenderedEntryKind.CODEX })
	}

	@Test
	fun turningOffCodexHidesCodexEntries() {
		val filtered = sampleParsedChatLog().filtered(ChatEntryFilter(showCodex = false))

		assertTrue(filtered.entries.none { it.kind == RenderedEntryKind.CODEX })
		assertTrue(filtered.entries.any { it.kind == RenderedEntryKind.YOU })
	}

	@Test
	fun turningOffToolCallHidesToolCallEntries() {
		val filtered = sampleParsedChatLog().filtered(ChatEntryFilter(showToolCall = false))

		assertTrue(filtered.entries.none { it.kind == RenderedEntryKind.TOOL_CALL })
		assertTrue(filtered.entries.any { it.kind == RenderedEntryKind.TOOL_RESULT })
	}

	@Test
	fun turningOffToolResultHidesToolResultEntries() {
		val filtered = sampleParsedChatLog().filtered(ChatEntryFilter(showToolResult = false))

		assertTrue(filtered.entries.none { it.kind == RenderedEntryKind.TOOL_RESULT })
		assertTrue(filtered.entries.any { it.kind == RenderedEntryKind.TOOL_CALL })
	}

	@Test
	fun turningOffMetaHidesSystemAndContextEntries() {
		val filtered = sampleParsedChatLog().filtered(ChatEntryFilter(showMeta = false))

		assertTrue(filtered.entries.none {
			it.kind == RenderedEntryKind.SYSTEM ||
				it.kind == RenderedEntryKind.CONTEXT ||
				it.kind == RenderedEntryKind.TASK
		})
		assertTrue(filtered.entries.any { it.kind == RenderedEntryKind.YOU || it.kind == RenderedEntryKind.CODEX })
	}

	private fun sampleParsedChatLog(): ParsedChatLog {
		return ParsedChatLog(
			entries = listOf(
				RenderedEntry(RenderedEntryKind.CONTEXT, "AGENTS.md project instructions loaded"),
				RenderedEntry(RenderedEntryKind.TASK, "Task or prompt instructions loaded"),
				RenderedEntry(RenderedEntryKind.YOU, "안녕하세요"),
				RenderedEntry(RenderedEntryKind.CODEX, "Café 확인 완료 🙂"),
				RenderedEntry(RenderedEntryKind.TOOL_CALL, "shell_command: Get-ChildItem -Force"),
				RenderedEntry(RenderedEntryKind.TOOL_RESULT, "Exit code: 0"),
				RenderedEntry(RenderedEntryKind.SYSTEM, "Session: test")
			),
			parsedCandidates = 7,
			ignoredLines = 2,
			malformedLines = 1,
			observedEventCounts = mapOf("event_msg/user_message" to 1)
		)
	}
}
