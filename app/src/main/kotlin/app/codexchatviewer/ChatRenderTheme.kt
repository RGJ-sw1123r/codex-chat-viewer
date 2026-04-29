package app.codexchatviewer

import java.awt.Color
import java.awt.Font

data class ChatTextStyle(
	val color: Color,
	val bold: Boolean = false,
	val fontFamily: String = Font.MONOSPACED,
	val fontSize: Int = 14,
	val backgroundColor: Color? = null
)

enum class ChatBlockAlignment {
	LEFT,
	RIGHT,
	CENTER
}

data class ChatBlockStyle(
	val labelStyle: ChatTextStyle,
	val contentStyle: ChatTextStyle,
	val alignment: ChatBlockAlignment = ChatBlockAlignment.LEFT,
	val leftIndent: Float = 0f,
	val rightIndent: Float = 0f,
	val firstLineIndent: Float = 0f,
	val spaceAbove: Float = 0f,
	val spaceBelow: Float = 0f,
	val horizontalPadding: Int = 0,
	val minimumCardWidth: Int = 0
)

data class ChatToggleMarkers(
	val expanded: String,
	val collapsed: String
)

data class ChatRenderTheme(
	val name: String,
	val backgroundColor: Color,
	val foregroundColor: Color,
	val viewerFont: Font,
	val headerStyle: ChatTextStyle,
	val metadataStyle: ChatTextStyle,
	val bodyStyle: ChatTextStyle,
	val separatorStyle: ChatTextStyle,
	val toggleMarkers: ChatToggleMarkers,
	val blockStyles: Map<RenderedEntryKind, ChatBlockStyle>
) {
	fun blockStyleFor(type: RenderedEntryKind): ChatBlockStyle {
		return blockStyles.getValue(type)
	}
}

object ChatRenderThemes {
	val availableThemeNames = listOf(
		"Terminal Style",
		"Markdown Style",
		"DM Style",
		"Messenger Style"
	)

	val terminalStyle = buildTerminalTheme()
	val markdownStyle = buildMarkdownTheme()
	val dmStyle = buildDmTheme()
	val messengerStyle = buildMessengerTheme()

	fun byName(name: String): ChatRenderTheme {
		return when (name) {
			terminalStyle.name -> terminalStyle
			markdownStyle.name -> markdownStyle
			dmStyle.name -> dmStyle
			messengerStyle.name -> messengerStyle
			else -> terminalStyle
		}
	}

	private fun buildTerminalTheme(): ChatRenderTheme {
		val terminalBackground = Color(20, 20, 20)
		val terminalForeground = Color(230, 230, 230)
		val terminalHeader = Color(180, 180, 180)
		val terminalMetadata = Color(150, 150, 150)
		val terminalSeparator = Color(135, 135, 135)
		return ChatRenderTheme(
			name = "Terminal Style",
			backgroundColor = terminalBackground,
			foregroundColor = terminalForeground,
			viewerFont = Font(Font.MONOSPACED, Font.PLAIN, 14),
			headerStyle = ChatTextStyle(
				color = terminalHeader,
				bold = true
			),
			metadataStyle = ChatTextStyle(
				color = terminalMetadata
			),
			bodyStyle = ChatTextStyle(
				color = terminalForeground
			),
			separatorStyle = ChatTextStyle(
				color = terminalSeparator
			),
			toggleMarkers = ChatToggleMarkers(
				expanded = "[v]",
				collapsed = "[>]"
			),
			blockStyles = mapOf(
				RenderedEntryKind.CONTEXT to terminalBlock(Color(144, 196, 164)),
				RenderedEntryKind.TASK to terminalBlock(Color(196, 168, 255)),
				RenderedEntryKind.YOU to terminalBlock(Color(255, 196, 107)),
				RenderedEntryKind.CODEX to terminalBlock(Color(120, 200, 255)),
				RenderedEntryKind.TOOL_CALL to terminalBlock(Color(166, 201, 132)),
				RenderedEntryKind.TOOL_RESULT to terminalBlock(Color(180, 180, 180)),
				RenderedEntryKind.SYSTEM to terminalBlock(terminalSeparator)
			)
		)
	}

