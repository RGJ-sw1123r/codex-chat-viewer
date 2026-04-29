package app.codexchatviewer

import java.awt.Component
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import javax.swing.JFileChooser
import javax.swing.JOptionPane
import javax.swing.filechooser.FileNameExtensionFilter

data class MarkdownExportRequest(
	val sourceFile: File,
	val sessionId: String?,
	val chatLog: ParsedChatLog,
	val initialDirectory: File?
)

sealed class MarkdownExportOutcome {
	data object Cancelled : MarkdownExportOutcome()
	data class Success(val file: File, val noticeMessage: String) : MarkdownExportOutcome()
	data class Failure(val noticeMessage: String) : MarkdownExportOutcome()
}

class MarkdownExportController(
	private val parent: Component?,
	private val fallbackDirectory: () -> File,
	private val chooserFactory: (File) -> JFileChooser = ::JFileChooser,
	private val overwriteConfirmer: (Component?, File) -> Boolean = ::confirmOverwrite,
	private val explorerSelector: (File) -> Unit = ::openExplorerSelection,
	private val markdownWriter: (File, String) -> Unit = ::writeMarkdownFile
) {
	fun export(request: MarkdownExportRequest): MarkdownExportOutcome {
		val chooser = createChooser(request)
		val result = chooser.showSaveDialog(parent)
		if (result != JFileChooser.APPROVE_OPTION) {
			return MarkdownExportOutcome.Cancelled
		}

		val targetFile = ensureMarkdownExtension(chooser.selectedFile ?: return MarkdownExportOutcome.Cancelled)
		if (targetFile.exists() && !overwriteConfirmer(parent, targetFile)) {
			return MarkdownExportOutcome.Cancelled
		}

		return try {
			val markdown = MarkdownTranscriptExporter.export(
				sourceFile = request.sourceFile,
				sessionId = request.sessionId,
				chatLog = request.chatLog
			)
			markdownWriter(targetFile, markdown)
			explorerSelector(targetFile)
			MarkdownExportOutcome.Success(
				file = targetFile,
				noticeMessage = "Markdown exported to ${targetFile.absolutePath}"
			)
		} catch (exception: Exception) {
			MarkdownExportOutcome.Failure(
				noticeMessage = "Markdown export failed: ${exception.message ?: exception.javaClass.simpleName}"
			)
		}
	}

	internal fun createChooser(request: MarkdownExportRequest): JFileChooser {
		val initialDirectory = request.sourceFile.parentFile ?: request.initialDirectory ?: fallbackDirectory()
		return chooserFactory(initialDirectory).apply {
			dialogTitle = "Export Markdown"
			fileFilter = FileNameExtensionFilter("Markdown files (*.md)", "md")
			setAcceptAllFileFilterUsed(true)
			selectedFile = suggestedMarkdownExportFile(request.sourceFile, currentDirectory)
		}
	}

	internal fun suggestedMarkdownExportFile(sourceFile: File, directory: File?): File {
		val baseDirectory = directory ?: sourceFile.parentFile ?: fallbackDirectory()
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

	internal fun ensureMarkdownExtension(file: File): File {
		return if (file.name.lowercase().endsWith(".md")) file else File(file.parentFile, file.name + ".md")
	}

	private fun defaultMarkdownExportName(sourceFile: File): String {
		return sourceFile.nameWithoutExtension + ".md"
	}

	companion object {
		private fun confirmOverwrite(parent: Component?, file: File): Boolean {
			val result = JOptionPane.showConfirmDialog(
				parent,
				"${file.name} already exists.\nDo you want to overwrite it?",
				"Overwrite Markdown Export",
				JOptionPane.YES_NO_OPTION,
				JOptionPane.WARNING_MESSAGE
			)
			return result == JOptionPane.YES_OPTION
		}

		private fun writeMarkdownFile(file: File, markdown: String) {
			file.parentFile?.mkdirs()
			Files.writeString(file.toPath(), markdown, StandardCharsets.UTF_8)
		}

		private fun openExplorerSelection(file: File) {
			try {
				ProcessBuilder("explorer.exe", "/select,", file.absolutePath)
					.start()
			} catch (_: Exception) {
				// Export success should not depend on Explorer opening successfully.
			}
		}
	}
}
