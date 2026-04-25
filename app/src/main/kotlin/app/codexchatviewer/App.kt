package app.codexchatviewer

import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Font
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTextArea
import javax.swing.SwingUtilities

fun main() {
	SwingUtilities.invokeLater {
		CodexChatViewerFrame().isVisible = true
	}
}

class CodexChatViewerFrame : JFrame("Codex Chat Viewer") {
	private val themeComboBox = JComboBox(
		arrayOf(
			"Terminal Style",
			"Markdown Style",
			"DM Style",
			"Talk Style",
			"Messenger Style"
		)
	)

	private val chatArea = JTextArea()

	init {
		defaultCloseOperation = EXIT_ON_CLOSE
		minimumSize = Dimension(900, 650)
		setLocationRelativeTo(null)

		layout = BorderLayout()

		add(createHeaderPanel(), BorderLayout.NORTH)
		add(createChatPanel(), BorderLayout.CENTER)

		pack()
	}

	private fun createHeaderPanel(): JPanel {
		val panel = JPanel()

		val openButton = JButton("Open JSONL")
		val exportButton = JButton("Export Markdown")

		openButton.addActionListener {
			chatArea.text = """
				[Codex Chat Viewer]
				
				Open JSONL clicked.
				
				Next step:
				- Select rollout-*.jsonl
				- Parse user / assistant messages
				- Render as chat
			""".trimIndent()
		}

		exportButton.addActionListener {
			chatArea.append("\n\nExport Markdown clicked.")
		}

		panel.add(JLabel("Theme:"))
		panel.add(themeComboBox)
		panel.add(openButton)
		panel.add(exportButton)

		return panel
	}

	private fun createChatPanel(): JScrollPane {
		chatArea.font = Font("Consolas", Font.PLAIN, 14)
		chatArea.lineWrap = true
		chatArea.wrapStyleWord = true
		chatArea.isEditable = false
		chatArea.text = """
			Codex Chat Viewer
			
			Ready.
			
			Default theme: Terminal Style
		""".trimIndent()

		return JScrollPane(chatArea)
	}
}