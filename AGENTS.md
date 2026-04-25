# AGENTS.md

## Project Overview

Codex Chat Viewer is a local desktop viewer for OpenAI Codex CLI session logs.

The app reads Codex `rollout-*.jsonl` files and presents the session as readable chat-style conversations.

## Project Type

- Kotlin/JVM desktop application
- Swing UI
- Gradle-based project
- Local-first utility
- Public GitHub repository

## Application Name

Use the application name consistently:

```text
Codex Chat Viewer
```

Project/repository name:

```text
codex-chat-viewer
```

Do not rename the project or application unless explicitly requested.

## Package Name

Keep the current package name:

```text
app.codexchatviewer
```

Do not change the package name.

## Runtime / Build

The default development run command must keep working:

```cmd
gradlew.bat run
```

Use the Gradle Wrapper for project execution and builds.

Do not assume users have Gradle installed globally.

## Release Direction

The final release target is:

```text
Portable Windows zip
```

Preferred release style:

```text
Download zip
Extract folder
Run Codex Chat Viewer.exe
```

Do not implement a full installer-first flow.

Do not add the full `jpackage` release flow yet unless explicitly requested.

The planned release direction can be documented, but the current focus is MVP functionality.

## Current Status

The Swing app shell already runs.

Implemented baseline:

- Kotlin/JVM Gradle application
- Swing window shell
- Theme selector placeholder
- Open JSONL file picker behavior
- Safe Codex sessions directory detection
- Fallback to the user home directory
- Selected JSONL file path display
- Resume command display, if a session ID is detected safely
- Copy Resume Command button, if a resume command is available
- Compact Selection metadata layout
- Last selected directory remembered during the current app session
- Markdown export button placeholder

## Next Development Steps

Implement features in this order:

1. Open Codex JSONL file
	- Use `JFileChooser`
	- Allow selecting `rollout-*.jsonl`
	- Display selected file path or file name

2. Parse Codex JSONL
	- Read the file line by line
	- Parse JSON safely
	- Extract user and assistant messages
	- Ignore unsupported events for the first MVP

3. Render readable conversation
	- Start with simple Q/A text rendering
	- Keep Terminal Style as the default

4. Export Markdown
	- Export the parsed conversation to `.md`

5. Add theme rendering
	- Terminal Style
	- Markdown Style
	- DM Style
	- Talk Style
	- Messenger Style

6. Prepare portable release flow
	- Add portable zip documentation
	- Add `jpackage` only when the MVP is stable

## UI Direction

Default theme:

```text
Terminal Style
```

Supported theme names:

```text
Terminal Style
Markdown Style
DM Style
Talk Style
Messenger Style
```

Keep the default view developer-friendly.

Chat-style themes should improve readability without hiding the original session structure.

## Privacy Direction

This app must be local-first.

Codex session logs may contain:

- prompts
- code
- file paths
- shell commands
- command outputs
- tool calls
- private project information

Do not upload logs anywhere.

Do not add network features unless explicitly requested.

## Dependency Policy

Do not add unnecessary dependencies.

Swing is preferred for the UI.

Add a JSON library only when needed for JSONL parsing.

If a dependency is added, keep it minimal and justify why it is needed.

## Coding Style

Keep the code simple and readable.

Prefer small, clear classes over premature abstraction.

Do not over-engineer the MVP.

Recommended model structure:

```kotlin
data class ChatMessage(
	val role: ChatRole,
	val content: String
)

enum class ChatRole {
	USER,
	ASSISTANT
}

enum class ChatTheme {
	TERMINAL,
	MARKDOWN,
	DM,
	TALK,
	MESSENGER
}
```

## Encoding and Multilingual Text Handling

- Never judge Korean or Japanese text corruption from terminal rendering alone.
- When editing files that contain non-ASCII text, read and write them only with UTF-8-safe tooling.
- Assume NFC normalization for all text files.
- Before editing any file containing Korean or Japanese text, verify the file is valid UTF-8 and preserve its existing normalization form unless explicitly asked to normalize.
- If a patch fails on a non-ASCII file, do not retry with broad text replacement; inspect the file with Python and make a minimal exact edit.
- Never rewrite an entire multilingual file just to change one line.
- For multilingual content, prefer line-precise edits based on exact file contents read via Python.

## Commit Message Policy

When asked to commit or push, generate the commit message in English in this exact structure:

```text
<type>: <short English summary>

- <detail 1>
- <detail 2>
- <detail 3>
- <detail 4>
```

Rules:

- Use conventional commit types such as `feat`, `fix`, `refactor`, `docs`, `style`, `test`, and `chore`.
- Write the subject line in English.
- Add 1 to 4 concise bullet points in English describing the concrete changes.
- Keep the summary and bullet points specific and ready to use without further editing.
- Do not add extra explanations outside the commit message.

## Git / Repository Notes

This is intended to be a public GitHub project.

Do not commit private files.

The following should remain ignored:

```text
/private/
/.codex/
```

Do not commit real Codex session logs unless they are sanitized samples.

## README Policy

Keep `README.md` user-facing.

README should explain:

- what the app does
- how to run it during development
- planned portable zip release
- privacy/local-first direction

Do not overload README with internal development prompts.

Use this `AGENTS.md` for agent-facing project instructions.
