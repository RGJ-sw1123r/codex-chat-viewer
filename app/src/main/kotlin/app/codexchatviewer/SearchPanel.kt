package app.codexchatviewer

import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.event.ActionEvent
import java.awt.event.KeyEvent
import javax.swing.AbstractAction
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.Icon
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextField
import javax.swing.KeyStroke
import javax.swing.UIManager
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

class SearchPanel(
	onQueryChanged: (String) -> Unit,
	onPrevious: () -> Unit,
	onNext: () -> Unit,
	private val onClose: () -> Unit
) : JPanel(BorderLayout(8, 0)) {
	private val searchField = JTextField(24)
	private val previousButton = JButton(PreviousMatchIcon())
	private val nextButton = JButton(NextMatchIcon())
	private val matchCountLabel = JLabel("")
	private var suppressQueryEvents = false

	init {
		border = BorderFactory.createEmptyBorder(0, 8, 8, 8)
		isVisible = false

		val controlsPanel = JPanel(FlowLayout(FlowLayout.LEFT, 6, 1))
		controlsPanel.isOpaque = false
		isOpaque = false

		searchField.document.addDocumentListener(object : DocumentListener {
			override fun insertUpdate(event: DocumentEvent) = notifyQueryChanged()
			override fun removeUpdate(event: DocumentEvent) = notifyQueryChanged()
			override fun changedUpdate(event: DocumentEvent) = notifyQueryChanged()

			private fun notifyQueryChanged() {
				if (!suppressQueryEvents) {
					onQueryChanged(searchField.text)
				}
			}
		})

		styleSearchField()
		styleIconButton(previousButton, "Previous match")
		styleIconButton(nextButton, "Next match")
		styleMatchLabel()

		searchField.inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "search.next")
		searchField.actionMap.put("search.next", object : AbstractAction() {
			override fun actionPerformed(event: ActionEvent?) {
				onNext()
			}
		})
		searchField.inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, KeyEvent.SHIFT_DOWN_MASK), "search.previous")
		searchField.actionMap.put("search.previous", object : AbstractAction() {
			override fun actionPerformed(event: ActionEvent?) {
				onPrevious()
			}
		})

		previousButton.addActionListener { onPrevious() }
		nextButton.addActionListener { onNext() }

		installEscapeBinding()

		controlsPanel.add(JLabel("Find:"))
		controlsPanel.add(searchField)
		controlsPanel.add(previousButton)
		controlsPanel.add(nextButton)
		controlsPanel.add(matchCountLabel)

		add(controlsPanel, BorderLayout.WEST)
	}

	fun openSearch() {
		isVisible = true
		focusSearchField()
	}

	fun closeSearch() {
		clearQueryText()
		if (isVisible) {
			isVisible = false
		}
		onClose()
	}

	fun focusSearchField() {
		searchField.requestFocusInWindow()
		searchField.selectAll()
	}

	fun updateMatchCount(currentIndex: Int?, totalMatches: Int?) {
		matchCountLabel.text = when {
			totalMatches == null -> ""
			totalMatches == 0 -> "0 / 0"
			currentIndex == null -> "0 / $totalMatches"
			else -> "${currentIndex + 1} / $totalMatches"
		}
	}

	fun reset() {
		clearQueryText()
		isVisible = false
	}

	private fun clearQueryText() {
		suppressQueryEvents = true
		searchField.text = ""
		suppressQueryEvents = false
		updateMatchCount(null, null)
	}

	private fun styleSearchField() {
		searchField.font = Font(Font.MONOSPACED, Font.PLAIN, 13)
		searchField.margin = java.awt.Insets(2, 8, 2, 8)
		searchField.preferredSize = Dimension(260, 24)
		searchField.minimumSize = Dimension(160, 24)
		searchField.maximumSize = Dimension(Int.MAX_VALUE, 24)
	}

	private fun styleIconButton(button: JButton, tooltip: String) {
		button.toolTipText = tooltip
		button.margin = java.awt.Insets(0, 0, 0, 0)
		button.preferredSize = Dimension(28, 24)
		button.minimumSize = Dimension(28, 24)
		button.maximumSize = Dimension(28, 24)
		button.isFocusPainted = false
		button.isBorderPainted = true
		button.background = UIManager.getColor("Button.background") ?: Color(240, 240, 240)
		button.foreground = UIManager.getColor("Button.foreground") ?: Color(60, 60, 60)
		button.border = BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(Color(184, 184, 184)),
			BorderFactory.createEmptyBorder(2, 2, 2, 2)
		)
	}

	private fun styleMatchLabel() {
		matchCountLabel.font = Font(Font.SANS_SERIF, Font.PLAIN, 12)
		matchCountLabel.border = BorderFactory.createEmptyBorder(0, 4, 0, 4)
	}

	private fun installEscapeBinding() {
		getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "search.close")
		actionMap.put("search.close", object : AbstractAction() {
			override fun actionPerformed(event: ActionEvent?) {
				closeSearch()
			}
		})
	}
}

private abstract class SearchArrowIcon : Icon {
	private val glyphColor = Color(96, 96, 96)

	override fun getIconWidth(): Int = 20

	override fun getIconHeight(): Int = 16

	final override fun paintIcon(component: Component, graphics: Graphics, x: Int, y: Int) {
		val g2 = graphics.create() as Graphics2D
		try {
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
			g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)
			g2.translate(x, y)
			g2.color = glyphColor
			paintGlyph(g2)
		} finally {
			g2.dispose()
		}
	}

	protected abstract fun paintGlyph(g2: Graphics2D)
}

private class PreviousMatchIcon : SearchArrowIcon() {
	override fun paintGlyph(g2: Graphics2D) {
		g2.drawLine(13, 4, 7, 8)
		g2.drawLine(7, 8, 13, 12)
		g2.drawLine(7, 8, 15, 8)
	}
}

private class NextMatchIcon : SearchArrowIcon() {
	override fun paintGlyph(g2: Graphics2D) {
		g2.drawLine(7, 4, 13, 8)
		g2.drawLine(13, 8, 7, 12)
		g2.drawLine(5, 8, 13, 8)
	}
}
