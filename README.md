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
  - **MemoryStore:** Persistent observation storage with hybrid search (text + metadata).
  - **Observation Compression:** Real-time, LLM-powered summarization of large tool outputs (>4000 chars) to prevent context window bloat.
  - **Progressive Disclosure:** Automatic injection of memory indexes into prompts, allowing agents to recall full details on demand using unique IDs.
  - **Timeline Ledger:** Automated tracking of every major decision and architectural change in `TIMELINE.md`.
  - **Daily Journals:** Cross-session summaries auto-written to `.ganglia/memory/daily-*.md`.
- **Native LLM Gateways:** Native implementations of OpenAI, Anthropic, and Gemini protocols using Vert.x WebClient. **Zero third-party SDK dependencies.**
- **Pluggable Memory via SPI:** Memory implementations are loaded via `java.util.ServiceLoader`, keeping the core framework clean and extensible.
- **Dynamic Skill System:** Load external capabilities at runtime from filesystem directories or JAR files via a manifest-based skill system.
- **Sub-Agent Orchestration:** Delegate specialized sub-tasks to isolated child agents, with support for DAG-based parallel execution.
- **MCP Support:** Connect to external tool servers via the Model Context Protocol (stdio/SSE).
- **Native Observability (Trace Studio):** A professional-grade, self-developed execution tracer (inspired by Arize Phoenix) that visualizes the hierarchical tree of Agent thoughts, tool calls, MCP interactions, and context compressions with precise metrics and retry tracking.
- **Memory as Code:** Transparent, file-based memory system using Markdown (`.ganglia/memory/MEMORY.md` and Daily Journals).
- **Modern React WebUI:** A high-performance dashboard built with **React 18**, **Zustand**, and **shadcn/ui** for real-time monitoring, continuous timeline tracking, and high-fidelity code/diff review.

---

## 🏗 Architecture

Ganglia is organized into four clean hexagonal layers:

1. **API / Adapter Layer**: Entry points like `WebUiVerticle` (WebSockets) and `TerminalUI` (Console).
2. **Observability Layer**: Independent `ObservabilityVerticle` providing a dedicated Trace Studio dashboard (Port 8081).
3. **Kernel Layer**: The heart of the reasoning loop, task scheduling, interceptor pipeline, and state evolution.
4. **Port Layer**: Domain models (`Message`, `Turn`, `SessionContext`) and strict service interfaces.
5. **Infrastructure Layer**: Technical implementations (LLM Gateways, File System, Memory Persistence).

For more details, see the [Architecture Documentation](docs/ARCHITECTURE.md).

---

## 📦 Modules

| Module                      | Description                                                               |
|:----------------------------|:--------------------------------------------------------------------------|
| `ganglia-harness`           | Core framework: kernel, ports, infrastructure (no memory implementations) |
| `ganglia-local-file-memory` | File-based memory SPI implementation                                      |
| `ganglia-coding`            | Coding agent builder + tools (bash, file-edit, web-fetch)                 |
| `ganglia-observability`     | Dedicated Trace Studio backend and observability API                      |
| `ganglia-terminal`          | JLine 3 terminal UI                                                       |
| `ganglia-web`               | WebSocket + JSON-RPC 2.0 web UI backend                                   |
| `ganglia-webui`             | React 18 multi-page frontend (Coding UI + Trace Studio)                   |
| `ganglia-example`           | Demo applications                                                         |
| `ganglia-swe-bench`         | SWE-bench evaluation with Docker sandboxing                               |
| `integration-test`          | E2E simulation scenarios                                                  |

---

## 🧠 ganglia-harness Capabilities

`ganglia-harness` is the core of the framework. It contains everything needed to build and run an agent.

### Kernel

| Component                                      | Description                                                              |
|:-----------------------------------------------|:-------------------------------------------------------------------------|
| `ReActAgentLoop`                               | Iterative Thought → Action → Observation reasoning loop                  |
| `AgentTaskFactory` / `DefaultAgentTaskFactory` | Maps LLM tool calls to executable `AgentTask` instances                  |
| `InterceptorPipeline`                          | Sequential `AgentInterceptor` hooks for pre/post turn and tool execution |
| `ObservationCompressionHook`                   | Compresses large tool outputs (>4000 chars) via LLM before storing       |
| `TokenAwareTruncator`                          | Prunes message history when token budget is exceeded                     |
| `GraphExecutor` / `DefaultGraphExecutor`       | DAG-based orchestration for parallel sub-agent execution                 |
| `ToDoTools`                                    | Built-in task planning tools (`todo_add`, `todo_list`, `todo_complete`)  |

