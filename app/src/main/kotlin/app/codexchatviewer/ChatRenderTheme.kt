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

	val terminalStyle = ChatRenderTheme(
		name = "Terminal Style",
		backgroundColor = Color(20, 20, 20),
		foregroundColor = Color(230, 230, 230),
		viewerFont = Font(Font.MONOSPACED, Font.PLAIN, 14),
		headerStyle = ChatTextStyle(
			color = Color(180, 180, 180),
			bold = true
		),
		metadataStyle = ChatTextStyle(
			color = Color(150, 150, 150)
		),
		bodyStyle = ChatTextStyle(
			color = Color(230, 230, 230)
		),
		separatorStyle = ChatTextStyle(
			color = Color(135, 135, 135)
		),
		toggleMarkers = ChatToggleMarkers(
			expanded = "[v]",
			collapsed = "[>]"
		),
		blockStyles = mapOf(
			RenderedEntryKind.CONTEXT to blockStyle(Color(144, 196, 164)),
			RenderedEntryKind.TASK to blockStyle(Color(196, 168, 255)),
			RenderedEntryKind.YOU to blockStyle(Color(255, 196, 107)),
			RenderedEntryKind.CODEX to blockStyle(Color(120, 200, 255)),
			RenderedEntryKind.TOOL_CALL to blockStyle(Color(166, 201, 132)),
			RenderedEntryKind.TOOL_RESULT to blockStyle(Color(180, 180, 180)),
			RenderedEntryKind.SYSTEM to blockStyle(Color(135, 135, 135))
		)
	)

	val markdownStyle = terminalStyle.copy(name = "Markdown Style")

	val dmStyle = ChatRenderTheme(
		name = "DM Style",
		backgroundColor = Color(255, 255, 255),
		foregroundColor = Color(28, 32, 38),
		viewerFont = Font(Font.SANS_SERIF, Font.PLAIN, 14),
		headerStyle = ChatTextStyle(
			color = Color(95, 107, 122),
			bold = true,
			fontFamily = Font.SANS_SERIF,
			fontSize = 16
		),
		metadataStyle = ChatTextStyle(
			color = Color(114, 124, 136),
			fontFamily = Font.SANS_SERIF,
			fontSize = 12
		),
		bodyStyle = ChatTextStyle(
			color = Color(28, 32, 38),
			fontFamily = Font.SANS_SERIF
		),
		separatorStyle = ChatTextStyle(
			color = Color(206, 214, 223),
			fontFamily = Font.SANS_SERIF,
			fontSize = 12
		),
		toggleMarkers = ChatToggleMarkers(
			expanded = "v",
			collapsed = ">"
		),
		blockStyles = mapOf(
			RenderedEntryKind.CONTEXT to centeredMessengerBlock(
				labelColor = Color(101, 111, 122),
				backgroundColor = Color(241, 244, 247),
				contentColor = Color(98, 108, 120)
			),
			RenderedEntryKind.TASK to centeredMessengerBlock(
				labelColor = Color(101, 111, 122),
				backgroundColor = Color(241, 244, 247),
				contentColor = Color(98, 108, 120)
			),
			RenderedEntryKind.YOU to conversationBubbleBlock(
				labelColor = Color(59, 94, 146),
				contentColor = Color(28, 32, 38),
				backgroundColor = Color(219, 233, 252),
				alignment = ChatBlockAlignment.RIGHT
			),
			RenderedEntryKind.CODEX to conversationBubbleBlock(
				labelColor = Color(81, 91, 104),
				contentColor = Color(28, 32, 38),
				backgroundColor = Color(247, 248, 250),
				alignment = ChatBlockAlignment.LEFT
			),
			RenderedEntryKind.TOOL_CALL to centeredMessengerBlock(
				labelColor = Color(115, 124, 135),
				backgroundColor = Color(236, 239, 243),
				contentColor = Color(110, 119, 130)
			),
			RenderedEntryKind.TOOL_RESULT to centeredMessengerBlock(
				labelColor = Color(115, 124, 135),
				backgroundColor = Color(236, 239, 243),
				contentColor = Color(110, 119, 130)
			),
			RenderedEntryKind.SYSTEM to centeredMessengerBlock(
				labelColor = Color(101, 111, 122),
				backgroundColor = Color(241, 244, 247),
				contentColor = Color(98, 108, 120)
			)
		)
	)

	val messengerStyle = ChatRenderTheme(
		name = "Messenger Style",
		backgroundColor = Color(242, 235, 221),
		foregroundColor = Color(48, 42, 35),
		viewerFont = Font(Font.SANS_SERIF, Font.PLAIN, 14),
		headerStyle = ChatTextStyle(
			color = Color(91, 80, 68),
			bold = true,
			fontFamily = Font.SANS_SERIF,
			fontSize = 16
		),
		metadataStyle = ChatTextStyle(
			color = Color(117, 107, 95),
			fontFamily = Font.SANS_SERIF,
			fontSize = 12
		),
		bodyStyle = ChatTextStyle(
			color = Color(48, 42, 35),
			fontFamily = Font.SANS_SERIF
		),
		separatorStyle = ChatTextStyle(
			color = Color(199, 185, 154),
			fontFamily = Font.SANS_SERIF,
			fontSize = 12
		),
		toggleMarkers = ChatToggleMarkers(
			expanded = "v",
			collapsed = ">"
		),
		blockStyles = mapOf(
			RenderedEntryKind.CONTEXT to centeredMessengerBlock(
				labelColor = Color(107, 98, 85),
				backgroundColor = Color(233, 225, 210)
			),
			RenderedEntryKind.TASK to centeredMessengerBlock(
				labelColor = Color(107, 98, 85),
				backgroundColor = Color(233, 225, 210)
			),
			RenderedEntryKind.YOU to ChatBlockStyle(
				labelStyle = messengerText(
					color = Color(92, 67, 20),
					bold = true,
					backgroundColor = Color(244, 214, 138)
				),
				contentStyle = messengerText(
					color = Color(48, 42, 35),
					backgroundColor = Color(244, 214, 138)
				),
				alignment = ChatBlockAlignment.RIGHT,
				leftIndent = 140f,
				rightIndent = 14f,
				firstLineIndent = 0f,
				spaceAbove = 8f,
				spaceBelow = 2f,
				horizontalPadding = 2,
				minimumCardWidth = 26
			),
			RenderedEntryKind.CODEX to ChatBlockStyle(
				labelStyle = messengerText(
					color = Color(82, 73, 63),
					bold = true,
					backgroundColor = Color(255, 253, 247)
				),
				contentStyle = messengerText(
					color = Color(48, 42, 35),
					backgroundColor = Color(255, 253, 247)
				),
				alignment = ChatBlockAlignment.LEFT,
				leftIndent = 14f,
				rightIndent = 140f,
				firstLineIndent = 0f,
				spaceAbove = 8f,
				spaceBelow = 2f,
				horizontalPadding = 2,
				minimumCardWidth = 26
			),
			RenderedEntryKind.TOOL_CALL to centeredMessengerBlock(
				labelColor = Color(117, 107, 95),
				backgroundColor = Color(221, 216, 200)
			),
			RenderedEntryKind.TOOL_RESULT to centeredMessengerBlock(
				labelColor = Color(117, 107, 95),
				backgroundColor = Color(221, 216, 200)
			),
			RenderedEntryKind.SYSTEM to centeredMessengerBlock(
				labelColor = Color(107, 98, 85),
				backgroundColor = Color(233, 225, 210)
			)
		)
	)

	fun byName(name: String): ChatRenderTheme {
		return when (name) {
			terminalStyle.name -> terminalStyle
			markdownStyle.name -> markdownStyle
			dmStyle.name -> dmStyle
			messengerStyle.name -> messengerStyle
			else -> terminalStyle
		}
	}

	private fun blockStyle(labelColor: Color): ChatBlockStyle {
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

	private fun centeredMessengerBlock(
		labelColor: Color,
		backgroundColor: Color,
		contentColor: Color = Color(117, 107, 95)
	): ChatBlockStyle {
		return ChatBlockStyle(
			labelStyle = messengerText(
				color = labelColor,
				bold = true,
				fontSize = 12,
				backgroundColor = backgroundColor
			),
			contentStyle = messengerText(
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

	private fun conversationBubbleBlock(
		labelColor: Color,
		contentColor: Color,
		backgroundColor: Color,
		alignment: ChatBlockAlignment
	): ChatBlockStyle {
		return ChatBlockStyle(
			labelStyle = messengerText(
				color = labelColor,
				bold = true,
				backgroundColor = backgroundColor
			),
			contentStyle = messengerText(
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

	private fun messengerText(
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
