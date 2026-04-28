package app.codexchatviewer

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ChatRenderThemeTest {
	@Test
	fun terminalStyleProvidesStylesForEveryRenderedEntryKind() {
		val theme = ChatRenderThemes.terminalStyle

		assertEquals("Terminal Style", theme.name)
		assertEquals(RenderedEntryKind.entries.toSet(), theme.blockStyles.keys)
	}
}
