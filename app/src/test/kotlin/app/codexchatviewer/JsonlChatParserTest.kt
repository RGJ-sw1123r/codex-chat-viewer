package app.codexchatviewer

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.Comparator
import kotlin.io.path.writeBytes

class JsonlChatParserTest {
	private lateinit var tempDir: Path

	@BeforeEach
	fun setUpTempDir() {
		val baseDir = Path.of("build", "tmp", "test-temp").toAbsolutePath()
		Files.createDirectories(baseDir)
		tempDir = Files.createTempDirectory(baseDir, "jsonl-parser-")
	}

	@AfterEach
	fun tearDownTempDir() {
		Files.walk(tempDir)
			.sorted(Comparator.reverseOrder())
			.forEach(Files::deleteIfExists)
	}

	@Test
	fun agentsInjectedInstructionsAreClassifiedAsContext() {
		val file = writeJsonl(
			"""{"timestamp":"2026-04-25T06:16:04.894Z","type":"event_msg","payload":{"type":"user_message","message":"# AGENTS.md instructions for /workspace/sample\n\n<INSTRUCTIONS>\n# AGENTS.md\n\nProject instructions here.\n</INSTRUCTIONS>"}}"""
		)

		val parsed = JsonlChatParser.parse(file.toFile())
		val rendered = ChatRenderer.render(file.toFile(), null, parsed)

		assertTrue(rendered.contains("[CONTEXT]\nAGENTS.md project instructions loaded"))
		assertFalse(rendered.contains("[YOU]\n# AGENTS.md instructions for"))
	}

	@Test
	fun structuredTaskBodyIsClassifiedAsTask() {
		val file = writeJsonl(
			"""{"timestamp":"2026-04-25T06:16:04.894Z","type":"event_msg","payload":{"type":"user_message","message":"# 002-2 - Viewer Wrapping and Message Type Filters\n\n## Goal\n\nImplement a viewer readability and filtering polish.\n\nRead `AGENTS.md` first.\n\n## Constraints\n\nDo not commit or push yet.\n\n## Verification\n\nRun:\n\n```cmd\n.\\gradlew.bat test\n```"}}"""
		)

		val parsed = JsonlChatParser.parse(file.toFile())
		val rendered = ChatRenderer.render(file.toFile(), null, parsed)

		assertTrue(rendered.contains("[TASK]\nTask or prompt instructions loaded"))
		assertFalse(rendered.contains("[YOU]\n# 002-2 - Viewer Wrapping and Message Type Filters"))
	}

	@Test
	fun eventMsgUserMessageIsRenderedAsYou() {
		val file = writeJsonl(
			"""{"timestamp":"2026-04-25T06:16:04.894Z","type":"event_msg","payload":{"type":"user_message","message":"open the file"}}"""
		)

		val parsed = JsonlChatParser.parse(file.toFile())
		val rendered = ChatRenderer.render(file.toFile(), null, parsed)

		assertTrue(rendered.contains("[YOU]\nopen the file"))
		assertEquals(1, parsed.parsedCandidates)
	}

	@Test
	fun duplicateUserMessageAcrossEventShapesIsRenderedOnce() {
		val file = writeJsonl(
			"""
			{"timestamp":"2026-04-25T06:16:04.894Z","type":"response_item","payload":{"type":"message","role":"user","content":[{"type":"input_text","text":"same user text"}]}}
			{"timestamp":"2026-04-25T06:16:04.894Z","type":"event_msg","payload":{"type":"user_message","message":"same user text","images":[],"local_images":[],"text_elements":[]}}
			""".trimIndent()
		)

		val parsed = JsonlChatParser.parse(file.toFile())
		val rendered = ChatRenderer.render(file.toFile(), null, parsed)

		assertEquals(2, parsed.parsedCandidates)
		assertEquals(1, "\\[YOU\\]\\nsame user text".toRegex().findAll(rendered).count())
	}

	@Test
	fun identicalUserMessagesWithDifferentIdsAreNotCollapsed() {
		val file = writeJsonl(
			"""
			{"timestamp":"2026-04-25T06:16:04.894Z","type":"response_item","payload":{"type":"message","id":"msg_u1","role":"user","content":[{"type":"input_text","text":"repeat"}]}}
			{"timestamp":"2026-04-25T06:16:05.894Z","type":"response_item","payload":{"type":"message","id":"msg_u2","role":"user","content":[{"type":"input_text","text":"repeat"}]}}
			""".trimIndent()
		)

		val parsed = JsonlChatParser.parse(file.toFile())
		val rendered = ChatRenderer.render(file.toFile(), null, parsed)

		assertEquals(2, parsed.parsedCandidates)
		assertEquals(2, "\\[YOU\\]\\nrepeat".toRegex().findAll(rendered).count())
	}