	private fun buildMarkdownTheme(): ChatRenderTheme {
		val pageBackground = Color(247, 248, 250)
		val bodyText = Color(36, 41, 47)
		val mutedText = Color(101, 109, 118)
		val subtleBorder = Color(208, 215, 222)
		val mutedLabel = Color(87, 96, 106)
		val mutedPanel = Color(246, 248, 250)
		val accentTask = Color(116, 76, 9)
		val accentYou = Color(9, 105, 218)
		val accentCodex = Color(31, 136, 61)
		val accentTool = Color(130, 80, 223)
		val taskPanel = Color(255, 248, 225)
		val messagePanel = Color(255, 255, 255)
		val secondaryBody = Color(73, 80, 87)
		return ChatRenderTheme(
			name = "Markdown Style",
			backgroundColor = pageBackground,
			foregroundColor = Color(31, 35, 40),
			viewerFont = Font(Font.SANS_SERIF, Font.PLAIN, 14),
			headerStyle = ChatTextStyle(
				color = bodyText,
				bold = true,
				fontFamily = Font.SANS_SERIF,
				fontSize = 18
			),
			metadataStyle = ChatTextStyle(
				color = mutedText,
				fontFamily = Font.SANS_SERIF,
				fontSize = 12
			),
			bodyStyle = ChatTextStyle(
				color = bodyText,
				fontFamily = Font.SANS_SERIF,
				fontSize = 14
			),
			separatorStyle = ChatTextStyle(
				color = subtleBorder,
				fontFamily = Font.SANS_SERIF,
				fontSize = 12
			),
			toggleMarkers = ChatToggleMarkers(
				expanded = "v",
				collapsed = ">"
			),
			blockStyles = mapOf(
				RenderedEntryKind.CONTEXT to markdownDocumentBlock(
					labelColor = mutedLabel,
					contentColor = secondaryBody,
					backgroundColor = mutedPanel
				),
				RenderedEntryKind.TASK to markdownDocumentBlock(
					labelColor = accentTask,
					contentColor = secondaryBody,
					backgroundColor = taskPanel
				),
				RenderedEntryKind.YOU to markdownDocumentBlock(
					labelColor = accentYou,
					contentColor = bodyText,
					backgroundColor = messagePanel
				),
				RenderedEntryKind.CODEX to markdownDocumentBlock(
					labelColor = accentCodex,
					contentColor = bodyText,
					backgroundColor = messagePanel
				),
				RenderedEntryKind.TOOL_CALL to markdownDocumentBlock(
					labelColor = accentTool,
					contentColor = bodyText,
					backgroundColor = mutedPanel
				),
				RenderedEntryKind.TOOL_RESULT to markdownDocumentBlock(
					labelColor = mutedLabel,
					contentColor = bodyText,
					backgroundColor = mutedPanel
				),
				RenderedEntryKind.SYSTEM to markdownDocumentBlock(
					labelColor = mutedLabel,
					contentColor = secondaryBody,
					backgroundColor = mutedPanel
				)
			)
		)
	}

