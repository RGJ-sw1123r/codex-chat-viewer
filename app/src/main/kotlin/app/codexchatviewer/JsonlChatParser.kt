package app.codexchatviewer

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import java.io.File
import java.io.InputStreamReader
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

private val objectMapper = ObjectMapper()

data class ParsedChatLog(
	val entries: List<RenderedEntry>,
	val parsedCandidates: Int,
	val ignoredLines: Int,
	val malformedLines: Int,
	val observedEventCounts: Map<String, Int>
)

data class ChatEntryFilter(
	val showYou: Boolean = true,
	val showCodex: Boolean = true,
	val showToolCall: Boolean = true,
	val showToolResult: Boolean = true,
	val showMeta: Boolean = true
) {
	fun includes(kind: RenderedEntryKind): Boolean {
		return when (kind) {
			RenderedEntryKind.YOU -> showYou
			RenderedEntryKind.CODEX -> showCodex
			RenderedEntryKind.TOOL_CALL -> showToolCall
			RenderedEntryKind.TOOL_RESULT -> showToolResult
			RenderedEntryKind.SYSTEM, RenderedEntryKind.CONTEXT, RenderedEntryKind.TASK -> showMeta
		}
	}
}

data class RenderedEntry(
	val kind: RenderedEntryKind,
	val content: String
)

enum class RenderedEntryKind(val label: String) {
	CONTEXT("[CONTEXT]"),
	TASK("[TASK]"),
	YOU("[YOU]"),
	CODEX("[CODEX]"),
	TOOL_CALL("[TOOL CALL]"),
	TOOL_RESULT("[TOOL RESULT]"),
	SYSTEM("[SYSTEM]")
}

fun ParsedChatLog.filtered(filter: ChatEntryFilter): ParsedChatLog {
	return copy(entries = entries.filter { filter.includes(it.kind) })
}

private data class ParsedCandidate(
	val entry: RenderedEntry,
	val stableKey: String?,
	val normalizedText: String,
	val source: CandidateSource,
	val timestamp: String?
)

private enum class CandidateSource(val priority: Int) {
	RESPONSE_MESSAGE(4),
	RESPONSE_TOOL_RESULT(4),
	EVENT_TOOL_RESULT(3),
	RESPONSE_TOOL_CALL(3),
	EVENT_USER_MESSAGE(3),
	EVENT_AGENT_MESSAGE(2),
	EVENT_SYSTEM(2),
	FALLBACK(1)
}

object JsonlChatParser {
	fun parse(file: File): ParsedChatLog {
		if (!file.isFile) {
			return ParsedChatLog(emptyList(), 0, 0, 0, emptyMap())
		}

		val decoder = StandardCharsets.UTF_8.newDecoder()
			.onMalformedInput(CodingErrorAction.REPLACE)
			.onUnmappableCharacter(CodingErrorAction.REPLACE)

		var parsedCandidates = 0
		var ignoredLines = 0
		var malformedLines = 0
		val candidates = mutableListOf<ParsedCandidate>()
		val observedEventCounts = linkedMapOf<String, Int>()

		InputStreamReader(file.inputStream().buffered(), decoder).useLines { lines ->
			lines.forEach { rawLine ->
				val line = rawLine.trim()
				if (line.isEmpty()) {
					return@forEach
				}

				val root = try {
					objectMapper.readTree(line)
				} catch (_: Exception) {
					malformedLines += 1
					return@forEach
				}

				observeEventType(root, observedEventCounts)

				val extractedCandidates = extractEntries(root)
				if (extractedCandidates.isEmpty()) {
					ignoredLines += 1
				} else {
					candidates += extractedCandidates
					parsedCandidates += extractedCandidates.size
				}
			}
		}

		val entries = dedupeCandidates(candidates).map(ParsedCandidate::entry)

		return ParsedChatLog(
			entries = entries,
			parsedCandidates = parsedCandidates,
			ignoredLines = ignoredLines,
			malformedLines = malformedLines,
			observedEventCounts = observedEventCounts.toMap()
		)
	}

