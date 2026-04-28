package app.codexchatviewer

import java.awt.Color
import java.awt.Font

data class ChatTextStyle(
	val color: Color,
	val bold: Boolean = false,
	val fontFamily: String = Font.MONOSPACED,
	val fontSize: Int = 14
)

data class ChatBlockStyle(
	val labelStyle: ChatTextStyle,
	val contentStyle: ChatTextStyle
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
	val blockStyles: Map<RenderedEntryKind, ChatBlockStyle>
) {
	fun blockStyleFor(type: RenderedEntryKind): ChatBlockStyle {
		return blockStyles.getValue(type)
	}
}

object ChatRenderThemes {
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
}
