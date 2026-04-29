package app.codexchatviewer

import java.awt.Rectangle
import java.awt.geom.Rectangle2D
import javax.swing.DefaultBoundedRangeModel
import javax.swing.JComponent
import javax.swing.JScrollBar
import javax.swing.JScrollPane
import javax.swing.JTextPane
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import kotlin.math.roundToInt

class TranscriptScrollControllerTest {
	@Test
	fun captureAndRestoreViewportAnchorUseScrollRatio() {
		val textPane = JTextPane()
		val scrollPane = FakeScrollPane(textPane)
		val controller = TranscriptScrollController(scrollPane, textPane)
		scrollPane.verticalScrollBar.model = DefaultBoundedRangeModel(120, 80, 0, 400)
		val initialScrollBar = scrollPane.verticalScrollBar
		val initialMaxY = (initialScrollBar.maximum - initialScrollBar.visibleAmount).coerceAtLeast(0)
		val expectedRatio = if (initialMaxY > 0) {
			initialScrollBar.value.toDouble() / initialMaxY.toDouble()
		} else {
			0.0
		}

		val anchor = controller.captureViewportAnchor()

		scrollPane.verticalScrollBar.model = DefaultBoundedRangeModel(0, 80, 0, 400)
		val restoredScrollBar = scrollPane.verticalScrollBar
		val restoredMaxY = (restoredScrollBar.maximum - restoredScrollBar.visibleAmount).coerceAtLeast(0)
		val expectedRestoredValue = if (restoredMaxY > 0) {
			(anchor.scrollRatio * restoredMaxY.toDouble()).roundToInt().coerceIn(0, restoredMaxY)
		} else {
			0
		}
		controller.restoreViewportAnchor(anchor)

		assertEquals(expectedRatio, anchor.scrollRatio, 0.0001)
		assertEquals(expectedRestoredValue, scrollPane.verticalScrollBar.value)
	}

	@Test
	fun scrollToTopResetsVerticalScrollBar() {
		val textPane = JTextPane()
		val scrollPane = FakeScrollPane(textPane)
		val controller = TranscriptScrollController(scrollPane, textPane)
		scrollPane.verticalScrollBar.model = DefaultBoundedRangeModel(200, 80, 0, 500)

		controller.scrollToTop()

		assertEquals(0, scrollPane.verticalScrollBar.value)
	}

	@Test
	fun textBlockAnchorCaptureAndRestorePreserveViewportOffset() {
		val textPane = FakeTextPane()
		val scrollPane = JScrollPane(textPane)
		val controller = TranscriptScrollController(scrollPane, textPane)
		val headerRange = TranscriptHeaderRange(blockIndex = 3, startOffset = 42, endOffset = 55)
		textPane.boundsByOffset[42] = Rectangle(0, 180, 200, 20)
		textPane.setFakeVisibleRect(Rectangle(0, 120, 300, 100))
		textPane.setFakeHeight(900)

		val anchor = controller.captureTextBlockAnchor(headerRange)

		assertNotNull(anchor)
		assertEquals(3, anchor?.blockIndex)
		assertEquals(60, anchor?.viewportYOffset)

		textPane.boundsByOffset[42] = Rectangle(0, 260, 200, 20)
		controller.restoreTextBlockAnchor(anchor!!, listOf(headerRange))

		assertEquals(Rectangle(0, 200, 300, 100), textPane.lastScrolledRect)
	}

	@Test
	fun scrollComponentAndTextOffsetUseTargetBounds() {
		val textPane = FakeTextPane()
		val scrollPane = JScrollPane(textPane)
		val controller = TranscriptScrollController(scrollPane, textPane)
		val component = FakeComponent().apply { setSize(320, 140) }
		textPane.text = "0123456789abcdefghijklmnop"
		textPane.boundsByOffset[15] = Rectangle(8, 144, 120, 18)

		controller.scrollComponentIntoView(component)
		controller.scrollTextOffsetIntoView(15)

		assertEquals(Rectangle(0, 0, 320, 140), component.lastScrolledRect)
		assertEquals(15, textPane.caretPosition)
		assertEquals(Rectangle(8, 144, 120, 18), textPane.lastScrolledRect)
	}
}

private class FakeTextPane : JTextPane() {
	val boundsByOffset = linkedMapOf<Int, Rectangle>()
	var lastScrolledRect: Rectangle? = null
	private var fakeHeightValue: Int = 0
	private var fakeVisibleRectangle: Rectangle = Rectangle()

	override fun modelToView2D(pos: Int): Rectangle2D? {
		return boundsByOffset[pos]
	}

	override fun scrollRectToVisible(aRect: Rectangle?) {
		lastScrolledRect = aRect?.let { Rectangle(it) }
	}

	override fun getVisibleRect(): Rectangle {
		return Rectangle(fakeVisibleRectangle)
	}

	override fun getHeight(): Int {
		return fakeHeightValue
	}

	fun setFakeVisibleRect(value: Rectangle) {
		fakeVisibleRectangle = Rectangle(value)
	}

	fun setFakeHeight(value: Int) {
		fakeHeightValue = value
	}
}

private class FakeScrollPane(view: JTextPane) : JScrollPane(view) {
	private val fakeVerticalScrollBar = JScrollBar().apply {
		model = DefaultBoundedRangeModel(0, 0, 0, 0)
	}

	override fun getVerticalScrollBar(): JScrollBar {
		return fakeVerticalScrollBar
	}
}

private class FakeComponent : JComponent() {
	var lastScrolledRect: Rectangle? = null

	override fun scrollRectToVisible(aRect: Rectangle?) {
		lastScrolledRect = aRect?.let { Rectangle(it) }
	}
}
