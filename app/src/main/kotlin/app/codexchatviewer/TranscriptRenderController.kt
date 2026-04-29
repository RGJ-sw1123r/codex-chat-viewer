package app.codexchatviewer

import java.io.File
import javax.swing.JPanel
import javax.swing.JTextPane

enum class TranscriptRenderMode {
	TEXT,
	COMPONENT
}

data class TranscriptRenderRequest(
	val file: File?,
	val sessionId: String?,
	val parsedChatLog: ParsedChatLog?,
	val theme: ChatRenderTheme,
	val viewportWidth: Int = 900,
	val collapsedBlockIndexes: Set<Int> = emptySet(),
	val resetCaretToTop: Boolean = true,
	val onComponentHeaderClicked: (Int) -> Unit = {}
)

data class TranscriptRenderResult(
	val mode: TranscriptRenderMode,
	val headerRanges: List<TranscriptHeaderRange>,
	val renderedText: String,
	val componentBlockRanges: List<ComponentBlockRange>
)

internal enum class TranscriptRendererPath {
	TERMINAL_TEXT,
	MARKDOWN_COMPONENT,
	CHAT_COMPONENT
}

class TranscriptRenderController(
	private val textViewer: JTextPane,
	private val componentContainer: JPanel
) {
	fun renderModeFor(theme: ChatRenderTheme): TranscriptRenderMode {
		return when (rendererPathFor(theme)) {
			TranscriptRendererPath.TERMINAL_TEXT -> TranscriptRenderMode.TEXT
			TranscriptRendererPath.MARKDOWN_COMPONENT,
			TranscriptRendererPath.CHAT_COMPONENT -> TranscriptRenderMode.COMPONENT
		}
	}

	fun render(request: TranscriptRenderRequest): TranscriptRenderResult {
		return when (rendererPathFor(request.theme)) {
			TranscriptRendererPath.TERMINAL_TEXT -> renderTextTranscript(request)
			TranscriptRendererPath.MARKDOWN_COMPONENT -> renderMarkdownTranscript(request)
			TranscriptRendererPath.CHAT_COMPONENT -> renderChatTranscript(request)
		}
	}

	fun appendSystemNotice(notice: String, theme: ChatRenderTheme, viewportWidth: Int): TranscriptRenderMode {
		return when (rendererPathFor(theme)) {
			TranscriptRendererPath.TERMINAL_TEXT -> {
				ChatStyledRenderer.appendSystemNotice(textViewer, notice, theme)
				TranscriptRenderMode.TEXT
			}

			TranscriptRendererPath.MARKDOWN_COMPONENT -> {
				MarkdownDocumentRenderer.appendSystemNotice(componentContainer, notice, theme, viewportWidth)
				TranscriptRenderMode.COMPONENT
			}

			TranscriptRendererPath.CHAT_COMPONENT -> {
				MessengerChatRenderer.appendSystemNotice(componentContainer, notice, theme, viewportWidth)
				TranscriptRenderMode.COMPONENT
			}
		}
	}

	internal fun rendererPathFor(theme: ChatRenderTheme): TranscriptRendererPath {
		return when (theme.name) {
			ChatRenderThemes.markdownStyle.name -> TranscriptRendererPath.MARKDOWN_COMPONENT
			ChatRenderThemes.dmStyle.name,
			ChatRenderThemes.messengerStyle.name -> TranscriptRendererPath.CHAT_COMPONENT
			else -> TranscriptRendererPath.TERMINAL_TEXT
		}
	}

	private fun renderTextTranscript(request: TranscriptRenderRequest): TranscriptRenderResult {
		val headerRanges = ChatStyledRenderer.render(
			viewer = textViewer,
			file = request.file,
			sessionId = request.sessionId,
			parsedChatLog = request.parsedChatLog,
			theme = request.theme,
			collapsedBlockIndexes = request.collapsedBlockIndexes,
			resetCaretToTop = request.resetCaretToTop
		)
		val document = textViewer.document
		return TranscriptRenderResult(
			mode = TranscriptRenderMode.TEXT,
			headerRanges = headerRanges,
			renderedText = document.getText(0, document.length),
			componentBlockRanges = emptyList()
		)
	}

	private fun renderMarkdownTranscript(request: TranscriptRenderRequest): TranscriptRenderResult {
		return componentTranscriptResult(
			MarkdownDocumentRenderer.render(
				container = componentContainer,
				file = request.file,
				sessionId = request.sessionId,
				parsedChatLog = request.parsedChatLog,
				theme = request.theme,
				viewportWidth = request.viewportWidth,
				collapsedBlockIndexes = request.collapsedBlockIndexes,
				onHeaderClicked = request.onComponentHeaderClicked
			)
		)
	}

	private fun renderChatTranscript(request: TranscriptRenderRequest): TranscriptRenderResult {
		return componentTranscriptResult(
			MessengerChatRenderer.render(
				container = componentContainer,
				file = request.file,
				sessionId = request.sessionId,
				parsedChatLog = request.parsedChatLog,
				theme = request.theme,
				viewportWidth = request.viewportWidth,
				collapsedBlockIndexes = request.collapsedBlockIndexes,
				onHeaderClicked = request.onComponentHeaderClicked
			)
		)
	}

	private fun componentTranscriptResult(result: ComponentRenderResult): TranscriptRenderResult {
		return TranscriptRenderResult(
			mode = TranscriptRenderMode.COMPONENT,
			headerRanges = result.headerRanges,
			renderedText = result.transcriptText,
			componentBlockRanges = result.blockRanges
		)
	}
}
