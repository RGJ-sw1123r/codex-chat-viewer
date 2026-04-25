# Codex Chat Viewer

A local desktop viewer for OpenAI Codex CLI session logs.

Codex Chat Viewer opens local `rollout-*.jsonl` files and projects them into readable, filterable, terminal-style conversations.

> Status: early MVP

## Why?

Codex CLI is useful, but its local JSONL session logs are not easy to read directly.

This project aims to make those logs easier to review by separating human prompts, Codex responses, tool calls, tool results, task context, and session metadata into a readable local UI.

## Important Note About Codex Rollout Logs

Codex Chat Viewer reads local Codex CLI `rollout-*.jsonl` session logs.

These files are treated as an observed local session format, not as an official stable public export schema.

This means the parser is based on the rollout structures currently observed in local Codex CLI logs, and those structures may change in future Codex CLI versions.

If OpenAI later provides a stable official session export format, this project may add support for it as a separate input adapter.

## What It Does

Codex Chat Viewer is designed for developers who want to review their local Codex CLI sessions without reading raw JSONL logs.

The app currently focuses on:

- opening local Codex rollout logs
- rendering readable conversation blocks
- distinguishing who or what produced each block
- filtering noisy sections such as tool output or metadata
- keeping everything local

## Features

### Implemented

- Kotlin/JVM Gradle application
- Swing-based desktop app
- Local-first desktop workflow
- JSONL file picker with `*.jsonl` filter
- Safe Codex sessions directory detection:
  - `CODEX_HOME/sessions`
  - `~/.codex/sessions`
  - user home fallback
- Last selected directory remembered during the current app session
- Selected file metadata display
- Conservative Codex session ID detection
- `codex resume <session-id>` display when a session ID is detected
- Copy Resume Command button
- Minimal JSONL parsing for Codex rollout logs
- Speaker/source labels:
  - `[SYSTEM]`
  - `[CONTEXT]`
  - `[TASK]`
  - `[YOU]`
  - `[CODEX]`
  - `[TOOL CALL]`
  - `[TOOL RESULT]`
- Duplicate suppression for known duplicate rollout event shapes
- AGENTS.md / project instruction context separated from direct user prompts
- Task-like prompt document content separated as `[TASK]` when safely identifiable
- UTF-8 file reading
- Unicode-friendly viewer rendering
- Terminal-style dark viewer
- Styled label colors for faster scanning
- Soft-wrapped main viewer without horizontal scrolling
- Compact icon filter toggles:
  - YOU
  - CODEX
  - CALL
  - RESULT
  - META
- Cached parsed entries with filter-based re-rendering
- Markdown export button placeholder

### Planned

- Export the visible filtered transcript to Markdown
- Search within the opened session
- Session list browser for local Codex session directories
- Live tail / watch mode for active Codex sessions
- Additional visual themes:
  - Markdown Style
  - DM Style
  - Talk Style
  - Messenger Style
- Portable Windows zip release
- Optional support for a stable official Codex session export format, if one becomes available

## Screenshots

Coming soon.

## Requirements

- JDK 21 or later
- Windows is the primary development target
- Gradle Wrapper included

The project uses the included Gradle Wrapper, so you do not need to install Gradle manually after cloning.

## Run

### Windows

```cmd
gradlew.bat run
```

## Manual Windows Release Zip

Codex Chat Viewer is intended to ship as a portable Windows zip, not as an installer.

Build the local release artifact with:

```cmd
gradlew.bat packageWindowsReleaseZip
```

The generated files are written under the build output directory:

```text
app/build/release/windows/app-image/
app/build/release/windows/codex-chat-viewer-windows-x64.zip
```

The zip is intended for a manual GitHub Releases upload after a local check. The expected user flow is:

- build `codex-chat-viewer-windows-x64.zip` locally
- extract the zip
- run `Codex Chat Viewer.exe`
- verify the app opens without a separate Java install
- upload the zip to GitHub Releases manually

`jpackage` is required for the release build because the release zip bundles a Java runtime. If `jpackage` is not already on `PATH`, point the build at a full JDK by setting `JPACKAGE_HOME` or `JPACKAGE_EXECUTABLE`.

The source-code zip from GitHub's green `Code` button is not the same as the portable release zip. End users should download the release asset instead of the source archive.

## Development Notes

Codex Chat Viewer reads local Codex CLI session logs from paths such as:

```text
~/.codex/sessions
```

or:

```text
%CODEX_HOME%/sessions
```

The app does not upload logs anywhere.

Codex rollout logs may contain prompts, code, file paths, shell commands, command outputs, tool calls, and private project information. Treat local session files carefully.

## Privacy

This is a local-first utility.

- No network behavior is required for viewing logs.
- Real Codex session logs should not be committed.
- Private local samples should remain ignored.
- Exported files may contain private session content, so review them before sharing.

## Release Direction

The intended release direction is a portable Windows zip:

```text
Download zip
Extract folder
Run Codex Chat Viewer.exe
```

A full installer is not the first target. The portable zip should include a bundled runtime so Java does not need to be installed separately on the target machine.

## License

MIT License
