package app.codexchatviewer

import java.io.File

object MarkdownTranscriptExporter {
	fun export(
		sourceFile: File,
		sessionId: String?,
		chatLog: ParsedChatLog
	): String {
		val transcript = chatLog.transcriptBlocks().joinToString("\n\n") { block ->
			"${block.label}\n${block.content.trim()}"
		}.trim()
		val fence = selectFence(transcript)
		val metadataLines = buildList {
			add("# Codex Chat Viewer Export")
			add("")
			add("Source file: ${sourceFile.name}  ")
			add("Path: ${sourceFile.absolutePath}  ")
			sessionId?.takeIf { it.isNotBlank() }?.let { add("Session ID: $it") }
			add("")
			add("${fence}text")
			if (transcript.isNotEmpty()) {
				add(transcript)
			}
			add(fence)
		}

		return metadataLines.joinToString("\n")
	}

	private fun selectFence(transcript: String): String {
		val longestRun = Regex("`+").findAll(transcript)
			.maxOfOrNull { it.value.length }
			?: 2
		return "`".repeat(maxOf(3, longestRun + 1))
	}
}