	private fun dedupeCandidates(candidates: List<ParsedCandidate>): List<ParsedCandidate> {
		if (candidates.isEmpty()) {
			return emptyList()
		}

		val stableSelections = linkedMapOf<String, ParsedCandidate>()
		val stableOrder = mutableListOf<String>()
		val pending = mutableListOf<ParsedCandidate>()

		for (candidate in candidates) {
			val stableKey = candidate.stableKey
			if (stableKey.isNullOrBlank()) {
				pending += candidate
				continue
			}

			val existing = stableSelections[stableKey]
			if (existing == null) {
				stableSelections[stableKey] = candidate
				stableOrder += stableKey
			} else if (shouldReplace(existing, candidate)) {
				stableSelections[stableKey] = candidate
			}
		}

		val combined = buildList {
			addAll(pending)
			stableOrder.mapNotNullTo(this) { stableSelections[it] }
		}.sortedBy { candidates.indexOf(it) }

		val deduped = mutableListOf<ParsedCandidate>()
		for (candidate in combined) {
			val previous = deduped.lastOrNull()
			if (previous != null && shouldSuppressAdjacentDuplicate(previous, candidate)) {
				if (shouldReplace(previous, candidate)) {
					deduped[deduped.lastIndex] = candidate
				}
				continue
			}
			deduped += candidate
		}

		return deduped
	}

	private fun shouldReplace(existing: ParsedCandidate, replacement: ParsedCandidate): Boolean {
		if (replacement.source.priority != existing.source.priority) {
			return replacement.source.priority > existing.source.priority
		}

		return replacement.entry.content.length > existing.entry.content.length
	}

	private fun shouldSuppressAdjacentDuplicate(previous: ParsedCandidate, current: ParsedCandidate): Boolean {
		if (previous.entry.kind != current.entry.kind) {
			return false
		}

		if (previous.normalizedText != current.normalizedText) {
			return false
		}

		if (previous.stableKey != null && current.stableKey != null) {
			return previous.stableKey == current.stableKey
		}

		if (previous.timestamp != null && current.timestamp != null && previous.timestamp == current.timestamp) {
			return true
		}

		return isKnownDuplicatePair(previous.source, current.source)
	}

	private fun isKnownDuplicatePair(first: CandidateSource, second: CandidateSource): Boolean {
		return setOf(first, second) == setOf(CandidateSource.EVENT_USER_MESSAGE, CandidateSource.RESPONSE_MESSAGE) ||
			setOf(first, second) == setOf(CandidateSource.EVENT_AGENT_MESSAGE, CandidateSource.RESPONSE_MESSAGE) ||
			setOf(first, second) == setOf(CandidateSource.EVENT_TOOL_RESULT, CandidateSource.RESPONSE_TOOL_RESULT)
	}

	private fun observeEventType(root: JsonNode, observedEventCounts: MutableMap<String, Int>) {
		val topType = root.getText("type")
		val payloadType = root.get("payload")?.getText("type")
		val key = when {
			!topType.isNullOrBlank() && !payloadType.isNullOrBlank() -> "$topType/$payloadType"
			!payloadType.isNullOrBlank() -> payloadType
			!topType.isNullOrBlank() -> topType
			else -> "unknown"
		}

		observedEventCounts[key] = (observedEventCounts[key] ?: 0) + 1
	}

	private fun extractEntries(root: JsonNode): List<ParsedCandidate> {
		extractFromEnvelope(root)?.let { return it }

		return extractFromNode(root)
			.ifEmpty { extractFromContainer(root, "message") }
			.ifEmpty { extractFromContainer(root, "item") }
			.ifEmpty { extractFromContainer(root, "delta") }
			.ifEmpty { extractFromResponseOutput(root) }
	}

