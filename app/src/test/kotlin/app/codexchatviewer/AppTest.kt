package app.codexchatviewer

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class AppTest {
	@Test
	fun frameClassIsAvailable() {
		assertNotNull(CodexChatViewerFrame::class.java, "frame class should be available")
	}
}
