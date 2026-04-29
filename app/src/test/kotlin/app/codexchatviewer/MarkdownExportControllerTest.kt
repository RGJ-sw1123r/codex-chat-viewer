package app.codexchatviewer

import java.io.File
import javax.swing.JFileChooser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class MarkdownExportControllerTest {
	@TempDir
	lateinit var tempDir: File

	@Test
	fun suggestedFileUsesDefaultNameAndIncrementsOnConflict() {
		val sourceFile = File(tempDir, "rollout-test.jsonl")
		sourceFile.writeText("{}", Charsets.UTF_8)
		File(tempDir, "rollout-test.md").writeText("existing", Charsets.UTF_8)
		val controller = controller()

		val suggested = controller.suggestedMarkdownExportFile(sourceFile, tempDir)

		assertEquals(File(tempDir, "rollout-test (1).md"), suggested)
	}

	@Test
	fun ensureMarkdownExtensionAppendsMdOnlyWhenNeeded() {
		val controller = controller()

		assertEquals(File(tempDir, "chat.md"), controller.ensureMarkdownExtension(File(tempDir, "chat")))
		assertEquals(File(tempDir, "chat.md"), controller.ensureMarkdownExtension(File(tempDir, "chat.md")))
		assertEquals(File(tempDir, "chat.MD"), controller.ensureMarkdownExtension(File(tempDir, "chat.MD")))
	}

	@Test
	fun exportWritesMarkdownAndReturnsSuccessNotice() {
		val sourceFile = File(tempDir, "rollout-test.jsonl")
		sourceFile.writeText("{}", Charsets.UTF_8)
		val selectedFile = File(tempDir, "export-result")
		var explorerOpenedFor: File? = null
		val controller = MarkdownExportController(
			parent = null,
			fallbackDirectory = { tempDir },
			chooserFactory = { initialDirectory ->
				FakeFileChooser(initialDirectory, selectedFile, JFileChooser.APPROVE_OPTION)
			},
			overwriteConfirmer = { _, _ -> true },
			explorerSelector = { explorerOpenedFor = it }
		)

		val result = controller.export(
			MarkdownExportRequest(
				sourceFile = sourceFile,
				sessionId = "session-123",
				chatLog = ParsedChatLog(
					entries = listOf(RenderedEntry(RenderedEntryKind.YOU, "Export this")),
					parsedCandidates = 1,
					ignoredLines = 0,
					malformedLines = 0,
					observedEventCounts = emptyMap()
				),
				initialDirectory = tempDir
			)
		)

		assertTrue(result is MarkdownExportOutcome.Success)
		result as MarkdownExportOutcome.Success
		assertEquals(File(tempDir, "export-result.md"), result.file)
		assertEquals("Markdown exported to ${result.file.absolutePath}", result.noticeMessage)
		assertTrue(result.file.readText(Charsets.UTF_8).contains("[YOU]\nExport this"))
		assertEquals(result.file, explorerOpenedFor)
	}

	@Test
	fun exportReturnsCancelledWhenDialogIsClosed() {
		val sourceFile = File(tempDir, "rollout-test.jsonl")
		sourceFile.writeText("{}", Charsets.UTF_8)
		val controller = MarkdownExportController(
			parent = null,
			fallbackDirectory = { tempDir },
			chooserFactory = { initialDirectory ->
				FakeFileChooser(initialDirectory, File(tempDir, "ignored.md"), JFileChooser.CANCEL_OPTION)
			}
		)

		val result = controller.export(
			MarkdownExportRequest(
				sourceFile = sourceFile,
				sessionId = null,
				chatLog = ParsedChatLog(emptyList(), 0, 0, 0, emptyMap()),
				initialDirectory = tempDir
			)
		)

		assertEquals(MarkdownExportOutcome.Cancelled, result)
	}

	private fun controller(): MarkdownExportController {
		return MarkdownExportController(
			parent = null,
			fallbackDirectory = { tempDir }
		)
	}
}

private class FakeFileChooser(
	currentDirectory: File,
	private val chosenFile: File,
	private val dialogResult: Int
) : JFileChooser(currentDirectory) {
	override fun showSaveDialog(parent: java.awt.Component?): Int {
		selectedFile = chosenFile
		return dialogResult
	}
}
