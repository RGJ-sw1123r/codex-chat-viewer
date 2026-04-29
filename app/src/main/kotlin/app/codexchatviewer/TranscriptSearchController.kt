package app.codexchatviewer

data class SearchState(
	val query: String = "",
	val matches: List<SearchMatch> = emptyList(),
	val currentIndex: Int = -1
) {
	fun currentMatch(): SearchMatch? = matches.getOrNull(currentIndex)
}

data class SearchMatch(
	val start: Int,
	val end: Int
)

class TranscriptSearchController {
	private var state = SearchState()

	fun currentState(): SearchState {
		return state
	}

	fun updateQuery(query: String, text: String): SearchState {
		state = recalculatedState(
			query = query,
			text = text,
			preserveCurrentIndex = false
		)
		return state
	}

	fun refreshMatches(text: String, preserveCurrentIndex: Boolean): SearchState {
		state = recalculatedState(
			query = state.query,
			text = text,
			preserveCurrentIndex = preserveCurrentIndex
		)
		return state
	}

	fun moveToNextMatch(): SearchState {
		return moveSelection(direction = 1)
	}

	fun moveToPreviousMatch(): SearchState {
		return moveSelection(direction = -1)
	}

	fun clear(): SearchState {
		state = SearchState()
		return state
	}

	private fun moveSelection(direction: Int): SearchState {
		if (state.matches.isEmpty()) {
			return state
		}

		state = state.copy(
			currentIndex = when {
				state.currentIndex < 0 -> 0
				direction > 0 -> (state.currentIndex + 1) % state.matches.size
				else -> (state.currentIndex - 1 + state.matches.size) % state.matches.size
			}
		)
		return state
	}

	private fun recalculatedState(query: String, text: String, preserveCurrentIndex: Boolean): SearchState {
		if (query.isBlank()) {
			return SearchState(query = query)
		}

		val matches = findCaseInsensitiveMatches(text, query)
		if (matches.isEmpty()) {
			return SearchState(
				query = query,
				matches = emptyList(),
				currentIndex = -1
			)
		}

		val currentIndex = if (preserveCurrentIndex && state.currentIndex >= 0) {
			state.currentIndex.coerceAtMost(matches.lastIndex)
		} else {
			0
		}

		return SearchState(
			query = query,
			matches = matches,
			currentIndex = currentIndex
		)
	}
}

internal fun findCaseInsensitiveMatches(text: String, query: String): List<SearchMatch> {
	if (query.isBlank() || query.length > text.length) {
		return emptyList()
	}

	val matches = mutableListOf<SearchMatch>()
	var startIndex = 0
	while (startIndex <= text.length - query.length) {
		var matchIndex = -1
		var candidateIndex = startIndex
		while (candidateIndex <= text.length - query.length) {
			if (text.regionMatches(candidateIndex, query, 0, query.length, ignoreCase = true)) {
				matchIndex = candidateIndex
				break
			}
			candidateIndex += 1
		}
		if (matchIndex < 0) {
			break
		}

		matches += SearchMatch(matchIndex, matchIndex + query.length)
		startIndex = matchIndex + query.length
	}
	return matches
}
