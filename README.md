# Codex Chat Viewer

A local desktop viewer for OpenAI Codex CLI session logs.

Codex Chat Viewer turns `rollout-*.jsonl` session logs into readable chat-style conversations.

> Status: early development

## Why?

Codex CLI is useful, but its local JSONL session logs are not easy to read directly.

This project aims to make those logs easier to review, search, and export by presenting them as readable conversations.

## Features

### Implemented

- Kotlin/JVM Gradle application
- Swing-based desktop app shell
- Basic window layout
- Theme selector placeholder
- JSONL open button placeholder
- Markdown export button placeholder

### Planned

- Open Codex `rollout-*.jsonl` files
- Parse user and assistant messages
- Render sessions as readable Q/A conversations
- Support multiple visual themes:
  - Terminal Style
  - Markdown Style
  - DM Style
  - Talk Style
  - Messenger Style
- Export parsed conversations to Markdown
- Export parsed conversations to HTML
- Auto-detect local Codex session directories
- Search by session ID or keyword

## Screenshots

Coming soon.

## Requirements

- JDK 21 or later
- Gradle Wrapper included

The project uses the included Gradle Wrapper, so you do not need to install Gradle manually after cloning.

## Run

### Windows

```cmd
gradlew.bat run
```

## License

MIT License
