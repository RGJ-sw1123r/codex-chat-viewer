package app.codexchatviewer

import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Font
import java.awt.FlowLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import java.awt.RenderingHints
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.File
import javax.swing.BorderFactory
import javax.swing.Icon
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JFileChooser
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTextPane
import javax.swing.JToggleButton
import javax.swing.SwingUtilities
import javax.swing.ScrollPaneConstants
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
	private var currentSelectedFile: File? = null
	private var currentSessionId: String? = null
	private var currentParsedChatLog: ParsedChatLog? = null

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
	private val chatArea = WrappedTextPane()
	private val showYouToggle = createFilterToggle("YOU", PersonFilterIcon())
	private val showCodexToggle = createFilterToggle("CODEX", RobotFilterIcon())
	private val showToolCallToggle = createFilterToggle("TOOL CALL", TerminalFilterIcon())
	private val showToolResultToggle = createFilterToggle("TOOL RESULT", DocumentFilterIcon())
	private val showMetaToggle = createFilterToggle("META", MetaFilterIcon())

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
		val panel = JPanel(BorderLayout())
		panel.border = BorderFactory.createEmptyBorder(8, 8, 8, 8)

		val openButton = JButton("Open JSONL")
		val exportButton = JButton("Export Markdown")
		val leftPanel = JPanel(FlowLayout(FlowLayout.LEFT, 8, 0))
		val rightPanel = JPanel(FlowLayout(FlowLayout.RIGHT, 6, 0))

		openButton.addActionListener {
			openJsonlFile()
		}

		exportButton.addActionListener {
			ChatStyledRenderer.appendSystemNotice(chatArea, "Export Markdown clicked.")
		}

		leftPanel.add(JLabel("Theme:"))
		leftPanel.add(themeComboBox)
		leftPanel.add(openButton)
		leftPanel.add(exportButton)

		rightPanel.add(showYouToggle)
		rightPanel.add(showCodexToggle)
		rightPanel.add(showToolCallToggle)
		rightPanel.add(showToolResultToggle)
		rightPanel.add(showMetaToggle)

		panel.add(leftPanel, BorderLayout.WEST)
		panel.add(rightPanel, BorderLayout.EAST)

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
		ChatStyledRenderer.configure(chatArea)
		ChatStyledRenderer.render(chatArea, null, null, null)

		return JScrollPane(chatArea).apply {
			horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
			verticalScrollBarPolicy = ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED
		}
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
			currentSelectedFile = null
			currentSessionId = null
			currentParsedChatLog = null
			updateMetadataLabel(selectedFileLabel, "Selected File", "None")
			updateMetadataLabel(selectedPathLabel, "Path", "Not selected")
			updateMetadataLabel(sessionIdLabel, "Session ID", "Not detected")
			updateMetadataLabel(resumeCommandLabel, "Resume Command", "Not available")
			copyResumeCommandButton.isEnabled = false
			copyResumeCommandButton.toolTipText = null
			ChatStyledRenderer.render(chatArea, null, null, null)
			return
		}

		val sessionId = detectSessionId(selectedFile)
		val resumeCommand = sessionId?.let { "codex resume $it" }
		val parsedChatLog = JsonlChatParser.parse(selectedFile)
		currentSelectedFile = selectedFile
		currentSessionId = sessionId
		currentParsedChatLog = parsedChatLog

		updateMetadataLabel(selectedFileLabel, "Selected File", selectedFile.name)
		updateMetadataLabel(selectedPathLabel, "Path", selectedFile.absolutePath)
		updateMetadataLabel(sessionIdLabel, "Session ID", sessionId ?: "Not detected")
		updateMetadataLabel(resumeCommandLabel, "Resume Command", resumeCommand ?: "Not available")
		copyResumeCommandButton.isEnabled = resumeCommand != null
		copyResumeCommandButton.toolTipText = resumeCommand

		renderCurrentChat()
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

	private fun createFilterToggle(tooltip: String, icon: Icon): JToggleButton {
		return JToggleButton(icon, true).apply {
			toolTipText = tooltip
			margin = Insets(0, 0, 0, 0)
			font = Font(Font.SANS_SERIF, Font.PLAIN, 11)
			preferredSize = Dimension(28, 24)
			minimumSize = Dimension(28, 24)
			isFocusPainted = false
			isBorderPainted = true
			background = Color(42, 42, 42)
			foreground = Color(230, 230, 230)
			border = BorderFactory.createCompoundBorder(
				BorderFactory.createLineBorder(Color(78, 78, 78)),
				BorderFactory.createEmptyBorder(2, 2, 2, 2)
			)
			addActionListener { renderCurrentChat() }
		}
	}

	private fun renderCurrentChat() {
		val file = currentSelectedFile
		val parsed = currentParsedChatLog
		if (file == null || parsed == null) {
			ChatStyledRenderer.render(chatArea, null, null, null)
			return
		}

		val filteredChatLog = parsed.filtered(
			ChatEntryFilter(
				showYou = showYouToggle.isSelected,
				showCodex = showCodexToggle.isSelected,
				showToolCall = showToolCallToggle.isSelected,
				showToolResult = showToolResultToggle.isSelected,
				showMeta = showMetaToggle.isSelected
			)
		)

		ChatStyledRenderer.render(chatArea, file, currentSessionId, filteredChatLog)
	}
}

private class WrappedTextPane : JTextPane() {
	override fun getScrollableTracksViewportWidth(): Boolean {
		return true
	}
}

private abstract class BaseFilterIcon : Icon {
	protected val selectedColor = Color(230, 230, 230)
	protected val unselectedColor = Color(118, 118, 118)
	protected val accentColor = Color(74, 144, 226)

	final override fun paintIcon(component: java.awt.Component, graphics: Graphics, x: Int, y: Int) {
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

private class PersonFilterIcon : BaseFilterIcon() {
	override fun paintGlyph(g2: Graphics2D) {
		g2.fillOval(7, 1, 6, 6)
		g2.fillRoundRect(5, 8, 10, 6, 5, 5)
	}
}

private class RobotFilterIcon : BaseFilterIcon() {
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

private class TerminalFilterIcon : BaseFilterIcon() {
	override fun paintGlyph(g2: Graphics2D) {
		g2.drawRoundRect(2, 2, 16, 12, 3, 3)
		g2.drawLine(5, 6, 8, 8)
		g2.drawLine(5, 10, 8, 8)
		g2.drawLine(10, 10, 14, 10)
	}
}

private class DocumentFilterIcon : BaseFilterIcon() {
	override fun paintGlyph(g2: Graphics2D) {
		g2.drawRoundRect(4, 1, 10, 14, 2, 2)
		g2.drawLine(14, 1, 16, 3)
		g2.drawLine(14, 1, 14, 3)
		g2.drawLine(8, 5, 12, 5)
		g2.drawLine(6, 8, 12, 8)
		g2.drawLine(6, 11, 12, 11)
	}
}

private class MetaFilterIcon : BaseFilterIcon() {
	override fun paintGlyph(g2: Graphics2D) {
		g2.drawOval(5, 1, 10, 10)
		g2.fillOval(9, 4, 2, 2)
		g2.drawLine(10, 7, 10, 10)
		g2.drawLine(4, 13, 16, 13)
	}
}
