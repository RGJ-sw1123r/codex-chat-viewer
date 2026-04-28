package app.codexchatviewer

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import javax.swing.JTextPane
import javax.swing.text.StyleConstants

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

	@Test
	fun messengerStyleAppliesChatRoomAlignmentAndBubbleColors() {
		val viewer = JTextPane()
		ChatStyledRenderer.render(
			viewer = viewer,
			file = File("rollout-test.jsonl"),
			sessionId = null,
			parsedChatLog = ParsedChatLog(
				entries = listOf(
					RenderedEntry(RenderedEntryKind.YOU, "User bubble\nsecond line"),
					RenderedEntry(RenderedEntryKind.CODEX, "Codex bubble"),
					RenderedEntry(RenderedEntryKind.SYSTEM, "System note")
				),
				parsedCandidates = 3,
				ignoredLines = 0,
				malformedLines = 0,
				observedEventCounts = emptyMap()
			),
			theme = ChatRenderThemes.messengerStyle
		)

		val rendered = viewer.document.getText(0, viewer.document.length)
		val youOffset = rendered.indexOf("v [YOU]")
		val codexOffset = rendered.indexOf("v [CODEX]")
		val systemOffset = rendered.indexOf("v [SYSTEM]")
		val document = viewer.styledDocument

		assertEquals(ChatRenderThemes.messengerStyle.backgroundColor, viewer.background)
		assertEquals(StyleConstants.ALIGN_RIGHT, StyleConstants.getAlignment(document.getParagraphElement(youOffset).attributes))
		assertEquals(StyleConstants.ALIGN_LEFT, StyleConstants.getAlignment(document.getParagraphElement(codexOffset).attributes))
		assertEquals(StyleConstants.ALIGN_CENTER, StyleConstants.getAlignment(document.getParagraphElement(systemOffset).attributes))
		assertEquals(
			ChatRenderThemes.messengerStyle.blockStyleFor(RenderedEntryKind.SYSTEM).leftIndent,
			StyleConstants.getLeftIndent(document.getParagraphElement(systemOffset).attributes)
		)
		assertTrue(rendered.contains("User bubble".padEnd(ChatRenderThemes.messengerStyle.blockStyleFor(RenderedEntryKind.YOU).minimumCardWidth)))
		assertTrue(rendered.contains("second line".padEnd(ChatRenderThemes.messengerStyle.blockStyleFor(RenderedEntryKind.YOU).minimumCardWidth)))
		assertTrue(rendered.contains("System note".padEnd(ChatRenderThemes.messengerStyle.blockStyleFor(RenderedEntryKind.SYSTEM).minimumCardWidth)))
		assertEquals(
			ChatRenderThemes.messengerStyle.blockStyleFor(RenderedEntryKind.YOU).labelStyle.backgroundColor,
			StyleConstants.getBackground(document.getCharacterElement(youOffset).attributes)
		)
		assertEquals(
			ChatRenderThemes.messengerStyle.blockStyleFor(RenderedEntryKind.CODEX).labelStyle.backgroundColor,
			StyleConstants.getBackground(document.getCharacterElement(codexOffset).attributes)
		)
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