	private fun buildDmTheme(): ChatRenderTheme {
		val pageBackground = Color(255, 255, 255)
		val bodyText = Color(28, 32, 38)
		val headerText = Color(95, 107, 122)
		val metadataText = Color(114, 124, 136)
		val separatorText = Color(206, 214, 223)
		val metaLabel = Color(101, 111, 122)
		val metaPanel = Color(241, 244, 247)
		val metaContent = Color(98, 108, 120)
		val youLabel = Color(59, 94, 146)
		val youBubble = Color(219, 233, 252)
		val codexLabel = Color(81, 91, 104)
		val codexBubble = Color(247, 248, 250)
		val toolLabel = Color(115, 124, 135)
		val toolPanel = Color(236, 239, 243)
		val toolContent = Color(110, 119, 130)
		return ChatRenderTheme(
			name = "DM Style",
			backgroundColor = pageBackground,
			foregroundColor = bodyText,
			viewerFont = Font(Font.SANS_SERIF, Font.PLAIN, 14),
			headerStyle = ChatTextStyle(
				color = headerText,
				bold = true,
				fontFamily = Font.SANS_SERIF,
				fontSize = 16
			),
			metadataStyle = ChatTextStyle(
				color = metadataText,
				fontFamily = Font.SANS_SERIF,
				fontSize = 12
			),
			bodyStyle = ChatTextStyle(
				color = bodyText,
				fontFamily = Font.SANS_SERIF
			),
			separatorStyle = ChatTextStyle(
				color = separatorText,
				fontFamily = Font.SANS_SERIF,
				fontSize = 12
			),
			toggleMarkers = ChatToggleMarkers(
				expanded = "v",
				collapsed = ">"
			),
			blockStyles = mapOf(
				RenderedEntryKind.CONTEXT to centeredConversationMetaBlock(
					labelColor = metaLabel,
					backgroundColor = metaPanel,
					contentColor = metaContent
				),
				RenderedEntryKind.TASK to centeredConversationMetaBlock(
					labelColor = metaLabel,
					backgroundColor = metaPanel,
					contentColor = metaContent
				),
				RenderedEntryKind.YOU to alignedConversationBubbleBlock(
					labelColor = youLabel,
					contentColor = bodyText,
					backgroundColor = youBubble,
					alignment = ChatBlockAlignment.RIGHT
				),
				RenderedEntryKind.CODEX to alignedConversationBubbleBlock(
					labelColor = codexLabel,
					contentColor = bodyText,
					backgroundColor = codexBubble,
					alignment = ChatBlockAlignment.LEFT
				),
				RenderedEntryKind.TOOL_CALL to centeredConversationMetaBlock(
					labelColor = toolLabel,
					backgroundColor = toolPanel,
					contentColor = toolContent
				),
				RenderedEntryKind.TOOL_RESULT to centeredConversationMetaBlock(
					labelColor = toolLabel,
					backgroundColor = toolPanel,
					contentColor = toolContent
				),
				RenderedEntryKind.SYSTEM to centeredConversationMetaBlock(
					labelColor = metaLabel,
					backgroundColor = metaPanel,
					contentColor = metaContent
				)
			)
		)
	}

	private fun buildMessengerTheme(): ChatRenderTheme {
		val pageBackground = Color(242, 235, 221)
		val bodyText = Color(48, 42, 35)
		val headerText = Color(91, 80, 68)
		val metadataText = Color(117, 107, 95)
		val separatorText = Color(199, 185, 154)
		val metaLabel = Color(107, 98, 85)
		val metaPanel = Color(233, 225, 210)
		val toolPanel = Color(221, 216, 200)
		val youLabel = Color(92, 67, 20)
		val youBubble = Color(244, 214, 138)
		val codexLabel = Color(82, 73, 63)
		val codexBubble = Color(255, 253, 247)
		return ChatRenderTheme(
			name = "Messenger Style",
			backgroundColor = pageBackground,
			foregroundColor = bodyText,
			viewerFont = Font(Font.SANS_SERIF, Font.PLAIN, 14),
			headerStyle = ChatTextStyle(
				color = headerText,
				bold = true,
				fontFamily = Font.SANS_SERIF,
				fontSize = 16
			),
			metadataStyle = ChatTextStyle(
				color = metadataText,
				fontFamily = Font.SANS_SERIF,
				fontSize = 12
			),
			bodyStyle = ChatTextStyle(
				color = bodyText,
				fontFamily = Font.SANS_SERIF
			),
			separatorStyle = ChatTextStyle(
				color = separatorText,
				fontFamily = Font.SANS_SERIF,
				fontSize = 12
			),
			toggleMarkers = ChatToggleMarkers(
				expanded = "v",
				collapsed = ">"
			),
			blockStyles = mapOf(
				RenderedEntryKind.CONTEXT to centeredConversationMetaBlock(
					labelColor = metaLabel,
					backgroundColor = metaPanel
				),
				RenderedEntryKind.TASK to centeredConversationMetaBlock(
					labelColor = metaLabel,
					backgroundColor = metaPanel
				),
				RenderedEntryKind.YOU to alignedConversationBubbleBlock(
					labelColor = youLabel,
					contentColor = bodyText,
					backgroundColor = youBubble,
					alignment = ChatBlockAlignment.RIGHT
				),
				RenderedEntryKind.CODEX to alignedConversationBubbleBlock(
					labelColor = codexLabel,
					contentColor = bodyText,
					backgroundColor = codexBubble,
					alignment = ChatBlockAlignment.LEFT
				),
				RenderedEntryKind.TOOL_CALL to centeredConversationMetaBlock(
					labelColor = metadataText,
					backgroundColor = toolPanel
				),
				RenderedEntryKind.TOOL_RESULT to centeredConversationMetaBlock(
					labelColor = metadataText,
					backgroundColor = toolPanel
				),
				RenderedEntryKind.SYSTEM to centeredConversationMetaBlock(
					labelColor = metaLabel,
					backgroundColor = metaPanel
				)
			)
		)
	}

