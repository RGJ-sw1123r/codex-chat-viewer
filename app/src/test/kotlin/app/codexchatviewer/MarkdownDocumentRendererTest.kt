package app.codexchatviewer

import java.awt.Component
import java.awt.Container
import java.awt.event.MouseEvent
import java.io.File
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextArea
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MarkdownDocumentRendererTest {
	@Test
	fun rendersMarkdownBlocksAsDocumentSections() {
		val panel = JPanel()
		val result = MarkdownDocumentRenderer.render(
			container = panel,
			file = File("rollout-test.jsonl"),
			sessionId = "session-123",
			parsedChatLog = sampleLog(),
			theme = ChatRenderThemes.markdownStyle,
			viewportWidth = 1200
		)

		val youSection = requireComponent(panel, "markdown-section-YOU")
		val codexSection = requireComponent(panel, "markdown-section-CODEX")
		val toolSection = requireComponent(panel, "markdown-section-TOOL_RESULT")
		val youContent = requireComponent(panel, "markdown-content-YOU") as JTextArea
		val toolContent = requireComponent(panel, "markdown-content-TOOL_RESULT") as JTextArea

		assertEquals(ChatRenderThemes.markdownStyle.backgroundColor, panel.background)
		assertEquals(ChatRenderThemes.markdownStyle.blockStyleFor(RenderedEntryKind.YOU).contentStyle.backgroundColor, youSection.background)
		assertEquals(ChatRenderThemes.markdownStyle.blockStyleFor(RenderedEntryKind.CODEX).contentStyle.backgroundColor, codexSection.background)
		assertEquals(ChatRenderThemes.markdownStyle.blockStyleFor(RenderedEntryKind.TOOL_RESULT).contentStyle.backgroundColor, toolSection.background)
		assertEquals("User request", youContent.text)
		assertTrue(youContent.lineWrap)
		assertTrue(toolContent.font.family.contains("Monospaced") || toolContent.font.family.contains("Courier"))
		assertTrue(result.transcriptText.contains("v [YOU]\nUser request"))
		assertEquals(3, result.blockRanges.size)
	}

	@Test
	fun clickingMarkdownHeaderInvokesCollapseCallback() {
		val panel = JPanel()
		var clickedBlockIndex: Int? = null
		MarkdownDocumentRenderer.render(
			container = panel,
			file = File("rollout-test.jsonl"),
			sessionId = null,
			parsedChatLog = sampleLog(),
			theme = ChatRenderThemes.markdownStyle,
			onHeaderClicked = { clickedBlockIndex = it }
		)

		val header = requireComponent(panel, "markdown-header-YOU") as JLabel
		header.mouseListeners.forEach { listener ->
			listener.mouseClicked(
				MouseEvent(header, MouseEvent.MOUSE_CLICKED, 0, 0, 1, 1, 1, false, MouseEvent.BUTTON1)
			)
		}

		assertEquals(0, clickedBlockIndex)
	}

	@Test
	fun longMarkdownContentExpandsSectionHeightAfterWrapping() {
		val longCommand = (1..24).joinToString(" ") { "very-long-command-segment-$it" }
		val panel = JPanel()
		MarkdownDocumentRenderer.render(
			container = panel,
			file = File("rollout-test.jsonl"),
			sessionId = "session-123",
			parsedChatLog = ParsedChatLog(
				entries = listOf(
					RenderedEntry(RenderedEntryKind.CODEX, (1..12).joinToString(" ") { "paragraph-token-$it" }),
					RenderedEntry(RenderedEntryKind.TOOL_CALL, longCommand)
				),
				parsedCandidates = 2,
				ignoredLines = 0,
				malformedLines = 0,
				observedEventCounts = emptyMap()
			),
			theme = ChatRenderThemes.markdownStyle,
			viewportWidth = 420
		)

		val codexContent = requireComponent(panel, "markdown-content-CODEX") as JTextArea
		val toolContent = requireComponent(panel, "markdown-content-TOOL_CALL") as JTextArea
		val toolSection = requireComponent(panel, "markdown-section-TOOL_CALL")

		assertTrue(codexContent.lineWrap)
		assertTrue(toolContent.lineWrap)
		assertTrue(codexContent.preferredSize.height > codexContent.getFontMetrics(codexContent.font).height)
		assertTrue(toolContent.preferredSize.height > toolContent.getFontMetrics(toolContent.font).height)
		assertTrue(toolSection.preferredSize.height >= toolContent.preferredSize.height)
	}

	private fun sampleLog(): ParsedChatLog {
		return ParsedChatLog(
			entries = listOf(
				RenderedEntry(RenderedEntryKind.YOU, "User request"),
				RenderedEntry(RenderedEntryKind.CODEX, "Assistant response"),
				RenderedEntry(RenderedEntryKind.TOOL_RESULT, "tool output")
			),
			parsedCandidates = 3,
			ignoredLines = 0,
			malformedLines = 0,
			observedEventCounts = emptyMap()
		)
	}

	private fun requireComponent(root: Component, name: String): JComponent {
		val component = findComponent(root, name)
		assertNotNull(component, "component named $name should exist")
		return component as JComponent
	}

	private fun findComponent(root: Component, name: String): Component? {
		if (root.name == name) {
			return root
		}
		if (root is Container) {
			root.components.forEach { child ->
				findComponent(child, name)?.let { return it }
			}
		}
		return null
	}
}