	private fun extractFromEnvelope(root: JsonNode): List<ParsedCandidate>? {
		val topType = root.getText("type") ?: return null
		val payload = root.get("payload")
		val timestamp = root.getText("timestamp")

		return when (topType) {
			"event_msg" -> payload?.let { extractFromEventPayload(it, timestamp) } ?: emptyList()
			"response_item" -> payload?.let { extractFromResponsePayload(it, timestamp) } ?: emptyList()
			"session_meta" -> payload?.let { extractSessionMeta(it, timestamp) } ?: emptyList()
			else -> null
		}
	}

	private fun extractFromEventPayload(payload: JsonNode, timestamp: String?): List<ParsedCandidate> {
		val payloadType = payload.getText("type") ?: return emptyList()

		return when (payloadType) {
			"user_message" -> payload.getText("message")
				?.let { classifyUserFacingText(it) }
				?.let { listOf(createCandidate(it.kind, it.content, CandidateSource.EVENT_USER_MESSAGE, timestamp, payloadType)) }
				?: emptyList()

			"agent_message" -> payload.getText("message")
				?.let { listOf(createCandidate(RenderedEntryKind.CODEX, it, CandidateSource.EVENT_AGENT_MESSAGE, timestamp, payloadType)) }
				?: emptyList()

			"exec_command_end" -> buildExecCommandResult(payload)
				?.let { listOf(createCandidate(it.kind, it.content, CandidateSource.EVENT_TOOL_RESULT, timestamp, payloadType, payload.getText("call_id"))) }
				?: emptyList()

			"patch_apply_end" -> buildPatchApplyResult(payload)
				?.let { listOf(createCandidate(it.kind, it.content, CandidateSource.EVENT_TOOL_RESULT, timestamp, payloadType, payload.getText("call_id"))) }
				?: emptyList()

			"task_started", "task_complete" -> buildTaskSystemEntry(payload)
				?.let { listOf(createCandidate(it.kind, it.content, CandidateSource.EVENT_SYSTEM, timestamp, payloadType, payload.getText("turn_id"))) }
				?: emptyList()

			else -> emptyList()
		}
	}

	private fun extractFromResponsePayload(payload: JsonNode, timestamp: String?): List<ParsedCandidate> {
		val payloadType = payload.getText("type") ?: return emptyList()

		return when (payloadType) {
			"message" -> extractRoleBasedEntry(payload)
				?.let {
					listOf(
						createCandidate(
							it.kind,
							it.content,
							CandidateSource.RESPONSE_MESSAGE,
							timestamp,
							payloadType,
							payload.getText("id")
						)
					)
				}
				?: emptyList()
			"function_call", "custom_tool_call" -> buildToolCallEntry(payload)
				?.let {
					listOf(
						createCandidate(
							it.kind,
							it.content,
							CandidateSource.RESPONSE_TOOL_CALL,
							timestamp,
							payloadType,
							payload.getText("call_id")
						)
					)
				}
				?: emptyList()
			"function_call_output", "custom_tool_call_output" -> buildToolResultEntry(payload)
				?.let {
					listOf(
						createCandidate(
							it.kind,
							it.content,
							CandidateSource.RESPONSE_TOOL_RESULT,
							timestamp,
							payloadType,
							payload.getText("call_id")
						)
					)
				}
				?: emptyList()
			else -> emptyList()
		}
	}

	private fun extractSessionMeta(payload: JsonNode, timestamp: String?): List<ParsedCandidate> {
		val parts = listOfNotNull(
			payload.getText("id")?.let { "Session: $it" },
			payload.getText("model_provider")?.let { provider ->
				payload.getText("cli_version")?.let { version -> "Provider: $provider, CLI: $version" } ?: "Provider: $provider"
			}
		)

		if (parts.isEmpty()) {
			return emptyList()
		}

		return listOf(
			createCandidate(
				RenderedEntryKind.SYSTEM,
				parts.joinToString("\n"),
				CandidateSource.EVENT_SYSTEM,
				timestamp,
				"session_meta",
				payload.getText("id")
			)
		)
	}

