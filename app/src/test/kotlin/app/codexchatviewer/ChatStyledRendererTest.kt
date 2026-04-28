package app.codexchatviewer

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import javax.swing.JTextPane

class ChatStyledRendererTest {
	@Test
	fun rendersTranscriptBlocksExpandedByDefaultWithThemeMarkers() {
		val viewer = JTextPane()
		val headerRanges = ChatStyledRenderer.render(
			viewer = viewer,
			file = File("rollout-test.jsonl"),
			sessionId = null,
			parsedChatLog = sampleLog(),
			theme = ChatRenderThemes.terminalStyle
		)

		val rendered = viewer.document.getText(0, viewer.document.length)

		assertEquals(2, headerRanges.size)
		assertTrue(rendered.contains("[v] [YOU]\nVisible user content"))
		assertTrue(rendered.contains("[v] [CODEX]\nVisible assistant content"))
		assertFalse(rendered.contains("[>]"))
		assertEquals("[v] [YOU]".length, headerRanges.first().endOffset - headerRanges.first().startOffset)
	}

	@Test
	fun collapsedTranscriptBlockHidesAllContentAndKeepsHeaderOnly() {
		val viewer = JTextPane()
		ChatStyledRenderer.render(
			viewer = viewer,
			file = File("rollout-test.jsonl"),
			sessionId = null,
			parsedChatLog = sampleLog(),
			theme = ChatRenderThemes.terminalStyle,
			collapsedBlockIndexes = setOf(0)
		)

		val rendered = viewer.document.getText(0, viewer.document.length)

		assertTrue(rendered.contains("[>] [YOU]\n\n[v] [CODEX]"))
		assertFalse(rendered.contains("Visible user content"))
		assertTrue(rendered.contains("Visible assistant content"))
	}

	@Test
	fun headerRangeExcludesTrailingNewlineAndContentOffsets() {
		val viewer = JTextPane()
		val headerRanges = ChatStyledRenderer.render(
			viewer = viewer,
			file = File("rollout-test.jsonl"),
			sessionId = null,
			parsedChatLog = sampleLog(),
			theme = ChatRenderThemes.terminalStyle
		)

		val rendered = viewer.document.getText(0, viewer.document.length)
		val firstHeader = headerRanges.first()

		assertEquals("[v] [YOU]", rendered.substring(firstHeader.startOffset, firstHeader.endOffset))
		assertEquals('\n', rendered[firstHeader.endOffset])
		assertTrue(rendered.indexOf("Visible user content") > firstHeader.endOffset)
	}

	private fun sampleLog(): ParsedChatLog {
		return ParsedChatLog(
			entries = listOf(
				RenderedEntry(RenderedEntryKind.YOU, "Visible user content"),
				RenderedEntry(RenderedEntryKind.CODEX, "Visible assistant content")
			),
			parsedCandidates = 2,
			ignoredLines = 0,
			malformedLines = 0,
			observedEventCounts = emptyMap()
		)
	}
}
