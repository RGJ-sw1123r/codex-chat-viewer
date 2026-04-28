package app.codexchatviewer

import java.io.File

object ChatRenderer {
	private const val separator = "========================================================================"

	fun render(file: File, sessionId: String?, parsedChatLog: ParsedChatLog): String {
		return buildString {
			appendLine("Codex Chat Viewer")
			appendLine()
			appendLine("File: ${file.name}")
			appendLine("Path: ${file.absolutePath}")
			appendLine("Session ID: ${sessionId ?: "Not detected"}")
			appendLine()
			appendLine(separator)
			appendLine()

			if (parsedChatLog.entries.isEmpty()) {
				appendLine("No renderable chat messages found in this JSONL file.")
				if (parsedChatLog.observedEventCounts.isNotEmpty()) {
					appendLine()
					appendLine("Observed event types:")
					parsedChatLog.observedEventCounts.entries
						.sortedByDescending { it.value }
						.take(8)
						.forEach { (name, count) ->
							appendLine("- $name: $count")
						}
				}
				appendLine()
			} else {
				val blocks = parsedChatLog.transcriptBlocks()
				blocks.forEachIndexed { index, block ->
					appendLine(block.label)
					appendLine(block.content)
					if (index != blocks.lastIndex) {
						appendLine()
					}
				}
				appendLine()
				appendLine()
			}

			appendLine(separator)
			appendLine()
			appendLine("Parsed candidates: ${parsedChatLog.parsedCandidates}")
			appendLine("Visible entries: ${parsedChatLog.entries.size}")
			appendLine("Ignored lines: ${parsedChatLog.ignoredLines}")
			appendLine("Malformed lines: ${parsedChatLog.malformedLines}")
		}.trimEnd()
	}
}