	private fun buildExecCommandResult(payload: JsonNode): RenderedEntry? {
		val commandSummary = buildCommandSummary(payload)
		val exitCode = payload.get("exit_code")?.takeIf(JsonNode::canConvertToInt)?.asInt()
		val status = payload.getText("status")
		val output = listOfNotNull(
			payload.getText("aggregated_output"),
			payload.getText("formatted_output"),
			payload.getText("stdout"),
			payload.getText("stderr")
		).firstOrNull { it.isNotBlank() }

		val summary = buildList {
			if (!commandSummary.isNullOrBlank()) {
				add("Command: $commandSummary")
			}
			if (exitCode != null || !status.isNullOrBlank()) {
				add(
					listOfNotNull(
						status?.takeIf { it.isNotBlank() }?.let { "Status: $it" },
						exitCode?.let { "Exit code: $it" }
					).joinToString(", ")
				)
			}
			output?.let { add(truncateForRender(it, 1200)) }
		}
			.filter { it.isNotBlank() }
			.joinToString("\n")
			.trim()

		return summary.takeIf { it.isNotEmpty() }?.let { RenderedEntry(RenderedEntryKind.TOOL_RESULT, it) }
	}

	private fun buildPatchApplyResult(payload: JsonNode): RenderedEntry? {
		val status = payload.getText("status") ?: return null
		return RenderedEntry(RenderedEntryKind.TOOL_RESULT, "Patch apply status: $status")
	}

	private fun buildTaskSystemEntry(payload: JsonNode): RenderedEntry? {
		val type = payload.getText("type") ?: return null
		val turnId = payload.getText("turn_id")
		val text = listOfNotNull(
			type.replace('_', ' ').replaceFirstChar(Char::uppercase),
			turnId?.let { "Turn: $it" }
		).joinToString("\n")
		return RenderedEntry(RenderedEntryKind.SYSTEM, text)
	}

	private fun extractFromContainer(root: JsonNode, fieldName: String): List<ParsedCandidate> {
		val nested = root.get(fieldName) ?: return emptyList()
		return extractFromNode(nested)
	}

	private fun extractFromResponseOutput(root: JsonNode): List<ParsedCandidate> {
		val output = root.get("output") ?: return emptyList()
		if (!output.isArray) {
			return emptyList()
		}

		return output.flatMap { extractFromNode(it) }
	}

	private fun extractFromNode(node: JsonNode): List<ParsedCandidate> {
		if (!node.isObject) {
			return emptyList()
		}

		extractRoleBasedEntry(node)?.let {
			return listOf(createCandidate(it.kind, it.content, CandidateSource.FALLBACK, null, node.getText("type"), node.getText("id")))
		}
		extractTypedToolEntry(node)?.let {
			return listOf(createCandidate(it.kind, it.content, CandidateSource.FALLBACK, null, node.getText("type"), node.getText("call_id") ?: node.getText("id")))
		}
		extractTypedRoleEntry(node)?.let {
			return listOf(createCandidate(it.kind, it.content, CandidateSource.FALLBACK, null, node.getText("type"), node.getText("id")))
		}

		return emptyList()
	}

	private fun createCandidate(
		kind: RenderedEntryKind,
		content: String,
		source: CandidateSource,
		timestamp: String?,
		typeName: String?,
		stableId: String? = null
	): ParsedCandidate {
		val normalized = normalizeForDedupe(content)
		val stableKey = stableId?.takeIf { it.isNotBlank() }?.let { "${kind.name}:$it" }
		return ParsedCandidate(
			entry = RenderedEntry(kind, content),
			stableKey = stableKey,
			normalizedText = normalized,
			source = source,
			timestamp = timestamp
		)
	}

	private fun normalizeForDedupe(value: String): String {
		return value
			.lowercase()
			.replace("\r\n", "\n")
			.replace(Regex("\\s+"), " ")
			.trim()
	}

