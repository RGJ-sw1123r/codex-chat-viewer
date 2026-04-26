package app.codexchatviewer

import java.awt.Color
import java.awt.Component
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import javax.swing.Icon
import javax.swing.JToggleButton

internal abstract class BaseFilterIcon : Icon {
	protected val selectedColor = Color(230, 230, 230)
	protected val unselectedColor = Color(118, 118, 118)
	protected val accentColor = Color(74, 144, 226)

	final override fun paintIcon(component: Component, graphics: Graphics, x: Int, y: Int) {
		val button = component as? JToggleButton
		val g2 = graphics.create() as Graphics2D
		try {
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
			g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)
			g2.translate(x, y)

			if (button?.isSelected == true) {
				g2.color = accentColor
				g2.fillRoundRect(1, 1, iconWidth - 2, iconHeight - 2, 8, 8)
			}

			g2.color = if (button?.isSelected == true) selectedColor else unselectedColor
			paintGlyph(g2)
		} finally {
			g2.dispose()
		}
	}

	override fun getIconWidth(): Int = 20

	override fun getIconHeight(): Int = 16

	protected abstract fun paintGlyph(g2: Graphics2D)
}

internal class PersonFilterIcon : BaseFilterIcon() {
	override fun paintGlyph(g2: Graphics2D) {
		g2.fillOval(7, 1, 6, 6)
		g2.fillRoundRect(5, 8, 10, 6, 5, 5)
	}
}

internal class RobotFilterIcon : BaseFilterIcon() {
	override fun paintGlyph(g2: Graphics2D) {
		g2.drawRoundRect(4, 3, 12, 8, 3, 3)
		g2.fillOval(7, 6, 2, 2)
		g2.fillOval(11, 6, 2, 2)
		g2.drawLine(10, 1, 10, 3)
		g2.fillOval(9, 0, 2, 2)
		g2.drawLine(6, 12, 6, 14)
		g2.drawLine(14, 12, 14, 14)
	}
}

internal class TerminalFilterIcon : BaseFilterIcon() {
	override fun paintGlyph(g2: Graphics2D) {
		g2.drawRoundRect(2, 2, 16, 12, 3, 3)
		g2.drawLine(5, 6, 8, 8)
		g2.drawLine(5, 10, 8, 8)
		g2.drawLine(10, 10, 14, 10)
	}
}

internal class DocumentFilterIcon : BaseFilterIcon() {
	override fun paintGlyph(g2: Graphics2D) {
		g2.drawRoundRect(4, 1, 10, 14, 2, 2)
		g2.drawLine(14, 1, 16, 3)
		g2.drawLine(14, 1, 14, 3)
		g2.drawLine(8, 5, 12, 5)
		g2.drawLine(6, 8, 12, 8)
		g2.drawLine(6, 11, 12, 11)
	}
}

internal class MetaFilterIcon : BaseFilterIcon() {
	override fun paintGlyph(g2: Graphics2D) {
		g2.drawOval(5, 1, 10, 10)
		g2.fillOval(9, 4, 2, 2)
		g2.drawLine(10, 7, 10, 10)
		g2.drawLine(4, 13, 16, 13)
	}
}