	@Test
	fun longNormalHumanPromptStillRendersAsYou() {
		val file = writeJsonl(
			"""{"timestamp":"2026-04-25T06:16:04.894Z","type":"response_item","payload":{"type":"message","role":"user","content":[{"type":"input_text","text":"Please inspect the schema-related files carefully:\n- frontend/prisma/schema.prisma\n- db/schema.sql\n- src/main/resources/schema.sql\nThen summarize the differences and propose the safest migration order."}]}}"""
		)

		val parsed = JsonlChatParser.parse(file.toFile())
		val rendered = ChatRenderer.render(file.toFile(), null, parsed)

		assertTrue(rendered.contains("[YOU]\nPlease inspect the schema-related files carefully:"))
		assertFalse(rendered.contains("[TASK]\nTask or prompt instructions loaded"))
	}

	@Test
	fun responseItemAssistantMessageIsRenderedAsCodex() {
		val file = writeJsonl(
			"""{"timestamp":"2026-04-25T06:16:18.951Z","type":"response_item","payload":{"type":"message","role":"assistant","content":[{"type":"output_text","text":"I will inspect the repo first."}]}}"""
		)

		val parsed = JsonlChatParser.parse(file.toFile())
		val rendered = ChatRenderer.render(file.toFile(), null, parsed)

		assertTrue(rendered.contains("[CODEX]\nI will inspect the repo first."))
		assertEquals(1, parsed.parsedCandidates)
	}

	@Test
	fun utf8KoreanUserMessageRendersCorrectly() {
		val file = writeJsonlUtf8(
			"""{"timestamp":"2026-04-25T06:16:04.894Z","type":"event_msg","payload":{"type":"user_message","message":"\uC548\uB155\uD558\uC138\uC694, \uD30C\uC77C\uC744 \uC5F4\uC5B4 \uC8FC\uC138\uC694."}}"""
		)

		val parsed = JsonlChatParser.parse(file.toFile())
		val rendered = ChatRenderer.render(file.toFile(), null, parsed)

		assertTrue(rendered.contains("[YOU]\n안녕하세요, 파일을 열어 주세요."))
	}

	@Test
	fun utf8CodexMessageRendersCorrectly() {
		val file = writeJsonlUtf8(
			"""{"timestamp":"2026-04-25T06:16:18.951Z","type":"response_item","payload":{"type":"message","role":"assistant","content":[{"type":"output_text","text":"Caf\u00E9 \uD655\uC778 \uC644\uB8CC \uD83D\uDE42"}]}}"""
		)

		val parsed = JsonlChatParser.parse(file.toFile())
		val rendered = ChatRenderer.render(file.toFile(), null, parsed)

		assertTrue(rendered.contains("[CODEX]\nCafé 확인 완료 🙂"))
	}

	@Test
	fun responseItemFunctionCallIsRenderedAsToolCall() {
		val file = writeJsonl(
			"""{"timestamp":"2026-04-25T06:16:18.955Z","type":"response_item","payload":{"type":"function_call","name":"shell_command","arguments":"{\"command\":\"Get-ChildItem -Force\",\"workdir\":\"E:\\\\repo\"}","call_id":"call_123"}}"""
		)

		val parsed = JsonlChatParser.parse(file.toFile())
		val rendered = ChatRenderer.render(file.toFile(), null, parsed)

		assertTrue(rendered.contains("[TOOL CALL]\nshell_command: Get-ChildItem -Force"))
	}

	@Test
	fun execCommandEndIsRenderedAsToolResult() {
		val file = writeJsonl(
			"""{"timestamp":"2026-04-25T06:16:20.189Z","type":"event_msg","payload":{"type":"exec_command_end","call_id":"call_123","command":["powershell.exe","-Command","rg --files"],"aggregated_output":"App.kt","exit_code":0,"status":"completed"}}"""
		)

		val parsed = JsonlChatParser.parse(file.toFile())
		val rendered = ChatRenderer.render(file.toFile(), null, parsed)

		assertTrue(rendered.contains("[TOOL RESULT]\nCommand: powershell.exe -Command rg --files"))
		assertTrue(rendered.contains("Exit code: 0"))
		assertTrue(rendered.contains("App.kt"))
	}

	@Test
	fun functionCallOutputIsRenderedAsToolResult() {
		val file = writeJsonl(
			"""{"timestamp":"2026-04-25T06:16:20.253Z","type":"response_item","payload":{"type":"function_call_output","call_id":"call_123","output":"Exit code: 0\nOutput:\nApp.kt"}}"""
		)

		val parsed = JsonlChatParser.parse(file.toFile())
		val rendered = ChatRenderer.render(file.toFile(), null, parsed)

		assertTrue(rendered.contains("[TOOL RESULT]\nExit code: 0"))
	}

	@Test
	fun duplicateCodexMessageAcrossEventShapesIsRenderedOnce() {
		val file = writeJsonl(
			"""
			{"timestamp":"2026-04-25T06:16:29.620Z","type":"event_msg","payload":{"type":"agent_message","message":"same assistant text","phase":"commentary"}}
			{"timestamp":"2026-04-25T06:16:29.620Z","type":"response_item","payload":{"type":"message","role":"assistant","content":[{"type":"output_text","text":"same assistant text"}],"phase":"commentary"}}
			""".trimIndent()
		)

		val parsed = JsonlChatParser.parse(file.toFile())
		val rendered = ChatRenderer.render(file.toFile(), null, parsed)

		assertEquals(2, parsed.parsedCandidates)
		assertEquals(1, "\\[CODEX\\]\\nsame assistant text".toRegex().findAll(rendered).count())
	}