	private fun extractRoleBasedEntry(node: JsonNode): RenderedEntry? {
		val role = node.getText("role")?.lowercase() ?: return null
		val content = extractTextContent(node) ?: return null

		return when (role) {
			"user" -> classifyUserFacingText(content)
			"assistant", "model" -> RenderedEntry(RenderedEntryKind.CODEX, content)
			"tool" -> RenderedEntry(RenderedEntryKind.TOOL_RESULT, content)
			"system" -> RenderedEntry(RenderedEntryKind.SYSTEM, content)
			else -> null
		}
	}

	private fun extractTypedRoleEntry(node: JsonNode): RenderedEntry? {
		val type = node.getText("type")?.lowercase() ?: return null
		val content = extractTextContent(node) ?: return null

		return when {
			type.contains("user") -> classifyUserFacingText(content)
			type.contains("assistant") || type.contains("model") || type.contains("agent") -> RenderedEntry(RenderedEntryKind.CODEX, content)
			type.contains("system") || type.contains("session") -> RenderedEntry(RenderedEntryKind.SYSTEM, content)
			else -> null
		}
	}

	private fun classifyUserFacingText(content: String): RenderedEntry {
		return when {
			looksLikeAgentsInjectedContext(content) -> {
				RenderedEntry(RenderedEntryKind.CONTEXT, "AGENTS.md project instructions loaded")
			}
			looksLikeInjectedTaskBody(content) -> {
				RenderedEntry(RenderedEntryKind.TASK, "Task or prompt instructions loaded")
			}
			else -> {
				RenderedEntry(RenderedEntryKind.YOU, content)
			}
		}
	}

	private fun looksLikeAgentsInjectedContext(content: String): Boolean {
		val normalized = content.replace("\r\n", "\n").trim()
		return normalized.startsWith("# AGENTS.md instructions for") ||
			(normalized.contains("<INSTRUCTIONS>") && normalized.contains("# AGENTS.md"))
	}

	private fun looksLikeInjectedTaskBody(content: String): Boolean {
		val normalized = content.replace("\r\n", "\n").trim()
		val startsLikeTaskDoc = normalized.startsWith("# 00") || normalized.startsWith("# 0") || normalized.startsWith("# Task")
		val hasGoal = normalized.contains("\n## Goal")
		val hasVerification = normalized.contains("\n## Verification")
		val hasConstraints = normalized.contains("\n## Constraints")
		val hasAgentsStep = normalized.contains("Read `AGENTS.md` first.")
		val hasNoCommitGuard = normalized.contains("Do not commit or push yet.")
		val hasTaskLanguage = normalized.contains("## Requested Changes") ||
			normalized.contains("## Current State") ||
			normalized.contains("## Targets") ||
			normalized.contains("## Target File") ||
			normalized.contains("## Expected Result")

		return (startsLikeTaskDoc && hasGoal && (hasVerification || hasConstraints)) ||
			(hasAgentsStep && hasGoal && hasNoCommitGuard && (hasVerification || hasConstraints || hasTaskLanguage))
	}

	private fun extractTypedToolEntry(node: JsonNode): RenderedEntry? {
		val type = node.getText("type")?.lowercase() ?: return null

		return when {
			type.contains("tool_call") || type.contains("function_call") || type.contains("command") && !type.contains("end") && !type.contains("output") -> {
				buildToolCallEntry(node)
			}
			type.contains("tool_result") || type.contains("function_result") || type.contains("command_result") || type.contains("output") || type.contains("end") -> {
				buildToolResultEntry(node)
			}
			else -> null
		}
	}

