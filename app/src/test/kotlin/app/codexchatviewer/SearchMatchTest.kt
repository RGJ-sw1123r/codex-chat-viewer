package app.codexchatviewer

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SearchMatchTest {
	@Test
	fun searchRangesPointToExactKoreanMatches() {
		val text = "검색 테스트\n다시 검색"

		val matches = findCaseInsensitiveMatches(text, "검색")

		assertEquals(listOf("검색", "검색"), matches.map { text.substring(it.start, it.end) })
	}

	@Test
	fun searchRangesPointToExactAsciiMatches() {
		val text = "test app\nTitle text\napp"

		val tMatches = findCaseInsensitiveMatches(text, "t")
		val appMatches = findCaseInsensitiveMatches(text, "app")

		assertTrue(tMatches.all { text.substring(it.start, it.end) == "t" || text.substring(it.start, it.end) == "T" })
		assertEquals(listOf("app", "app"), appMatches.map { text.substring(it.start, it.end) })
	}

	@Test
	fun caseInsensitiveSearchKeepsOriginalOffsetsWhenCaseMappingCanExpand() {
		val text = "İstanbul test app"

		val matches = findCaseInsensitiveMatches(text, "t")

		assertEquals(listOf("t", "t", "t"), matches.map { text.substring(it.start, it.end).lowercase() })
	}
}
