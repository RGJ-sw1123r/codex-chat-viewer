package app.codexchatviewer

import java.awt.BorderLayout
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
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MessengerChatRendererTest {
	@Test
	fun rendersMessengerBlocksAsComponentsWithChatAlignment() {
		val panel = JPanel()
		val result = MessengerChatRenderer.render(
			container = panel,
			file = File("rollout-test.jsonl"),
			sessionId = "session-123",
			parsedChatLog = sampleLog(),
			theme = ChatRenderThemes.messengerStyle
		)

		val youRow = requireComponent(panel, "messenger-row-YOU") as JPanel
		val codexRow = requireComponent(panel, "messenger-row-CODEX") as JPanel
		val systemRow = requireComponent(panel, "messenger-row-SYSTEM") as JPanel
		val youBubble = requireComponent(panel, "messenger-bubble-YOU")
		val codexBubble = requireComponent(panel, "messenger-bubble-CODEX")
		val systemBubble = requireComponent(panel, "messenger-bubble-SYSTEM")
		val youContent = requireComponent(panel, "messenger-content-YOU") as JTextArea

		assertEquals(ChatRenderThemes.messengerStyle.backgroundColor, panel.background)
		assertSame(youBubble, (youRow.layout as BorderLayout).getLayoutComponent(BorderLayout.EAST))
		assertSame(codexBubble, (codexRow.layout as BorderLayout).getLayoutComponent(BorderLayout.WEST))
		assertSame(systemBubble.parent, (systemRow.layout as BorderLayout).getLayoutComponent(BorderLayout.CENTER))
		assertEquals(500, youBubble.maximumSize.width)
		assertEquals(500, codexBubble.maximumSize.width)
		assertEquals(600, systemBubble.maximumSize.width)
		assertEquals("Line one\nLine two", youContent.text)
		assertEquals(59, youContent.columns)
		assertTrue(youContent.lineWrap)
		assertTrue(result.transcriptText.contains("v [YOU]\nLine one\nLine two"))
		assertEquals(3, result.blockRanges.size)
	}

	@Test
	fun adjustsBubbleWidthsFromViewportWidth() {
		val narrowPanel = JPanel()
		MessengerChatRenderer.render(
			container = narrowPanel,
			file = File("rollout-test.jsonl"),
			sessionId = "session-123",
			parsedChatLog = sampleLog(),
			theme = ChatRenderThemes.messengerStyle,
			viewportWidth = 700
		)

		val widePanel = JPanel()
		MessengerChatRenderer.render(
			container = widePanel,
			file = File("rollout-test.jsonl"),
			sessionId = "session-123",
			parsedChatLog = sampleLog(),
			theme = ChatRenderThemes.messengerStyle,
			viewportWidth = 1400
		)

		val narrowYouBubble = requireComponent(narrowPanel, "messenger-bubble-YOU")
		val wideYouBubble = requireComponent(widePanel, "messenger-bubble-YOU")
		val narrowSystemBubble = requireComponent(narrowPanel, "messenger-bubble-SYSTEM")
		val wideSystemBubble = requireComponent(widePanel, "messenger-bubble-SYSTEM")

		assertEquals(500, narrowYouBubble.maximumSize.width)
		assertEquals(644, wideYouBubble.maximumSize.width)
		assertEquals(600, narrowSystemBubble.maximumSize.width)
		assertEquals(756, wideSystemBubble.maximumSize.width)
	}

	@Test
	fun shrinksBubbleWidthsBelowMinimumWhenViewportIsTooNarrow() {
		val panel = JPanel()
		MessengerChatRenderer.render(
			container = panel,
			file = File("rollout-test.jsonl"),
			sessionId = "session-123",
			parsedChatLog = ParsedChatLog(
				entries = listOf(
					RenderedEntry(RenderedEntryKind.YOU, "User message"),
					RenderedEntry(RenderedEntryKind.TOOL_RESULT, "Tool result")
				),
				parsedCandidates = 2,
				ignoredLines = 0,
				malformedLines = 0,
				observedEventCounts = emptyMap()
			),
			theme = ChatRenderThemes.messengerStyle,
			viewportWidth = 300
		)

		val youBubble = requireComponent(panel, "messenger-bubble-YOU")
		val toolBubble = requireComponent(panel, "messenger-bubble-TOOL_RESULT")

		assertEquals(244, youBubble.maximumSize.width)
		assertEquals(244, youBubble.preferredSize.width)
		assertEquals(244, toolBubble.maximumSize.width)
		assertEquals(244, toolBubble.preferredSize.width)
	}

	@Test
	fun clickingMessengerHeaderInvokesCollapseCallback() {
		val panel = JPanel()
		var clickedBlockIndex: Int? = null
		MessengerChatRenderer.render(
			container = panel,
			file = File("rollout-test.jsonl"),
			sessionId = null,
			parsedChatLog = sampleLog(),
			theme = ChatRenderThemes.messengerStyle,
			onHeaderClicked = { clickedBlockIndex = it }
		)

		val header = requireComponent(panel, "messenger-header-YOU") as JLabel
		header.mouseListeners.forEach { listener ->
			listener.mouseClicked(
				MouseEvent(header, MouseEvent.MOUSE_CLICKED, 0, 0, 1, 1, 1, false, MouseEvent.BUTTON1)
			)
		}

		assertEquals(0, clickedBlockIndex)
	}

	private fun sampleLog(): ParsedChatLog {
		return ParsedChatLog(
			entries = listOf(
				RenderedEntry(RenderedEntryKind.YOU, "Line one\nLine two"),
				RenderedEntry(RenderedEntryKind.CODEX, "Assistant reply"),
				RenderedEntry(RenderedEntryKind.SYSTEM, "System note")
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
