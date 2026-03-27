# Ganglia

**Ganglia** is a high-performance, non-blocking Java 17 Agent framework built on **Vert.x 5.0.6**. It follows a **Hexagonal (Ports & Adapters)** architecture, prioritizing simplicity, robustness, and transparency for building autonomous agents that can be seamlessly integrated into any third-party application.

![Status](https://img.shields.io/badge/status-In_Development-yellow)
![CI](https://github.com/stream-iori/ganglia/actions/workflows/ci.yml/badge.svg)
![Version](https://img.shields.io/badge/version-0.1.7--SNAPSHOT-blue)
![Java](https://img.shields.io/badge/Java-17-orange)
![Vert.x](https://img.shields.io/badge/Vert.x-5.0.6-purple)
![License](https://img.shields.io/badge/license-MIT-green)

> [!WARNING]
> **Status: Early Development.** Ganglia is currently in an active development phase. API stability is not guaranteed, and it is **not yet suitable for production use**.

---

## 🚀 Key Features

- **Single Control Loop (ReAct):** A powerful, flat reasoning loop inspired by modern coding agents (e.g., Claude Code), designed for iterative problem-solving and tool execution.
- **Hexagonal Architecture:** Complete decoupling of core reasoning (Kernel) from model providers and technical implementations (Infrastructure), ensuring high testability and platform independence.
- **Aspect-Oriented Hooks:** A unified `AgentInterceptor` pipeline allowing for transparent context enrichment, security validation, and post-turn reflection without cluttering core logic.
- **Advanced Memory Subsystem:**
  - **MemoryStore:** Hybrid search (text + metadata) for long-term knowledge retrieval.
  - **Observation Compression:** Real-time, LLM-powered summarization of large tool outputs (>4000 chars) to prevent context window bloat.
  - **Progressive Disclosure:** Automatic injection of memory indexes into prompts, allowing agents to recall full details on demand using unique IDs.
  - **Timeline Ledger:** An automated system medical record (`TIMELINE.md`) tracking every major decision and architectural change.
- **Native LLM Gateways:** Native implementations of OpenAI and Anthropic protocols using Vert.x WebClient. **Zero third-party SDK dependencies.**
- **Memory as Code:** Transparent, file-based memory system using Markdown (`.ganglia/memory/MEMORY.md` and Daily Journals).
- **Modern React WebUI:** A high-performance dashboard built with **React 18**, **Zustand**, and **shadcn/ui** for real-time monitoring, continuous timeline tracking, and high-fidelity code/diff review.

---

## 🏗 Architecture

Ganglia is organized into four clean hexagonal layers:

1. **API / Adapter Layer**: Entry points like `WebUiVerticle` (WebSockets) and `TerminalUI` (Console).
2. **Kernel Layer**: The heart of the reasoning loop, task scheduling, interceptor pipeline, and state evolution.
3. **Port Layer**: Domain models (`Message`, `Turn`, `Context`, `MemoryEntry`) and strict service interfaces.
4. **Infrastructure Layer**: Technical implementations (LLM Gateways, File System, Memory Persistence).

For more details, see the [Architecture Documentation](docs/ARCHITECTURE.md).

---

## ⚡ Quick Start with `just`

The project uses the `just` command runner for common development tasks.

| Command          | Description                                           |
|:-----------------|:------------------------------------------------------|
| `just setup`     | Initialize everything (Maven install + NPM install)   |
| `just backend`   | Start the Java Backend (Interactive WebUI Demo)       |
| `just frontend`  | Start the Frontend Dev Server (Vite)                  |
| `just ui-watch`  | Build UI in watch mode (updates `dist/` continuously) |
| `just build-all` | Full production build (UI + Backend JAR)              |
| `just test`      | Run all tests (Backend & Frontend)                    |

---

## 🕹 Demos

Ganglia provides several ways to interact with the reasoning engine:

### 1. WebUI Interactive Demo (`work.ganglia.example.WebUIDemo`)

This is the recommended way to explore the agent's reasoning, tool use, and memory systems with a rich visual dashboard.

```bash
# Start the full environment (Backend + Frontend)
just backend
```

Then open `http://localhost:8080`. For frontend development with HMR, use `just dev-all` and open `http://localhost:5173`.

### 2. Interactive Terminal Chat (`work.ganglia.example.InteractiveChatDemo`)

A feature-rich TTY interface for chatting with the agent directly from your terminal, featuring box-drawing responses and markdown rendering.

```bash
# Start the terminal REPL
just chat
```

> [!NOTE]
> Ensure you have an environment variable `OPENAI_API_KEY` or `ANTHROPIC_API_KEY` set before running the demos. You can also configure providers in `.ganglia/config.json`.

---

## ⚙️ Configuration

Ganglia is configured via a JSON file, typically located at `ganglia-example/.ganglia/config.json` in your project root. If the file doesn't exist, it will be automatically created with default values on the first run.

### Example `.ganglia/config.json`

```json
{
  "agent": {
    "maxIterations": 10,
    "compressionThreshold": 0.7,
    "instructionFile": "GANGLIA.md"
  },
  "models": {
    "primary": {
      "name": "gpt-4o",
      "type": "openai",
      "apiKey": "your-api-key-here",
      "temperature": 0.0,
      "maxTokens": 4096,
      "stream": true
    },
    "utility": {
      "name": "gpt-4o-mini",
      "type": "openai",
      "apiKey": "your-api-key-here",
      "temperature": 0.0,
      "maxTokens": 2048,
      "stream": false
    }
  }
}
```

### Key Configuration Fields

- **`agent`**:
  - `maxIterations`: Maximum number of reasoning steps per turn.
  - `instructionFile`: A Markdown file (e.g., `GANGLIA.md`) containing custom system instructions and persona rules.
- **`models`**: Supports `primary` and `utility` (used for background tasks like compression).
  - `type`: `openai`, `anthropic`, or `gemini` (via OpenAI compatibility).
  - `apiKey`: Your provider API key. Environment variables like `OPENAI_API_KEY` are also supported.

### Custom Instructions (`GANGLIA.md`)

You can provide structured system instructions by creating a `GANGLIA.md` file. Ganglia parses **H2 headers** to extract individual context fragments:

```markdown
## [Coding Style] (Priority: 8, Mandatory)
Always use Google Java Style for all code changes.

## [Security] (Priority: 10, Mandatory)
Never log or commit secrets or API keys.
```

---

## 🔌 Communication Protocol

Ganglia uses **JSON-RPC 2.0** over **WebSockets** for all external client communication.

### Client-to-Server Methods

- **`SYNC`**: Request full session history and workspace configuration.
- **`START`**: Begin a new reasoning turn with a natural language prompt.
- **`LIST_FILES`**: Manually request a fresh workspace file tree snapshot.
- **`READ_FILE`**: Retrieve the content of a specific file (with syntax highlighting metadata).
- **`RESPOND_ASK`**: Provide user authorization or selection for an interactive tool.
- **`CANCEL`**: Abort current agent execution and tool tasks.
- **`RETRY`**: Re-trigger the last failed or interrupted turn.

### Server-to-Client Notifications

- **`server_event`**: Unified stream for thoughts, tool starts, results, and system errors.
- **`tty_event`**: High-frequency streaming output for shell commands.

---

## 📜 License

Distributed under the **MIT License**. See `LICENSE` for more information.

© 2026 Ganglia Team. All rights reserved.