### LLM Gateways

| Gateway                 | Description                                    |
|:------------------------|:-----------------------------------------------|
| `OpenAIModelGateway`    | Native OpenAI protocol (GPT-4o, etc.)          |
| `AnthropicModelGateway` | Native Anthropic protocol (Claude)             |
| `GeminiModelGateway`    | Google Gemini via OpenAI-compatible API        |
| `FallbackModelGateway`  | Primary + utility model fallback strategy      |
| `RetryingModelGateway`  | Exponential backoff retry wrapper              |
| `ModelGatewayFactory`   | Selects the correct gateway from configuration |

### Built-in Tools

| Tool                 | Description                                                          |
|:---------------------|:---------------------------------------------------------------------|
| `KnowledgeBaseTools` | `add_knowledge`, `search_knowledge` — long-term knowledge management |
| `InteractionTools`   | `ask_user` — pause loop and request user input                       |
| `RecallMemoryTools`  | Query the memory store for past observations                         |
| `SkillTools`         | `list_available_skills`, `activate_skill` — manage dynamic skills    |
| `ToDoTools`          | `todo_add`, `todo_list`, `todo_complete` — in-loop task planning     |

### Prompt Engine & Context Sources

`StandardPromptEngine` composes the system prompt from prioritized `ContextSource` fragments:

| Source                  | Description                                |
|:------------------------|:-------------------------------------------|
| `PersonaContextSource`  | Agent identity and persona                 |
| `MandatesContextSource` | Instructions from `GANGLIA.md`             |
| `EnvironmentSource`     | Runtime environment variables              |
| `FileContextSource`     | Inject contents of specified files         |
| `SkillContextSource`    | Active skill descriptions and tool schemas |
| `ToolContextSource`     | Available tool definitions                 |
| `MemoryContextSource`   | Memory index for progressive disclosure    |
| `DailyContextSource`    | Daily journal fragments                    |
| `ToDoContextSource`     | Pending tasks                              |

Fragments are prioritized (1–10) and pruned to fit the configured token budget.

### Skill System

Skills are external capability packages loadable at runtime:

- **`FileSystemSkillLoader`** — loads skills from `.ganglia/skills/` directories
- **`JarSkillLoader`** — loads skills packaged as JAR files
- **`JavaSkillToolSet`** — executes Java-based skill tools
- **`ScriptSkillToolSet`** — executes script-based skill tools
- **Skill Manifest** — defines `id`, `version`, `name`, `description`, tools, prompt fragments, and activation triggers

### Memory SPI

Memory is pluggable via `java.util.ServiceLoader`:

```
MemorySystemProvider  (SPI in ganglia-harness)
    └── FileSystemMemoryProvider  (impl in ganglia-local-file-memory)
            └── MemorySystem record:
                  MemoryStore, ObservationCompressor, ContextCompressor,
                  TimelineLedger, DailyRecordManager, LongTermMemory,
                  MemoryService, MemoryContextSource
```

### MCP (Model Context Protocol)

- **`McpRegistry`** — registry of connected MCP servers
- **`McpToolSet`** — exposes MCP server tools as a standard `ToolSet`
- **`McpConfigManager`** — loads MCP server config from `.ganglia/.mcp.json`
- Supports stdio and SSE transports with full JSON-RPC 2.0 message handling

### State & Observability

| Component                 | Description                                                   |
|:--------------------------|:--------------------------------------------------------------|
| `DefaultSessionManager`   | Session lifecycle: create, persist, add steps, complete turns |
| `FileStateEngine`         | Persists session state to the filesystem                      |
| `DefaultContextOptimizer` | Token-aware history pruning and message deduplication         |
| `TraceManager`            | Records full request/response traces for debugging            |
| `TokenUsageManager`       | Tracks cumulative token consumption per session               |
| `ObservationDispatcher`   | Publishes all lifecycle events to the Vert.x EventBus         |

### Domain Model (Port Layer)

All core models are immutable Java 17 `record` types using functional `with...` updates:

| Model                          | Description                                                       |
|:-------------------------------|:------------------------------------------------------------------|
| `SessionContext`               | Full session state: turns, metadata, active skills, model options |
| `Turn`                         | Single ReAct iteration containing messages and observations       |
| `Message`                      | A single user/assistant/tool message                              |
| `AgentSignal`                  | Control signals: `INTERRUPT`, `ABORT`, `ACCEPT`                   |
| `ToolDefinition`               | Tool name, description, JSON schema, interrupt flag               |
| `ToolCall` / `AgentTaskResult` | Tool invocation and its result                                    |
| `ObservationType`              | Full enum of lifecycle event types                                |

---

## 🔧 ganglia-coding Capabilities

`ganglia-coding` provides a pre-assembled coding agent with a curated tool set:

| Tool                    | Description                                                                             |
|:------------------------|:----------------------------------------------------------------------------------------|
| `BashTools`             | `run_shell_command` — execute shell commands with streaming TTY output                  |
| `FileEditTools`         | `replace_in_file`, `write_file`, `apply_patch` — precise file editing with diff preview |
| `NativeFileSystemTools` | `list_files`, `read_file`, `create_directory`, `exists` — filesystem navigation         |
| `WebFetchTools`         | `fetch_url`, `fetch_file` — retrieve web content                                        |

Use `CodingAgentBuilder` to assemble a coding agent with optional `PathMapper` support for Docker/remote environments.

---

## 🖥 ganglia-terminal Capabilities

Rich interactive terminal UI powered by **JLine 3**:

- Real-time token streaming with append-only display
- ANSI markdown rendering (`MarkdownRenderer`)
- Overlay `DetailView` for inspecting long tool outputs
- Slash command menu (`/`)
- Visual `ToolCard` per tool invocation showing name, args, duration, and result
- Task/ToDo panel rendered inline
- Status bar with model name, token usage, and elapsed time

---

## 🌐 ganglia-web Capabilities

WebSocket backend for the React dashboard:

- Full-duplex **JSON-RPC 2.0 over WebSocket** protocol
- `WebUIVerticle` serves both the API and the static React frontend from `webroot/`
- `WebUIEventPublisher` fans out observation events to all connected clients
- Multi-session management with history persistence
- Methods: `SYNC`, `START`, `LIST_FILES`, `READ_FILE`, `RESPOND_ASK`, `CANCEL`, `RETRY`
- Notifications: `server_event` (lifecycle stream), `tty_event` (shell output)

---

## ⚡ Quick Start with `just`

The project uses the `just` command runner for common development tasks.

| Command             | Description                                           |
|:--------------------|:------------------------------------------------------|
| `just setup`        | Initialize everything (Maven install + NPM install)   |
| `just backend`      | Start the Java Backend (Interactive WebUI Demo)       |
| `just frontend`     | Start the Frontend Dev Server (Vite, port 5173)       |
| `just ui-watch`     | Build UI in watch mode (updates `dist/` continuously) |
| `just build-all`    | Full production build (UI + Backend JAR)              |
| `just test`         | Run all tests (Backend & Frontend)                    |
| `just test-backend` | Unit tests for all Java modules                       |
| `just test-it`      | Integration tests                                     |
| `just coverage`     | Generate JaCoCo coverage report                       |

---

## 🕹 Demos

### 1. WebUI Interactive Demo (`work.ganglia.example.WebUIDemo`)

```bash
just backend
```

Open `http://localhost:8080`. For frontend HMR, use `just frontend` and open `http://localhost:5173`.

### 2. Interactive Terminal Chat (`work.ganglia.example.InteractiveChatDemo`)

```bash
just chat
```

> [!NOTE]
> Set `OPENAI_API_KEY` or `ANTHROPIC_API_KEY` before running. Alternatively, configure the provider in `.ganglia/config.json`.

---

## ⚙️ Configuration

Ganglia is configured via `.ganglia/config.json`. It is created automatically on first run.

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

- **`agent.maxIterations`**: Maximum ReAct loop iterations per turn.
- **`agent.compressionThreshold`**: Context compression trigger (0.7 = 70% of token budget).
- **`agent.instructionFile`**: Custom instructions file parsed as priority-tagged `ContextFragment`s.
- **`models.type`**: `openai`, `anthropic`, or `gemini`.

### Custom Instructions (`GANGLIA.md`)

H2 headers are parsed as individual context fragments with optional priority and mandatory flags:

```markdown
## [Coding Style] (Priority: 8, Mandatory)
Always use Google Java Style for all code changes.

## [Security] (Priority: 10, Mandatory)
Never log or commit secrets or API keys.
```

---

## 📜 License

Distributed under the **MIT License**. See `LICENSE` for more information.

© 2026 Ganglia Team. All rights reserved.