	@Test
	fun duplicateToolResultsWithSameCallIdAreRenderedOnce() {
		val file = writeJsonl(
			"""
			{"timestamp":"2026-04-25T06:16:20.189Z","type":"event_msg","payload":{"type":"exec_command_end","call_id":"call_123","command":["powershell.exe","-Command","rg --files"],"aggregated_output":"App.kt","exit_code":0,"status":"completed"}}
			{"timestamp":"2026-04-25T06:16:20.313Z","type":"response_item","payload":{"type":"function_call_output","call_id":"call_123","output":"Exit code: 0\nOutput:\nApp.kt"}}
			""".trimIndent()
		)

		val parsed = JsonlChatParser.parse(file.toFile())
		val rendered = ChatRenderer.render(file.toFile(), null, parsed)

		assertEquals(2, parsed.parsedCandidates)
		assertEquals(1, "\\[TOOL RESULT\\]".toRegex().findAll(rendered).count())
	}

	@Test
	fun identicalCodexMessagesWithDifferentIdsAreNotCollapsed() {
		val file = writeJsonl(
			"""
			{"timestamp":"2026-04-25T06:16:29.620Z","type":"response_item","payload":{"type":"message","id":"msg_1","role":"assistant","content":[{"type":"output_text","text":"repeat"}]}}
			{"timestamp":"2026-04-25T06:16:30.620Z","type":"response_item","payload":{"type":"message","id":"msg_2","role":"assistant","content":[{"type":"output_text","text":"repeat"}]}}
			""".trimIndent()
		)

		val parsed = JsonlChatParser.parse(file.toFile())
		val rendered = ChatRenderer.render(file.toFile(), null, parsed)

		assertEquals(2, parsed.parsedCandidates)
		assertEquals(2, "\\[CODEX\\]\\nrepeat".toRegex().findAll(rendered).count())
	}

	@Test
	fun developerMessagesRemainIgnored() {
		val file = writeJsonl(
			"""{"timestamp":"2026-04-25T06:16:04.893Z","type":"response_item","payload":{"type":"message","role":"developer","content":[{"type":"input_text","text":"internal instruction"}]}}"""
		)

		val parsed = JsonlChatParser.parse(file.toFile())
		val rendered = ChatRenderer.render(file.toFile(), null, parsed)

		assertEquals(0, parsed.parsedCandidates)
		assertEquals(1, parsed.ignoredLines)
		assertTrue(rendered.contains("Observed event types:"))
		assertTrue(rendered.contains("- response_item/message: 1"))
	}

	@Test
	fun malformedJsonLinesDoNotCrashParsing() {
		val file = writeJsonl(
			"""
			{"type":"event_msg","payload":{"type":"user_message","message":"safe line"}}
			{"type":"response_item"
			""".trimIndent()
		)

		val parsed = JsonlChatParser.parse(file.toFile())
		val rendered = ChatRenderer.render(file.toFile(), null, parsed)

		assertEquals(1, parsed.parsedCandidates)
		assertEquals(1, parsed.malformedLines)
		assertTrue(rendered.contains("[YOU]\nsafe line"))
	}

	@Test
	fun emptyFilesReturnSafeResult() {
		val file = writeJsonl("")

		val parsed = JsonlChatParser.parse(file.toFile())
		val rendered = ChatRenderer.render(file.toFile(), null, parsed)

		assertEquals(0, parsed.parsedCandidates)
		assertTrue(rendered.contains("No renderable chat messages found in this JSONL file."))
	}

	@Test
	fun userAndCodexBlocksRemainSeparateForRealisticEnvelopeShapes() {
		val file = writeJsonl(
			"""
			{"timestamp":"2026-04-25T06:16:04.894Z","type":"event_msg","payload":{"type":"user_message","message":"question"}}
			{"timestamp":"2026-04-25T06:16:18.951Z","type":"response_item","payload":{"type":"message","role":"assistant","content":[{"type":"output_text","text":"answer"}]}}
			""".trimIndent()
		)

		val parsed = JsonlChatParser.parse(file.toFile())
		val rendered = ChatRenderer.render(file.toFile(), "123e4567-e89b-12d3-a456-426614174000", parsed)

		assertTrue(rendered.contains("[YOU]\nquestion\n\n[CODEX]\nanswer"))
		assertTrue(rendered.contains("Session ID: 123e4567-e89b-12d3-a456-426614174000"))
	}

	private fun writeJsonl(contents: String): Path {
		val file = tempDir.resolve("sample.jsonl")
		file.writeBytes(contents.toByteArray(StandardCharsets.UTF_8))
		return file
	}

	private fun writeJsonlUtf8(contents: String): Path = writeJsonl(contents)
}
