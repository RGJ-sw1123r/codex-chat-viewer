package app.codexchatviewer

import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.Font
import java.awt.FlowLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.File
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JFileChooser
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTextArea
import javax.swing.SwingUtilities
import javax.swing.filechooser.FileNameExtensionFilter

fun main() {
	SwingUtilities.invokeLater {
		CodexChatViewerFrame().isVisible = true
	}
}

class CodexChatViewerFrame : JFrame("Codex Chat Viewer") {
	private val sessionIdPattern =
		Regex("\\b[0-9a-fA-F]{8}\\-[0-9a-fA-F]{4}\\-[0-9a-fA-F]{4}\\-[0-9a-fA-F]{4}\\-[0-9a-fA-F]{12}\\b")

	private var lastSelectedDirectory: File? = null

	private val themeComboBox = JComboBox(
		arrayOf(
			"Terminal Style",
			"Markdown Style",
			"DM Style",
			"Talk Style",
			"Messenger Style"
		)
	)

	private val selectedFileLabel = JLabel("Selected File: None")
	private val selectedPathLabel = JLabel("Path: Not selected")
	private val sessionIdLabel = JLabel("Session ID: Not detected")
	private val resumeCommandLabel = JLabel("Resume Command: Not available")
	private val copyResumeCommandButton = JButton("Copy Resume Command")
	private val chatArea = JTextArea()

	init {
		defaultCloseOperation = EXIT_ON_CLOSE
		minimumSize = Dimension(900, 650)
		setLocationRelativeTo(null)

		layout = BorderLayout()

		add(createTopPanel(), BorderLayout.NORTH)
		add(createChatPanel(), BorderLayout.CENTER)
		updateSelection(null)

		pack()
	}

	private fun createTopPanel(): JPanel {
		val panel = JPanel(BorderLayout())
		panel.add(createHeaderPanel(), BorderLayout.NORTH)
		panel.add(createMetadataPanel(), BorderLayout.CENTER)
		return panel
	}

