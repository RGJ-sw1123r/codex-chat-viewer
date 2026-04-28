package app.codexchatviewer

import java.awt.Color
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

class ChatRenderThemeTest {
	@Test
	fun terminalStyleProvidesStylesForEveryRenderedEntryKind() {
		val theme = ChatRenderThemes.terminalStyle

		assertEquals("Terminal Style", theme.name)
		assertEquals(RenderedEntryKind.entries.toSet(), theme.blockStyles.keys)
	}

	@Test
	fun availableThemeNamesExcludeTalkStyleAndIncludeMessengerStyle() {
		assertFalse(ChatRenderThemes.availableThemeNames.contains("Talk Style"))
		assertEquals(
			listOf("Terminal Style", "Markdown Style", "DM Style", "Messenger Style"),
			ChatRenderThemes.availableThemeNames
		)
	}

	@Test
	fun messengerStyleProvidesStylesForEveryRenderedEntryKind() {
		val theme = ChatRenderThemes.messengerStyle

		assertEquals("Messenger Style", theme.name)
		assertEquals(RenderedEntryKind.entries.toSet(), theme.blockStyles.keys)
		assertEquals(ChatBlockAlignment.RIGHT, theme.blockStyleFor(RenderedEntryKind.YOU).alignment)
		assertEquals(ChatBlockAlignment.LEFT, theme.blockStyleFor(RenderedEntryKind.CODEX).alignment)
		assertEquals(Color(242, 235, 221), theme.backgroundColor)
		assertEquals(ChatBlockAlignment.CENTER, theme.blockStyleFor(RenderedEntryKind.SYSTEM).alignment)
		assertEquals(ChatBlockAlignment.CENTER, theme.blockStyleFor(RenderedEntryKind.CONTEXT).alignment)
		assertEquals(ChatBlockAlignment.CENTER, theme.blockStyleFor(RenderedEntryKind.TASK).alignment)
		assertEquals(theme.blockStyleFor(RenderedEntryKind.SYSTEM).leftIndent, theme.blockStyleFor(RenderedEntryKind.SYSTEM).rightIndent)
		assertEquals(2, theme.blockStyleFor(RenderedEntryKind.SYSTEM).horizontalPadding)
		assertEquals(2, theme.blockStyleFor(RenderedEntryKind.YOU).horizontalPadding)
		assertEquals(2, theme.blockStyleFor(RenderedEntryKind.CODEX).horizontalPadding)
	}

	@Test
	fun markdownStyleProvidesDocumentStylesForEveryRenderedEntryKind() {
		val theme = ChatRenderThemes.markdownStyle

		assertEquals("Markdown Style", theme.name)
		assertEquals(RenderedEntryKind.entries.toSet(), theme.blockStyles.keys)
		assertEquals(Color(247, 248, 250), theme.backgroundColor)
		assertEquals(ChatBlockAlignment.LEFT, theme.blockStyleFor(RenderedEntryKind.YOU).alignment)
		assertEquals(ChatBlockAlignment.LEFT, theme.blockStyleFor(RenderedEntryKind.CODEX).alignment)
		assertNotEquals(ChatRenderThemes.terminalStyle.backgroundColor, theme.backgroundColor)
		assertNotEquals(
			ChatRenderThemes.messengerStyle.blockStyleFor(RenderedEntryKind.YOU).alignment,
			theme.blockStyleFor(RenderedEntryKind.YOU).alignment
		)
	}

	@Test
	fun dmStyleProvidesStylesForEveryRenderedEntryKind() {
		val theme = ChatRenderThemes.dmStyle

		assertEquals("DM Style", theme.name)
		assertEquals(RenderedEntryKind.entries.toSet(), theme.blockStyles.keys)
		assertEquals(Color(255, 255, 255), theme.backgroundColor)
		assertEquals(ChatBlockAlignment.RIGHT, theme.blockStyleFor(RenderedEntryKind.YOU).alignment)
		assertEquals(ChatBlockAlignment.LEFT, theme.blockStyleFor(RenderedEntryKind.CODEX).alignment)
		assertEquals(ChatBlockAlignment.CENTER, theme.blockStyleFor(RenderedEntryKind.SYSTEM).alignment)
		assertNotEquals(
			theme.backgroundColor,
			theme.blockStyleFor(RenderedEntryKind.CODEX).contentStyle.backgroundColor
		)
	}
}
