package app.codexchatviewer

import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.Font
import java.awt.FlowLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import java.awt.Rectangle
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.awt.event.ActionEvent
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import javax.swing.AbstractAction
import javax.swing.BorderFactory
import javax.swing.Icon
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JFileChooser
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.Scrollable
import javax.swing.JTextPane
import javax.swing.KeyStroke
import javax.swing.JToggleButton
import javax.swing.SwingUtilities
import javax.swing.ScrollPaneConstants
import javax.swing.filechooser.FileNameExtensionFilter
import javax.swing.text.DefaultHighlighter
import kotlin.math.abs
import kotlin.math.roundToInt

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
	private val collapsedBlockIndexes = mutableSetOf<Int>()
	private var transcriptHeaderRanges: List<TranscriptHeaderRange> = emptyList()
	private val searchHighlightTags = mutableListOf<Any>()
	private val searchResultPainter = DefaultHighlighter.DefaultHighlightPainter(Color(71, 104, 168))
	private val currentSearchResultPainter = DefaultHighlighter.DefaultHighlightPainter(Color(232, 181, 56))

	private val themeComboBox = JComboBox(ChatRenderThemes.availableThemeNames.toTypedArray())

	private val selectedFileLabel = JLabel("Selected File: None")
	private val selectedPathLabel = JLabel("Path: Not selected")
	private val sessionIdLabel = JLabel("Session ID: Not detected")
	private val resumeCommandLabel = JLabel("Resume Command: Not available")
	private val copyResumeCommandButton = JButton("Copy Resume Command")
	private val chatArea = WrappedTextPane()
	private val componentTranscriptPanel = ComponentTranscriptPanel()
	private val transcriptScrollPane = JScrollPane()
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
	private var componentRenderedText = ""
	private var componentBlockRanges: List<ComponentBlockRange> = emptyList()
	private var componentSearchHighlightedComponent: JComponent? = null
	private var lastComponentRenderViewportWidth = 0

	init {
		defaultCloseOperation = EXIT_ON_CLOSE
		minimumSize = Dimension(900, 650)
		setLocationRelativeTo(null)

		layout = BorderLayout()

		add(createTopPanel(), BorderLayout.NORTH)
		add(createChatPanel(), BorderLayout.CENTER)
		installSearchShortcuts()
		installTranscriptHeaderToggle()
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
		themeComboBox.addActionListener {
			renderCurrentChatPreservingViewport()
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

		return transcriptScrollPane.apply {
			setViewportView(chatArea)
			horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
			verticalScrollBarPolicy = ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED
			viewport.addComponentListener(object : ComponentAdapter() {
				override fun componentResized(event: ComponentEvent?) {
					handleTranscriptViewportResize()
				}
			})
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
			collapsedBlockIndexes.clear()
			transcriptHeaderRanges = emptyList()
			resetSearchState()
			updateMetadataLabel(selectedFileLabel, "Selected File", "None")
			updateMetadataLabel(selectedPathLabel, "Path", "Not selected")
			updateMetadataLabel(sessionIdLabel, "Session ID", "Not detected")
			updateMetadataLabel(resumeCommandLabel, "Resume Command", "Not available")
			copyResumeCommandButton.isEnabled = false
			copyResumeCommandButton.toolTipText = null
			renderCurrentChat()
			return
		}

		val sessionId = detectSessionId(selectedFile)
		val resumeCommand = sessionId?.let { "codex resume $it" }
		val parsedChatLog = JsonlChatParser.parse(selectedFile)
		currentSelectedFile = selectedFile
		currentSessionId = sessionId
		currentParsedChatLog = parsedChatLog
		collapsedBlockIndexes.clear()
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
			addActionListener {
				collapsedBlockIndexes.clear()
				renderCurrentChat()
			}
		}
	}

	private fun renderCurrentChat() {
		renderCurrentChat(scrollAnchor = null)
	}

	private fun renderCurrentChatPreservingViewport() {
		val viewportAnchor = viewportAnchorForCurrentPosition()
		renderCurrentChat(scrollAnchor = null, resetCaretToTop = false)
		if (viewportAnchor != null) {
			SwingUtilities.invokeLater {
				restoreViewportAnchor(viewportAnchor)
			}
		}
	}

	private fun renderCurrentChat(
		scrollAnchor: TranscriptScrollAnchor?,
		resetCaretToTop: Boolean = scrollAnchor == null
	) {
		val file = currentSelectedFile
		val parsed = currentParsedChatLog
		if (file == null || parsed == null) {
			if (usesComponentRenderer()) {
				showComponentView()
				val result = renderComponentTranscript(file = null, parsedChatLog = null)
				transcriptHeaderRanges = result.headerRanges
				componentRenderedText = result.transcriptText
				componentBlockRanges = result.blockRanges
				lastComponentRenderViewportWidth = currentTranscriptViewportWidth()
			} else {
				showTerminalTextView()
				ChatStyledRenderer.render(chatArea, null, null, null, currentTheme())
				componentRenderedText = ""
				componentBlockRanges = emptyList()
			}
			transcriptHeaderRanges = emptyList()
			clearSearchHighlights()
			return
		}

		val filteredChatLog = currentFilteredChatLog() ?: return

		if (usesComponentRenderer()) {
			showComponentView()
			val result = renderComponentTranscript(file = file, parsedChatLog = filteredChatLog)
			transcriptHeaderRanges = result.headerRanges
			componentRenderedText = result.transcriptText
			componentBlockRanges = result.blockRanges
			lastComponentRenderViewportWidth = currentTranscriptViewportWidth()
			if (resetCaretToTop) {
				scrollTranscriptToTop()
			}
		} else {
			showTerminalTextView()
			transcriptHeaderRanges = ChatStyledRenderer.render(
				viewer = chatArea,
				file = file,
				sessionId = currentSessionId,
				parsedChatLog = filteredChatLog,
				theme = currentTheme(),
				collapsedBlockIndexes = collapsedBlockIndexes,
				resetCaretToTop = resetCaretToTop
			)
			componentRenderedText = ""
			componentBlockRanges = emptyList()
		}
		refreshSearchHighlights(preserveCurrentIndex = true)
		if (scrollAnchor != null) {
			SwingUtilities.invokeLater {
				restoreScrollAnchor(scrollAnchor)
			}
		}
	}

	private fun renderComponentTranscript(file: File?, parsedChatLog: ParsedChatLog?): ComponentRenderResult {
		val theme = currentTheme()
		return if (theme.name == ChatRenderThemes.markdownStyle.name) {
			MarkdownDocumentRenderer.render(
				container = componentTranscriptPanel,
				file = file,
				sessionId = currentSessionId,
				parsedChatLog = parsedChatLog,
				theme = theme,
				viewportWidth = currentTranscriptViewportWidth(),
				collapsedBlockIndexes = collapsedBlockIndexes,
				onHeaderClicked = ::toggleComponentBlock
			)
		} else {
			MessengerChatRenderer.render(
				container = componentTranscriptPanel,
				file = file,
				sessionId = currentSessionId,
				parsedChatLog = parsedChatLog,
				theme = theme,
				viewportWidth = currentTranscriptViewportWidth(),
				collapsedBlockIndexes = collapsedBlockIndexes,
				onHeaderClicked = ::toggleComponentBlock
			)
		}
	}

	private fun handleTranscriptViewportResize() {
		if (!usesComponentRenderer()) {
			return
		}

		val viewportWidth = currentTranscriptViewportWidth()
		if (viewportWidth <= 0 || abs(viewportWidth - lastComponentRenderViewportWidth) < 12) {
			return
		}

		renderCurrentChatPreservingViewport()
	}

	private fun currentTranscriptViewportWidth(): Int {
		val viewportWidth = transcriptScrollPane.viewport.extentSize.width
		return if (viewportWidth > 0) viewportWidth else transcriptScrollPane.width
	}

	private fun scrollTranscriptToTop() {
		transcriptScrollPane.verticalScrollBar.value = 0
		SwingUtilities.invokeLater {
			transcriptScrollPane.verticalScrollBar.value = 0
		}
	}

	private fun showTerminalTextView() {
		if (transcriptScrollPane.viewport.view !== chatArea) {
			transcriptScrollPane.setViewportView(chatArea)
		}
		transcriptScrollPane.verticalScrollBar.unitIncrement = 16
		transcriptScrollPane.verticalScrollBar.blockIncrement = 80
	}

	private fun showComponentView() {
		if (transcriptScrollPane.viewport.view !== componentTranscriptPanel) {
			transcriptScrollPane.setViewportView(componentTranscriptPanel)
		}
		transcriptScrollPane.verticalScrollBar.unitIncrement = 36
		transcriptScrollPane.verticalScrollBar.blockIncrement = 220
	}

	private fun viewportAnchorForCurrentPosition(): TranscriptViewportAnchor? {
		val scrollBar = transcriptScrollPane.verticalScrollBar
		val maxY = (scrollBar.maximum - scrollBar.visibleAmount).coerceAtLeast(0)
		val scrollRatio = if (maxY > 0) {
			scrollBar.value.toDouble() / maxY.toDouble()
		} else {
			0.0
		}
		return TranscriptViewportAnchor(scrollRatio = scrollRatio)
	}

	private fun restoreViewportAnchor(viewportAnchor: TranscriptViewportAnchor) {
		val scrollBar = transcriptScrollPane.verticalScrollBar
		val maxY = (scrollBar.maximum - scrollBar.visibleAmount).coerceAtLeast(0)
		scrollBar.value = if (maxY > 0) {
			(viewportAnchor.scrollRatio * maxY.toDouble()).roundToInt().coerceIn(0, maxY)
		} else {
			0
		}
	}

	private fun toggleComponentBlock(blockIndex: Int) {
		val viewportAnchor = viewportAnchorForCurrentPosition()
		if (!collapsedBlockIndexes.add(blockIndex)) {
			collapsedBlockIndexes.remove(blockIndex)
		}
		renderCurrentChat(scrollAnchor = null, resetCaretToTop = false)
		if (viewportAnchor != null) {
			SwingUtilities.invokeLater {
				restoreViewportAnchor(viewportAnchor)
			}
		}
	}

	private fun installTranscriptHeaderToggle() {
		chatArea.addMouseListener(object : MouseAdapter() {
			override fun mouseClicked(event: MouseEvent) {
				if (event.button != MouseEvent.BUTTON1) {
					return
				}

				val offset = chatArea.viewToModel2D(event.point)
				val headerRange = transcriptHeaderRanges.firstOrNull { range ->
					offset >= range.startOffset && offset < range.endOffset
				} ?: return
				val anchor = scrollAnchorFor(headerRange)

				if (!collapsedBlockIndexes.add(headerRange.blockIndex)) {
					collapsedBlockIndexes.remove(headerRange.blockIndex)
				}
				renderCurrentChat(scrollAnchor = anchor)
			}
		})
	}

	private fun scrollAnchorFor(headerRange: TranscriptHeaderRange): TranscriptScrollAnchor? {
		val headerBounds = chatArea.modelToView2D(headerRange.startOffset)?.bounds ?: return null
		return TranscriptScrollAnchor(
			blockIndex = headerRange.blockIndex,
			viewportYOffset = headerBounds.y - chatArea.visibleRect.y
		)
	}

	private fun restoreScrollAnchor(scrollAnchor: TranscriptScrollAnchor?) {
		if (scrollAnchor == null) {
			return
		}

		val headerRange = transcriptHeaderRanges.firstOrNull { it.blockIndex == scrollAnchor.blockIndex } ?: return
		val headerBounds = chatArea.modelToView2D(headerRange.startOffset)?.bounds ?: return
		val viewport = chatArea.visibleRect
		viewport.y = (headerBounds.y - scrollAnchor.viewportYOffset)
			.coerceIn(0, (chatArea.height - viewport.height).coerceAtLeast(0))
		chatArea.scrollRectToVisible(viewport)
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
			appendViewerNotice("No transcript loaded to export.")
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
			appendViewerNotice("Markdown exported to ${targetFile.absolutePath}")
		} catch (exception: Exception) {
			appendViewerNotice("Markdown export failed: ${exception.message ?: exception.javaClass.simpleName}")
		}
	}

	private fun appendViewerNotice(notice: String) {
		if (usesComponentRenderer()) {
			showComponentView()
			if (currentTheme().name == ChatRenderThemes.markdownStyle.name) {
				MarkdownDocumentRenderer.appendSystemNotice(
					componentTranscriptPanel,
					notice,
					currentTheme(),
					viewportWidth = currentTranscriptViewportWidth()
				)
			} else {
				MessengerChatRenderer.appendSystemNotice(
					componentTranscriptPanel,
					notice,
					currentTheme(),
					viewportWidth = currentTranscriptViewportWidth()
				)
			}
			componentRenderedText = (componentRenderedText + "\n[SYSTEM]\n" + notice).trim()
		} else {
			showTerminalTextView()
			ChatStyledRenderer.appendSystemNotice(chatArea, notice, currentTheme())
		}
	}

	private fun currentTheme(): ChatRenderTheme {
		return ChatRenderThemes.byName(themeComboBox.selectedItem as? String ?: ChatRenderThemes.terminalStyle.name)
	}

	private fun usesComponentRenderer(): Boolean {
		return when (currentTheme().name) {
			ChatRenderThemes.markdownStyle.name,
			ChatRenderThemes.messengerStyle.name,
			ChatRenderThemes.dmStyle.name -> true
			else -> false
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

		if (usesComponentRenderer()) {
			val match = currentSearchMatches.getOrNull(currentSearchMatchIndex) ?: return
			val blockRange = componentBlockRanges.firstOrNull { range ->
				match.start >= range.startOffset && match.start < range.endOffset
			} ?: return
			componentSearchHighlightedComponent = blockRange.component
			blockRange.component.border = BorderFactory.createCompoundBorder(
				BorderFactory.createLineBorder(Color(255, 213, 79), 2),
				BorderFactory.createEmptyBorder(5, 4, 5, 4)
			)
			blockRange.component.repaint()
			return
		}

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
		componentSearchHighlightedComponent?.let { component ->
			component.border = BorderFactory.createEmptyBorder(5, 4, 5, 4)
			component.repaint()
		}
		componentSearchHighlightedComponent = null
	}

	private fun scrollToSearchMatch(match: SearchMatch) {
		if (usesComponentRenderer()) {
			val blockRange = componentBlockRanges.firstOrNull { range ->
				match.start >= range.startOffset && match.start < range.endOffset
			} ?: return
			blockRange.component.scrollRectToVisible(
				java.awt.Rectangle(0, 0, blockRange.component.width, blockRange.component.height)
			)
			return
		}

		chatArea.caretPosition = match.start
		val bounds = chatArea.modelToView2D(match.start)?.bounds ?: return
		chatArea.scrollRectToVisible(bounds)
	}

	private fun currentRenderedText(): String {
		if (usesComponentRenderer()) {
			return componentRenderedText
		}

		val document = chatArea.document
		return document.getText(0, document.length)
	}
}

private class WrappedTextPane : JTextPane() {
	override fun getScrollableTracksViewportWidth(): Boolean {
		return true
	}
}

private class ComponentTranscriptPanel : JPanel(), Scrollable {
	override fun getPreferredScrollableViewportSize(): Dimension {
		return preferredSize
	}

	override fun getScrollableUnitIncrement(visibleRect: Rectangle?, orientation: Int, direction: Int): Int {
		return 36
	}

	override fun getScrollableBlockIncrement(visibleRect: Rectangle?, orientation: Int, direction: Int): Int {
		return 220
	}

	override fun getScrollableTracksViewportWidth(): Boolean {
		return true
	}

	override fun getScrollableTracksViewportHeight(): Boolean {
		return false
	}
}

private data class TranscriptScrollAnchor(
	val blockIndex: Int,
	val viewportYOffset: Int
)

private data class TranscriptViewportAnchor(
	val scrollRatio: Double
)

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
