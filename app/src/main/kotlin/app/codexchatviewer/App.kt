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
import javax.imageio.ImageIO
import javax.swing.AbstractAction
import javax.swing.BorderFactory
import javax.swing.Icon
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JFileChooser
import javax.swing.JFrame
import javax.swing.JLabel
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
import javax.swing.text.JTextComponent
import javax.swing.border.Border
import kotlin.math.abs

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
	private val transcriptSearchController = TranscriptSearchController()
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
	private val transcriptRenderController = TranscriptRenderController(chatArea, componentTranscriptPanel)
	private val transcriptScrollPane = JScrollPane()
	private val transcriptScrollController = TranscriptScrollController(transcriptScrollPane, chatArea)
	private val markdownExportController = MarkdownExportController(
		parent = this,
		fallbackDirectory = ::resolveInitialDirectory
	)
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
	private val componentSearchHighlightTags = mutableListOf<ComponentSearchHighlightTag>()
	private val componentFallbackHighlightedBorders = mutableMapOf<JComponent, Border?>()
	private var lastComponentRenderViewportWidth = 0

	init {
		defaultCloseOperation = EXIT_ON_CLOSE
		CodexChatViewerFrame::class.java.getResource("app-icon.png")?.let { iconResource ->
			iconImage = ImageIO.read(iconResource)
		}
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
		val viewportAnchor = transcriptScrollController.captureViewportAnchor()
		renderCurrentChat(scrollAnchor = null, resetCaretToTop = false)
		SwingUtilities.invokeLater {
			transcriptScrollController.restoreViewportAnchor(viewportAnchor)
		}
	}

	private fun renderCurrentChat(
		scrollAnchor: TranscriptScrollAnchor?,
		resetCaretToTop: Boolean = scrollAnchor == null
	) {
		val theme = currentTheme()
		val hasLoadedTranscript = currentSelectedFile != null && currentParsedChatLog != null
		val renderResult = transcriptRenderController.render(
			TranscriptRenderRequest(
				file = currentSelectedFile.takeIf { hasLoadedTranscript },
				sessionId = currentSessionId,
				parsedChatLog = if (hasLoadedTranscript) currentFilteredChatLog() else null,
				theme = theme,
				viewportWidth = currentTranscriptViewportWidth(),
				collapsedBlockIndexes = collapsedBlockIndexes,
				resetCaretToTop = resetCaretToTop,
				onComponentHeaderClicked = ::toggleComponentBlock
			)
		)
		applyRenderResult(renderResult, resetCaretToTop)

		if (!hasLoadedTranscript) {
			clearSearchHighlights()
			return
		}

		refreshSearchHighlights(preserveCurrentIndex = true)
		if (scrollAnchor != null) {
			SwingUtilities.invokeLater {
				transcriptScrollController.restoreTextBlockAnchor(scrollAnchor, transcriptHeaderRanges)
			}
		}
	}

	private fun applyRenderResult(renderResult: TranscriptRenderResult, resetCaretToTop: Boolean) {
		transcriptHeaderRanges = renderResult.headerRanges
		when (renderResult.mode) {
			TranscriptRenderMode.TEXT -> {
				showTerminalTextView()
				componentRenderedText = ""
				componentBlockRanges = emptyList()
			}

			TranscriptRenderMode.COMPONENT -> {
				showComponentView()
				componentRenderedText = renderResult.renderedText
				componentBlockRanges = renderResult.componentBlockRanges
				lastComponentRenderViewportWidth = currentTranscriptViewportWidth()
				if (resetCaretToTop) {
					scrollTranscriptToTop()
				}
			}
		}
	}

	private fun handleTranscriptViewportResize() {
		if (currentRenderMode() != TranscriptRenderMode.COMPONENT) {
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
		transcriptScrollController.scrollToTop()
		SwingUtilities.invokeLater {
			transcriptScrollController.scrollToTop()
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

	private fun toggleComponentBlock(blockIndex: Int) {
		val viewportAnchor = transcriptScrollController.captureViewportAnchor()
		if (!collapsedBlockIndexes.add(blockIndex)) {
			collapsedBlockIndexes.remove(blockIndex)
		}
		renderCurrentChat(scrollAnchor = null, resetCaretToTop = false)
		SwingUtilities.invokeLater {
			transcriptScrollController.restoreViewportAnchor(viewportAnchor)
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
				val anchor = transcriptScrollController.captureTextBlockAnchor(headerRange)

				if (!collapsedBlockIndexes.add(headerRange.blockIndex)) {
					collapsedBlockIndexes.remove(headerRange.blockIndex)
				}
				renderCurrentChat(scrollAnchor = anchor)
			}
		})
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

		when (val result = markdownExportController.export(
			MarkdownExportRequest(
				sourceFile = sourceFile,
				sessionId = currentSessionId,
				chatLog = filteredChatLog,
				initialDirectory = lastSelectedDirectory
			)
		)) {
			MarkdownExportOutcome.Cancelled -> return
			is MarkdownExportOutcome.Success -> {
				lastSelectedDirectory = result.file.parentFile
				appendViewerNotice(result.noticeMessage)
			}
			is MarkdownExportOutcome.Failure -> {
				appendViewerNotice(result.noticeMessage)
			}
		}
	}

	private fun appendViewerNotice(notice: String) {
		val theme = currentTheme()
		when (transcriptRenderController.appendSystemNotice(notice, theme, currentTranscriptViewportWidth())) {
			TranscriptRenderMode.TEXT -> {
				showTerminalTextView()
			}

			TranscriptRenderMode.COMPONENT -> {
				showComponentView()
				componentRenderedText = (componentRenderedText + "\n[SYSTEM]\n" + notice).trim()
			}
		}
	}

	private fun currentTheme(): ChatRenderTheme {
		return ChatRenderThemes.byName(themeComboBox.selectedItem as? String ?: ChatRenderThemes.terminalStyle.name)
	}

	private fun currentRenderMode(): TranscriptRenderMode {
		return transcriptRenderController.renderModeFor(currentTheme())
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
		transcriptSearchController.updateQuery(query, currentRenderedText())
		refreshSearchHighlights(preserveCurrentIndex = false)
	}

	private fun refreshSearchHighlights(preserveCurrentIndex: Boolean) {
		clearSearchHighlights()
		val searchState = transcriptSearchController.refreshMatches(
			text = currentRenderedText(),
			preserveCurrentIndex = preserveCurrentIndex
		)

		if (searchState.query.isBlank()) {
			searchPanel.updateMatchCount(null, null)
			return
		}

		if (searchState.matches.isEmpty()) {
			searchPanel.updateMatchCount(null, 0)
			return
		}

		applySearchHighlights()
		scrollToSearchMatch(searchState.currentMatch() ?: return)
		searchPanel.updateMatchCount(searchState.currentIndex, searchState.matches.size)
	}

	private fun goToNextSearchResult() {
		moveToSearchResult(transcriptSearchController.moveToNextMatch())
	}

	private fun goToPreviousSearchResult() {
		moveToSearchResult(transcriptSearchController.moveToPreviousMatch())
	}

	private fun moveToSearchResult(searchState: SearchState) {
		if (searchState.matches.isEmpty()) {
			searchPanel.updateMatchCount(null, if (searchState.query.isBlank()) null else 0)
			return
		}

		applySearchHighlights()
		scrollToSearchMatch(searchState.currentMatch() ?: return)
		searchPanel.updateMatchCount(searchState.currentIndex, searchState.matches.size)
	}

	private fun closeSearch() {
		clearSearchHighlights()
		transcriptSearchController.clear()
		searchPanel.updateMatchCount(null, null)
	}

	private fun resetSearchState() {
		transcriptSearchController.clear()
		clearSearchHighlights()
		searchPanel.reset()
	}

	private fun applySearchHighlights() {
		clearSearchHighlights()
		val searchState = transcriptSearchController.currentState()
		val currentMatch = searchState.currentMatch() ?: return

		if (currentRenderMode() == TranscriptRenderMode.COMPONENT) {
			searchState.matches.forEachIndexed { index, match ->
				val mappedRange = componentTextRangeFor(match) ?: return@forEachIndexed
				val painter = if (index == searchState.currentIndex) currentSearchResultPainter else searchResultPainter
				val highlightTag = mappedRange.textComponent.highlighter.addHighlight(
					mappedRange.startOffset,
					mappedRange.endOffset,
					painter
				)
				componentSearchHighlightTags += ComponentSearchHighlightTag(mappedRange.textComponent, highlightTag)
			}
			if (componentTextRangeFor(currentMatch) == null) {
				applyComponentFallbackHighlight(currentMatch)
			}
			return
		}

		val highlighter = chatArea.highlighter
		searchState.matches.forEachIndexed { index, match ->
			val painter = if (index == searchState.currentIndex) currentSearchResultPainter else searchResultPainter
			val highlightTag = highlighter.addHighlight(match.start, match.end, painter)
			searchHighlightTags += highlightTag
		}
	}

	private fun clearSearchHighlights() {
		val highlighter = chatArea.highlighter
		searchHighlightTags.forEach(highlighter::removeHighlight)
		searchHighlightTags.clear()
		componentSearchHighlightTags.forEach { tag ->
			tag.textComponent.highlighter.removeHighlight(tag.highlightTag)
		}
		componentSearchHighlightTags.clear()
		componentFallbackHighlightedBorders.forEach { (component, border) ->
			component.border = border
			component.repaint()
		}
		componentFallbackHighlightedBorders.clear()
	}

	private fun scrollToSearchMatch(match: SearchMatch) {
		if (currentRenderMode() == TranscriptRenderMode.COMPONENT) {
			componentTextRangeFor(match)?.let { mappedRange ->
				transcriptScrollController.scrollTextComponentOffsetIntoView(
					mappedRange.textComponent,
					mappedRange.startOffset
				)
				return
			}
			val blockRange = componentBlockRanges.firstOrNull { range ->
				match.start >= range.startOffset && match.start < range.endOffset
			} ?: return
			transcriptScrollController.scrollComponentIntoView(blockRange.component)
			return
		}

		transcriptScrollController.scrollTextOffsetIntoView(match.start)
	}

	private fun componentTextRangeFor(match: SearchMatch): ComponentMappedTextRange? {
		componentBlockRanges.forEach { blockRange ->
			blockRange.textRanges.forEach { textRange ->
				if (match.start >= textRange.transcriptStartOffset && match.end <= textRange.transcriptEndOffset) {
					val startOffset = textRange.componentStartOffset + (match.start - textRange.transcriptStartOffset)
					val endOffset = textRange.componentStartOffset + (match.end - textRange.transcriptStartOffset)
					return ComponentMappedTextRange(textRange.textComponent, startOffset, endOffset)
				}
			}
		}
		return null
	}

	private fun applyComponentFallbackHighlight(match: SearchMatch) {
		val blockRange = componentBlockRanges.firstOrNull { range ->
			match.start >= range.startOffset && match.start < range.endOffset
		} ?: return
		componentFallbackHighlightedBorders.putIfAbsent(blockRange.component, blockRange.component.border)
		blockRange.component.border = BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(Color(255, 213, 79), 2),
			BorderFactory.createEmptyBorder(5, 4, 5, 4)
		)
		blockRange.component.repaint()
	}

	private fun currentRenderedText(): String {
		if (currentRenderMode() == TranscriptRenderMode.COMPONENT) {
			return componentRenderedText
		}

		val document = chatArea.document
		return document.getText(0, document.length)
	}
}

private data class ComponentSearchHighlightTag(
	val textComponent: JTextComponent,
	val highlightTag: Any
)

private data class ComponentMappedTextRange(
	val textComponent: JTextComponent,
	val startOffset: Int,
	val endOffset: Int
)

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
