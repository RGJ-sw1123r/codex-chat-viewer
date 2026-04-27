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
import java.awt.event.ActionEvent
import java.awt.event.KeyEvent
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import javax.swing.AbstractAction
import javax.swing.BorderFactory
import javax.swing.Icon
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JFileChooser
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTextPane
import javax.swing.KeyStroke
import javax.swing.JToggleButton
import javax.swing.SwingUtilities
import javax.swing.ScrollPaneConstants
import javax.swing.filechooser.FileNameExtensionFilter
import javax.swing.text.DefaultHighlighter

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
	private var currentSearchQuery = ""
	private var currentSearchMatches: List<SearchMatch> = emptyList()
	private var currentSearchMatchIndex = -1
	private val searchHighlightTags = mutableListOf<Any>()
	private val searchResultPainter = DefaultHighlighter.DefaultHighlightPainter(Color(71, 104, 168))
	private val currentSearchResultPainter = DefaultHighlighter.DefaultHighlightPainter(Color(232, 181, 56))

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
	private val searchPanel = SearchPanel(
		onQueryChanged = ::updateSearchQuery,
		onPrevious = ::goToPreviousSearchResult,
		onNext = ::goToNextSearchResult,
		onClose = ::closeSearch
	)

	init {
		defaultCloseOperation = EXIT_ON_CLOSE
		minimumSize = Dimension(900, 650)
		setLocationRelativeTo(null)

		layout = BorderLayout()

		add(createTopPanel(), BorderLayout.NORTH)
		add(createChatPanel(), BorderLayout.CENTER)
		installSearchShortcuts()
		updateSelection(null)

		pack()
	}

	private fun createTopPanel(): JPanel {
		val panel = JPanel(BorderLayout())
		panel.add(createHeaderPanel(), BorderLayout.NORTH)
		panel.add(searchPanel, BorderLayout.CENTER)
		panel.add(createMetadataPanel(), BorderLayout.SOUTH)
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
			exportMarkdown()
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
			resetSearchState()
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
		resetSearchState()

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
			clearSearchHighlights()
			return
		}

		val filteredChatLog = currentFilteredChatLog() ?: return

		ChatStyledRenderer.render(chatArea, file, currentSessionId, filteredChatLog)
		refreshSearchHighlights(preserveCurrentIndex = true)
	}

	private fun currentFilteredChatLog(): ParsedChatLog? {
		val parsed = currentParsedChatLog ?: return null
		return parsed.filtered(
			ChatEntryFilter(
				showYou = showYouToggle.isSelected,
				showCodex = showCodexToggle.isSelected,
				showToolCall = showToolCallToggle.isSelected,
				showToolResult = showToolResultToggle.isSelected,
				showMeta = showMetaToggle.isSelected
			)
		)
	}

	private fun exportMarkdown() {
		val sourceFile = currentSelectedFile
		val filteredChatLog = currentFilteredChatLog()
		if (sourceFile == null || filteredChatLog == null) {
			ChatStyledRenderer.appendSystemNotice(chatArea, "No transcript loaded to export.")
			return
		}

		val chooser = JFileChooser(sourceFile.parentFile ?: resolveInitialDirectory())
		chooser.dialogTitle = "Export Markdown"
		chooser.fileFilter = FileNameExtensionFilter("Markdown files (*.md)", "md")
		chooser.setAcceptAllFileFilterUsed(true)
		chooser.selectedFile = suggestedMarkdownExportFile(sourceFile, chooser.currentDirectory)

		val result = chooser.showSaveDialog(this)
		if (result != JFileChooser.APPROVE_OPTION) {
			return
		}

		val targetFile = ensureMarkdownExtension(chooser.selectedFile ?: return)
		if (targetFile.exists() && !confirmOverwrite(targetFile)) {
			return
		}

		try {
			targetFile.parentFile?.mkdirs()
			val markdown = MarkdownTranscriptExporter.export(
				sourceFile = sourceFile,
				sessionId = currentSessionId,
				chatLog = filteredChatLog
			)
			Files.writeString(targetFile.toPath(), markdown, StandardCharsets.UTF_8)
			lastSelectedDirectory = targetFile.parentFile
			openExplorerSelection(targetFile)
			ChatStyledRenderer.appendSystemNotice(chatArea, "Markdown exported to ${targetFile.absolutePath}")
		} catch (exception: Exception) {
			ChatStyledRenderer.appendSystemNotice(chatArea, "Markdown export failed: ${exception.message ?: exception.javaClass.simpleName}")
		}
	}

	private fun defaultMarkdownExportName(sourceFile: File): String {
		return sourceFile.nameWithoutExtension + ".md"
	}

	private fun suggestedMarkdownExportFile(sourceFile: File, directory: File?): File {
		val baseDirectory = directory ?: sourceFile.parentFile ?: resolveInitialDirectory()
		val defaultName = defaultMarkdownExportName(sourceFile)
		val defaultFile = File(baseDirectory, defaultName)
		if (!defaultFile.exists()) {
			return defaultFile
		}

		val baseName = sourceFile.nameWithoutExtension
		var index = 1
		while (true) {
			val candidate = File(baseDirectory, "$baseName ($index).md")
			if (!candidate.exists()) {
				return candidate
			}
			index += 1
		}
	}

	private fun ensureMarkdownExtension(file: File): File {
		return if (file.name.lowercase().endsWith(".md")) file else File(file.parentFile, file.name + ".md")
	}

	private fun confirmOverwrite(file: File): Boolean {
		val result = JOptionPane.showConfirmDialog(
			this,
			"${file.name} already exists.\nDo you want to overwrite it?",
			"Overwrite Markdown Export",
			JOptionPane.YES_NO_OPTION,
			JOptionPane.WARNING_MESSAGE
		)
		return result == JOptionPane.YES_OPTION
	}

	private fun openExplorerSelection(file: File) {
		try {
			ProcessBuilder("explorer.exe", "/select,", file.absolutePath)
				.start()
		} catch (_: Exception) {
			// Export success should not depend on Explorer opening successfully.
		}
	}

	private fun installSearchShortcuts() {
		val inputMap = rootPane.getInputMap(JPanel.WHEN_IN_FOCUSED_WINDOW)
		val actionMap = rootPane.actionMap

		inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_F, KeyEvent.CTRL_DOWN_MASK), "search.open")
		actionMap.put("search.open", object : AbstractAction() {
			override fun actionPerformed(event: ActionEvent?) {
				if (searchPanel.isVisible) {
					searchPanel.closeSearch()
				} else {
					searchPanel.openSearch()
				}
			}
		})
	}

	private fun updateSearchQuery(query: String) {
		currentSearchQuery = query
		refreshSearchHighlights(preserveCurrentIndex = false)
	}

	private fun refreshSearchHighlights(preserveCurrentIndex: Boolean) {
		clearSearchHighlights()

		if (currentSearchQuery.isBlank()) {
			currentSearchMatches = emptyList()
			currentSearchMatchIndex = -1
			searchPanel.updateMatchCount(null, null)
			return
		}

		currentSearchMatches = findCaseInsensitiveMatches(currentRenderedText(), currentSearchQuery)
		if (currentSearchMatches.isEmpty()) {
			currentSearchMatchIndex = -1
			searchPanel.updateMatchCount(null, 0)
			return
		}

		currentSearchMatchIndex = if (preserveCurrentIndex && currentSearchMatchIndex >= 0) {
			currentSearchMatchIndex.coerceAtMost(currentSearchMatches.lastIndex)
		} else {
			0
		}

		applySearchHighlights()
		scrollToSearchMatch(currentSearchMatches[currentSearchMatchIndex])
		searchPanel.updateMatchCount(currentSearchMatchIndex, currentSearchMatches.size)
	}

	private fun goToNextSearchResult() {
		moveToSearchResult(direction = 1)
	}

	private fun goToPreviousSearchResult() {
		moveToSearchResult(direction = -1)
	}

	private fun moveToSearchResult(direction: Int) {
		if (currentSearchMatches.isEmpty()) {
			searchPanel.updateMatchCount(null, if (currentSearchQuery.isBlank()) null else 0)
			return
		}

		currentSearchMatchIndex = when {
			currentSearchMatchIndex < 0 -> 0
			direction > 0 -> (currentSearchMatchIndex + 1) % currentSearchMatches.size
			else -> (currentSearchMatchIndex - 1 + currentSearchMatches.size) % currentSearchMatches.size
		}

		applySearchHighlights()
		scrollToSearchMatch(currentSearchMatches[currentSearchMatchIndex])
		searchPanel.updateMatchCount(currentSearchMatchIndex, currentSearchMatches.size)
	}

	private fun closeSearch() {
		clearSearchHighlights()
		currentSearchQuery = ""
		currentSearchMatches = emptyList()
		currentSearchMatchIndex = -1
		searchPanel.updateMatchCount(null, null)
	}

	private fun resetSearchState() {
		currentSearchQuery = ""
		currentSearchMatches = emptyList()
		currentSearchMatchIndex = -1
		clearSearchHighlights()
		searchPanel.reset()
	}

	private fun applySearchHighlights() {
		clearSearchHighlights()

		val highlighter = chatArea.highlighter
		currentSearchMatches.forEachIndexed { index, match ->
			val painter = if (index == currentSearchMatchIndex) currentSearchResultPainter else searchResultPainter
			val highlightTag = highlighter.addHighlight(match.start, match.end, painter)
			searchHighlightTags += highlightTag
		}
	}

	private fun clearSearchHighlights() {
		val highlighter = chatArea.highlighter
		searchHighlightTags.forEach(highlighter::removeHighlight)
		searchHighlightTags.clear()
	}

	private fun scrollToSearchMatch(match: SearchMatch) {
		chatArea.caretPosition = match.start
		val bounds = chatArea.modelToView2D(match.start)?.bounds ?: return
		chatArea.scrollRectToVisible(bounds)
	}

	private fun currentRenderedText(): String {
		val document = chatArea.document
		return document.getText(0, document.length)
	}
}

private class WrappedTextPane : JTextPane() {
	override fun getScrollableTracksViewportWidth(): Boolean {
		return true
	}
}

internal data class SearchMatch(
	val start: Int,
	val end: Int
)

internal fun findCaseInsensitiveMatches(text: String, query: String): List<SearchMatch> {
	if (query.isBlank() || query.length > text.length) {
		return emptyList()
	}

	val matches = mutableListOf<SearchMatch>()
	var startIndex = 0
	while (startIndex <= text.length - query.length) {
		var matchIndex = -1
		var candidateIndex = startIndex
		while (candidateIndex <= text.length - query.length) {
			if (text.regionMatches(candidateIndex, query, 0, query.length, ignoreCase = true)) {
				matchIndex = candidateIndex
				break
			}
			candidateIndex += 1
		}
		if (matchIndex < 0) {
			break
		}

		matches += SearchMatch(matchIndex, matchIndex + query.length)
		startIndex = matchIndex + query.length
	}
	return matches
}