	private fun buildToolCallEntry(node: JsonNode): RenderedEntry? {
		val name = node.getText("name") ?: node.getText("tool_name")
		val commandSummary = extractCommandFromArguments(node.getText("arguments"))
			?: extractCommandFromInput(node.getText("input"))
			?: buildCommandSummary(node)

		val summary = listOfNotNull(
			name?.takeIf { it.isNotBlank() }?.let {
				if (!commandSummary.isNullOrBlank() && commandSummary != it) "$it: $commandSummary" else it
			},
			commandSummary?.takeIf { name.isNullOrBlank() }
		)
			.firstOrNull()
			?.trim()
			?.takeIf { it.isNotEmpty() }
			?: return null

		return RenderedEntry(RenderedEntryKind.TOOL_CALL, truncateForRender(summary, 500))
	}

	private fun buildToolResultEntry(node: JsonNode): RenderedEntry? {
		val output = listOfNotNull(
			node.getText("output"),
			node.getText("aggregated_output"),
			node.getText("formatted_output"),
			node.getText("stdout"),
			node.getText("stderr"),
			node.getText("result"),
			extractTextContent(node)
		)
			.firstOrNull { it.isNotBlank() }
			?.trim()
			?.takeIf { it.isNotEmpty() }

		if (output != null) {
			return RenderedEntry(RenderedEntryKind.TOOL_RESULT, truncateForRender(output, 1200))
		}

		val fallback = buildCommandSummary(node)
		return fallback?.let { RenderedEntry(RenderedEntryKind.TOOL_RESULT, truncateForRender(it, 500)) }
	}

	private fun extractCommandFromArguments(arguments: String?): String? {
		if (arguments.isNullOrBlank()) {
			return null
		}

		return try {
			val node = objectMapper.readTree(arguments)
			node.getText("command")
				?: node.getText("path")
				?: node.getText("comment")
				?: node.fieldNames().asSequence().firstOrNull()?.let { firstField ->
					"$firstField=${node.get(firstField)?.asText()?.trim().orEmpty()}".trim()
				}
		} catch (_: Exception) {
			truncateForRender(arguments, 200)
		}
	}

	private fun extractCommandFromInput(input: String?): String? {
		if (input.isNullOrBlank()) {
			return null
		}

		return input.lineSequence()
			.map(String::trim)
			.firstOrNull { it.isNotEmpty() }
			?.let { truncateForRender(it, 200) }
	}

	private fun buildCommandSummary(node: JsonNode): String? {
		val commandNode = node.get("command")
		return when {
			commandNode?.isArray == true -> commandNode.mapNotNull { it.asText().trim().ifEmpty { null } }
				.joinToString(" ")
				.trim()
				.ifEmpty { null }
			else -> node.getText("command")
		}
	}

	private fun extractTextContent(node: JsonNode): String? {
		val directFields = listOf("content", "text", "message", "output", "result", "summary")
		for (field in directFields) {
			val value = node.get(field) ?: continue
			val text = extractTextValue(value)
			if (!text.isNullOrBlank()) {
				return text
			}
		}

		return null
	}

	private fun extractTextValue(node: JsonNode): String? {
		return when {
			node.isTextual -> node.asText().trim().ifEmpty { null }
			node.isArray -> {
				node.mapNotNull { part ->
					when {
						part.isTextual -> part.asText().trim().ifEmpty { null }
						part.isObject -> {
							part.getText("text")
								?: part.get("text")?.getText("value")
								?: part.getText("content")
								?: part.getText("message")
						}
						else -> null
					}
				}
					.joinToString("\n")
					.trim()
					.ifEmpty { null }
			}
			node.isObject -> {
				node.getText("text")
					?: node.get("text")?.getText("value")
					?: node.getText("value")
					?: node.getText("content")
					?: node.getText("message")
			}
			else -> null
		}
	}

	private fun truncateForRender(value: String, maxLength: Int): String {
		val normalized = value.replace("\r\n", "\n").trim()
		if (normalized.length <= maxLength) {
			return normalized
		}

		return normalized.take(maxLength - 3).trimEnd() + "..."
	}

	private fun JsonNode.getText(fieldName: String): String? {
		val child = get(fieldName) ?: return null
		return when {
			child.isTextual -> child.asText().trim().ifEmpty { null }
			else -> null
		}
	}
}
