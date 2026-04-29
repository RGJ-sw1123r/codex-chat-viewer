package app.codexchatviewer

import java.awt.Rectangle
import javax.swing.JComponent
import javax.swing.JScrollPane
import javax.swing.JTextPane
import javax.swing.text.JTextComponent
import kotlin.math.roundToInt

data class TranscriptScrollAnchor(
	val blockIndex: Int,
	val viewportYOffset: Int
)

data class TranscriptViewportAnchor(
	val scrollRatio: Double
)

class TranscriptScrollController(
	private val scrollPane: JScrollPane,
	private val textViewer: JTextPane
) {
	fun captureViewportAnchor(): TranscriptViewportAnchor {
		val scrollBar = scrollPane.verticalScrollBar
		val maxY = (scrollBar.maximum - scrollBar.visibleAmount).coerceAtLeast(0)
		val scrollRatio = if (maxY > 0) {
			scrollBar.value.toDouble() / maxY.toDouble()
		} else {
			0.0
		}
		return TranscriptViewportAnchor(scrollRatio = scrollRatio)
	}

	fun restoreViewportAnchor(anchor: TranscriptViewportAnchor) {
		val scrollBar = scrollPane.verticalScrollBar
		val maxY = (scrollBar.maximum - scrollBar.visibleAmount).coerceAtLeast(0)
		scrollBar.value = if (maxY > 0) {
			(anchor.scrollRatio * maxY.toDouble()).roundToInt().coerceIn(0, maxY)
		} else {
			0
		}
	}

	fun scrollToTop() {
		scrollPane.verticalScrollBar.value = 0
	}

	fun captureTextBlockAnchor(headerRange: TranscriptHeaderRange): TranscriptScrollAnchor? {
		val headerBounds = textViewer.modelToView2D(headerRange.startOffset)?.bounds ?: return null
		return TranscriptScrollAnchor(
			blockIndex = headerRange.blockIndex,
			viewportYOffset = headerBounds.y - textViewer.visibleRect.y
		)
	}

	fun restoreTextBlockAnchor(anchor: TranscriptScrollAnchor, headerRanges: List<TranscriptHeaderRange>) {
		val headerRange = headerRanges.firstOrNull { it.blockIndex == anchor.blockIndex } ?: return
		val headerBounds = textViewer.modelToView2D(headerRange.startOffset)?.bounds ?: return
		val viewport = textViewer.visibleRect
		viewport.y = (headerBounds.y - anchor.viewportYOffset)
			.coerceIn(0, (textViewer.height - viewport.height).coerceAtLeast(0))
		textViewer.scrollRectToVisible(viewport)
	}

	fun scrollComponentIntoView(component: JComponent) {
		component.scrollRectToVisible(Rectangle(0, 0, component.width, component.height))
	}

	fun scrollTextOffsetIntoView(offset: Int) {
		textViewer.caretPosition = offset
		val bounds = textViewer.modelToView2D(offset)?.bounds ?: return
		textViewer.scrollRectToVisible(bounds)
	}

	fun scrollTextComponentOffsetIntoView(textComponent: JTextComponent, offset: Int) {
		val safeOffset = offset.coerceIn(0, textComponent.document.length)
		textComponent.caretPosition = safeOffset
		val bounds = textComponent.modelToView2D(safeOffset)?.bounds ?: return
		textComponent.scrollRectToVisible(bounds)
	}
}