	private fun terminalBlock(labelColor: Color): ChatBlockStyle {
		return ChatBlockStyle(
			labelStyle = ChatTextStyle(
				color = labelColor,
				bold = true
			),
			contentStyle = ChatTextStyle(
				color = Color(230, 230, 230)
			)
		)
	}

	private fun markdownDocumentBlock(labelColor: Color, contentColor: Color, backgroundColor: Color): ChatBlockStyle {
		return ChatBlockStyle(
			labelStyle = ChatTextStyle(
				color = labelColor,
				bold = true,
				fontFamily = Font.SANS_SERIF,
				fontSize = 14,
				backgroundColor = backgroundColor
			),
			contentStyle = ChatTextStyle(
				color = contentColor,
				fontFamily = Font.SANS_SERIF,
				fontSize = 14,
				backgroundColor = backgroundColor
			),
			alignment = ChatBlockAlignment.LEFT,
			spaceAbove = 10f,
			spaceBelow = 8f
		)
	}

	private fun centeredConversationMetaBlock(
		labelColor: Color,
		backgroundColor: Color,
		contentColor: Color = Color(117, 107, 95)
	): ChatBlockStyle {
		return ChatBlockStyle(
			labelStyle = conversationText(
				color = labelColor,
				bold = true,
				fontSize = 12,
				backgroundColor = backgroundColor
			),
			contentStyle = conversationText(
				color = contentColor,
				fontSize = 12,
				backgroundColor = backgroundColor
			),
			alignment = ChatBlockAlignment.CENTER,
			leftIndent = 120f,
			rightIndent = 120f,
			spaceAbove = 8f,
			spaceBelow = 2f,
			horizontalPadding = 2,
			minimumCardWidth = 34
		)
	}

	private fun alignedConversationBubbleBlock(
		labelColor: Color,
		contentColor: Color,
		backgroundColor: Color,
		alignment: ChatBlockAlignment
	): ChatBlockStyle {
		return ChatBlockStyle(
			labelStyle = conversationText(
				color = labelColor,
				bold = true,
				backgroundColor = backgroundColor
			),
			contentStyle = conversationText(
				color = contentColor,
				backgroundColor = backgroundColor
			),
			alignment = alignment,
			leftIndent = if (alignment == ChatBlockAlignment.RIGHT) 140f else 14f,
			rightIndent = if (alignment == ChatBlockAlignment.RIGHT) 14f else 140f,
			firstLineIndent = 0f,
			spaceAbove = 8f,
			spaceBelow = 2f,
			horizontalPadding = 2,
			minimumCardWidth = 26
		)
	}

	private fun conversationText(
		color: Color,
		bold: Boolean = false,
		fontSize: Int = 14,
		backgroundColor: Color
	): ChatTextStyle {
		return ChatTextStyle(
			color = color,
			bold = bold,
			fontFamily = Font.SANS_SERIF,
			fontSize = fontSize,
			backgroundColor = backgroundColor
		)
	}
}
