package app.codexchatviewer

data class TranscriptBlock(
	val type: RenderedEntryKind,
	val label: String,
	val title: String,
	val content: String
)

fun ParsedChatLog.transcriptBlocks(): List<TranscriptBlock> {
	return entries.map { entry ->
		TranscriptBlock(
			type = entry.kind,
			label = entry.kind.label,
			title = entry.kind.label,
			content = entry.content
		)
	}
}