	private fun createHeaderPanel(): JPanel {
		val panel = JPanel()
		panel.layout = FlowLayout(FlowLayout.LEFT, 8, 8)

		val openButton = JButton("Open JSONL")
		val exportButton = JButton("Export Markdown")

		openButton.addActionListener {
			openJsonlFile()
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

	private fun createMetadataPanel(): JPanel {
		val panel = JPanel(BorderLayout(12, 0))
		panel.border = BorderFactory.createCompoundBorder(
			BorderFactory.createEmptyBorder(0, 8, 4, 8),
			BorderFactory.createTitledBorder("Selection")
		)

		val detailsPanel = JPanel(GridBagLayout())

		copyResumeCommandButton.isEnabled = false
		copyResumeCommandButton.margin = Insets(2, 8, 2, 8)
		copyResumeCommandButton.addActionListener {
			val command = copyResumeCommandButton.toolTipText
			if (!command.isNullOrBlank()) {
				val selection = StringSelection(command)
				Toolkit.getDefaultToolkit().systemClipboard.setContents(selection, selection)
			}
		}

		val fullWidthConstraints = GridBagConstraints().apply {
			gridx = 0
			weightx = 1.0
			fill = GridBagConstraints.HORIZONTAL
			anchor = GridBagConstraints.WEST
			insets = Insets(0, 0, 2, 0)
		}

		fullWidthConstraints.gridy = 0
		detailsPanel.add(selectedFileLabel, fullWidthConstraints)

		fullWidthConstraints.gridy = 1
		detailsPanel.add(selectedPathLabel, fullWidthConstraints)

		fullWidthConstraints.gridy = 2
		detailsPanel.add(sessionIdLabel, fullWidthConstraints)

		val resumeConstraints = GridBagConstraints().apply {
			gridx = 0
			gridy = 3
			weightx = 1.0
			fill = GridBagConstraints.HORIZONTAL
			anchor = GridBagConstraints.WEST
			insets = Insets(0, 0, 0, 8)
		}
		detailsPanel.add(resumeCommandLabel, resumeConstraints)

		val buttonConstraints = GridBagConstraints().apply {
			gridx = 1
			gridy = 3
			weightx = 0.0
			fill = GridBagConstraints.NONE
			anchor = GridBagConstraints.EAST
			insets = Insets(0, 0, 0, 0)
		}
		detailsPanel.add(copyResumeCommandButton, buttonConstraints)

		panel.add(detailsPanel, BorderLayout.CENTER)

		return panel
	}

	private fun createChatPanel(): JScrollPane {
		chatArea.font = Font("Consolas", Font.PLAIN, 14)
		chatArea.lineWrap = true
		chatArea.wrapStyleWord = true
		chatArea.isEditable = false
		chatArea.background = Color(20, 20, 20)
		chatArea.foreground = Color(230, 230, 230)
		chatArea.text = """
			Codex Chat Viewer
			
			Ready.
			
			Default theme: Terminal Style
		""".trimIndent()

		return JScrollPane(chatArea)
	}

	private fun openJsonlFile() {
		val chooser = JFileChooser(resolveInitialDirectory())
		chooser.dialogTitle = "Open Codex JSONL File"
		chooser.fileFilter = FileNameExtensionFilter("JSONL files (*.jsonl)", "jsonl")
		chooser.setAcceptAllFileFilterUsed(true)

		val result = chooser.showOpenDialog(this)
		if (result != JFileChooser.APPROVE_OPTION) {
			return
		}

		val selectedFile = chooser.selectedFile ?: return
		lastSelectedDirectory = selectedFile.parentFile
		updateSelection(selectedFile)
	}

	private fun resolveInitialDirectory(): File {
		lastSelectedDirectory?.let { rememberedDirectory ->
			if (rememberedDirectory.isDirectory) {
				return rememberedDirectory
			}
		}

		val codexHome = System.getenv("CODEX_HOME")
			?.takeIf { it.isNotBlank() }
			?.let(::File)
		val userHome = System.getProperty("user.home")
			?.takeIf { it.isNotBlank() }
			?.let(::File)

		val candidates = listOfNotNull(
			codexHome?.resolve("sessions"),
			userHome?.resolve(".codex")?.resolve("sessions"),
			userHome
		)

		return candidates.firstOrNull { it.isDirectory } ?: File(".").absoluteFile
	}

	private fun updateSelection(selectedFile: File?) {
		if (selectedFile == null) {
			updateMetadataLabel(selectedFileLabel, "Selected File", "None")
			updateMetadataLabel(selectedPathLabel, "Path", "Not selected")
			updateMetadataLabel(sessionIdLabel, "Session ID", "Not detected")
			updateMetadataLabel(resumeCommandLabel, "Resume Command", "Not available")
			copyResumeCommandButton.isEnabled = false
			copyResumeCommandButton.toolTipText = null
			return
		}

		val sessionId = detectSessionId(selectedFile)
		val resumeCommand = sessionId?.let { "codex resume $it" }

		updateMetadataLabel(selectedFileLabel, "Selected File", selectedFile.name)
		updateMetadataLabel(selectedPathLabel, "Path", selectedFile.absolutePath)
		updateMetadataLabel(sessionIdLabel, "Session ID", sessionId ?: "Not detected")
		updateMetadataLabel(resumeCommandLabel, "Resume Command", resumeCommand ?: "Not available")
		copyResumeCommandButton.isEnabled = resumeCommand != null
		copyResumeCommandButton.toolTipText = resumeCommand

		chatArea.text = buildString {
			appendLine("Codex Chat Viewer")
			appendLine()
			appendLine("Selected JSONL file:")
			appendLine(selectedFile.absolutePath)
			appendLine()
			appendLine("Parsing and chat rendering are not implemented yet.")
			if (resumeCommand != null) {
				appendLine()
				appendLine("Resume command:")
				appendLine(resumeCommand)
			}
		}.trimEnd()
	}

	private fun detectSessionId(selectedFile: File): String? {
		val candidates = buildList {
			add(selectedFile.nameWithoutExtension)
			generateSequence(selectedFile.parentFile) { it.parentFile }
				.map { it.name }
				.forEach(::add)
		}

		return candidates.firstNotNullOfOrNull { candidate ->
			sessionIdPattern.find(candidate)?.value
		}
	}

	private fun updateMetadataLabel(label: JLabel, prefix: String, value: String) {
		label.text = "$prefix: ${truncateForLabel(value)}"
		label.toolTipText = "$prefix: $value"
	}

	private fun truncateForLabel(value: String, maxLength: Int = 100): String {
		if (value.length <= maxLength) {
			return value
		}

		val visibleCharacters = maxLength - 3
		return value.take(visibleCharacters.coerceAtLeast(0)) + "..."
	}
}
