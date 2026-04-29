package app.codexchatviewer

import java.awt.Component
import java.awt.Container
import java.io.File
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTextPane
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TranscriptRenderControllerTest {
	@Test
	fun renderModeAndRendererPathFollowThemeFamilies() {
		val controller = controller()

		assertEquals(TranscriptRenderMode.TEXT, controller.renderModeFor(ChatRenderThemes.terminalStyle))
		assertEquals(TranscriptRendererPath.TERMINAL_TEXT, controller.rendererPathFor(ChatRenderThemes.terminalStyle))
		assertEquals(TranscriptRenderMode.COMPONENT, controller.renderModeFor(ChatRenderThemes.markdownStyle))
		assertEquals(TranscriptRendererPath.MARKDOWN_COMPONENT, controller.rendererPathFor(ChatRenderThemes.markdownStyle))
		assertEquals(TranscriptRenderMode.COMPONENT, controller.renderModeFor(ChatRenderThemes.dmStyle))
		assertEquals(TranscriptRendererPath.CHAT_COMPONENT, controller.rendererPathFor(ChatRenderThemes.dmStyle))
		assertEquals(TranscriptRenderMode.COMPONENT, controller.renderModeFor(ChatRenderThemes.messengerStyle))
		assertEquals(TranscriptRendererPath.CHAT_COMPONENT, controller.rendererPathFor(ChatRenderThemes.messengerStyle))
	}

	@Test
	fun renderUsesTerminalTextRendererForTerminalStyle() {
		val viewer = JTextPane()
		ChatStyledRenderer.configure(viewer)
		val controller = TranscriptRenderController(viewer, JPanel())

		val result = controller.render(
			TranscriptRenderRequest(
				file = File("rollout-terminal.jsonl"),
				sessionId = "session-terminal",
				parsedChatLog = sampleLog(),
				theme = ChatRenderThemes.terminalStyle
			)
		)

		assertEquals(TranscriptRenderMode.TEXT, result.mode)
		assertTrue(result.renderedText.contains("[YOU]"))
		assertTrue(result.renderedText.contains("User request"))
		assertTrue(result.componentBlockRanges.isEmpty())
		assertTrue(viewer.document.length > 0)
	}

	@Test
	fun renderUsesMarkdownComponentRendererForMarkdownStyle() {
		val panel = JPanel()
		val controller = TranscriptRenderController(JTextPane(), panel)

		val result = controller.render(
			TranscriptRenderRequest(
				file = File("rollout-markdown.jsonl"),
				sessionId = "session-markdown",
				parsedChatLog = sampleLog(),
				theme = ChatRenderThemes.markdownStyle,
				viewportWidth = 900
			)
		)

		assertEquals(TranscriptRenderMode.COMPONENT, result.mode)
		assertTrue(result.renderedText.contains("v [CODEX]\nAssistant response"))
		assertEquals(2, result.componentBlockRanges.size)
		assertNotNull(requireComponent(panel, "markdown-content-CODEX"))
	}

	@Test
	fun renderUsesChatComponentRendererForDmStyle() {
		val panel = JPanel()
		val controller = TranscriptRenderController(JTextPane(), panel)

		val result = controller.render(
			TranscriptRenderRequest(
				file = File("rollout-dm.jsonl"),
				sessionId = "session-dm",
				parsedChatLog = sampleLog(),
				theme = ChatRenderThemes.dmStyle,
				viewportWidth = 900
			)
		)

		assertEquals(TranscriptRenderMode.COMPONENT, result.mode)
		assertTrue(result.renderedText.contains("v [YOU]\nUser request"))
		assertEquals(2, result.componentBlockRanges.size)
		assertNotNull(requireComponent(panel, "messenger-content-YOU"))
	}

	private fun controller(): TranscriptRenderController {
		return TranscriptRenderController(JTextPane(), JPanel())
	}

	private fun sampleLog(): ParsedChatLog {
		return ParsedChatLog(
			entries = listOf(
				RenderedEntry(RenderedEntryKind.YOU, "User request"),
				RenderedEntry(RenderedEntryKind.CODEX, "Assistant response")
			),
			parsedCandidates = 2,
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
