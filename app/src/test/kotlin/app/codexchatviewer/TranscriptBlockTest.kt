package app.codexchatviewer

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TranscriptBlockTest {
	@Test
	fun transcriptBlocksPreserveEntryTypeLabelAndContent() {
		val log = ParsedChatLog(
			entries = listOf(
				RenderedEntry(RenderedEntryKind.YOU, "Inspect transcript conversion"),
				RenderedEntry(RenderedEntryKind.TOOL_CALL, "rg --files"),
				RenderedEntry(RenderedEntryKind.TOOL_RESULT, "app/src/main/kotlin/app/codexchatviewer/App.kt")
			),
			parsedCandidates = 3,
			ignoredLines = 0,
			malformedLines = 0,
			observedEventCounts = emptyMap()
		)

		val blocks = log.transcriptBlocks()

		assertEquals(3, blocks.size)
		assertEquals(RenderedEntryKind.YOU, blocks[0].type)
		assertEquals(RenderedEntryKind.YOU.label, blocks[0].label)
		assertEquals("Inspect transcript conversion", blocks[0].content)
		assertEquals(RenderedEntryKind.TOOL_CALL, blocks[1].type)
		assertEquals(RenderedEntryKind.TOOL_CALL.label, blocks[1].label)
		assertEquals("rg --files", blocks[1].content)
		assertEquals(RenderedEntryKind.TOOL_RESULT, blocks[2].type)
		assertEquals(RenderedEntryKind.TOOL_RESULT.label, blocks[2].label)
		assertEquals("app/src/main/kotlin/app/codexchatviewer/App.kt", blocks[2].content)
	}
}
