package app.codexchatviewer

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class TranscriptSearchControllerTest {
	@Test
	fun updateQueryBuildsMatchesAndSelectsFirstMatch() {
		val controller = TranscriptSearchController()

		val state = controller.updateQuery("app", "test app\nTitle text\napp")

		assertEquals("app", state.query)
		assertEquals(listOf(SearchMatch(5, 8), SearchMatch(20, 23)), state.matches)
		assertEquals(0, state.currentIndex)
		assertEquals(SearchMatch(5, 8), state.currentMatch())
	}

	@Test
	fun refreshMatchesPreservesCurrentIndexWhenPossible() {
		val controller = TranscriptSearchController()
		controller.updateQuery("app", "app one\napp two\napp three")
		controller.moveToNextMatch()

		val state = controller.refreshMatches("app one\napp two", preserveCurrentIndex = true)

		assertEquals(1, state.currentIndex)
		assertEquals(SearchMatch(8, 11), state.currentMatch())
	}

	@Test
	fun refreshMatchesResetsToFirstMatchWhenNotPreservingIndex() {
		val controller = TranscriptSearchController()
		controller.updateQuery("app", "app one\napp two\napp three")
		controller.moveToNextMatch()

		val state = controller.refreshMatches("app one\napp two", preserveCurrentIndex = false)

		assertEquals(0, state.currentIndex)
		assertEquals(SearchMatch(0, 3), state.currentMatch())
	}

	@Test
	fun nextAndPreviousNavigationWrapAroundMatches() {
		val controller = TranscriptSearchController()
		controller.updateQuery("app", "app one\napp two")

		val second = controller.moveToNextMatch()
		val wrapped = controller.moveToNextMatch()
		val previous = controller.moveToPreviousMatch()

		assertEquals(1, second.currentIndex)
		assertEquals(0, wrapped.currentIndex)
		assertEquals(1, previous.currentIndex)
	}

	@Test
	fun clearResetsQueryMatchesAndSelection() {
		val controller = TranscriptSearchController()
		controller.updateQuery("app", "app one\napp two")

		val state = controller.clear()

		assertEquals("", state.query)
		assertEquals(emptyList<SearchMatch>(), state.matches)
		assertEquals(-1, state.currentIndex)
		assertNull(state.currentMatch())
	}

	@Test
	fun blankQueryProducesEmptySearchState() {
		val controller = TranscriptSearchController()

		val state = controller.updateQuery("   ", "app one\napp two")

		assertEquals("   ", state.query)
		assertEquals(emptyList<SearchMatch>(), state.matches)
		assertEquals(-1, state.currentIndex)
	}
}
