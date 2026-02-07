# Ganglia Project Context

**Status:** Implementation Phase (Core Functional)

## 1. Project Overview
Ganglia is a **Java 17** Agent framework built on **Vert.x Core 5.0.6**, designed for high-performance, non-blocking agentic workflows. It follows a "Simple & Robust" philosophy inspired by Claude Code, using a single **ReAct control loop** and a transparent, **file-based memory system**.

## 2. Technology Stack
- **Runtime:** Java 17-zulu (SDKMAN! managed)
- **Core:** Vert.x 5.0.6 (Reactive, Non-blocking I/O)
- **Networking:** Vert.x WebClient 5.0.6
- **AI Integration:** OpenAI Java SDK 4.17.0 (Async/Stainless)
- **Logging:** SLF4J 2.0.16 + Log4j2 2.24.3
- **Testing:** JUnit 5, Mockito 5.15.2, Vertx-JUnit5

## 3. Logical Architecture
- **Brain (Model Layer):** `ModelGateway` abstraction supporting Async/Streaming interactions. Uses `chatStream` for real-time feedback via EventBus.
- **Hands (Tooling):** `DefaultToolExecutor` orchestrates built-in and extension tools via `ToolSet` interface.
- **Memory (Context):** Three-tier system (Turn, Session, Project) with semantic compression.
- **Expertise (Skills):** Modular `SkillPackage` system providing domain-specific prompts and tools.
- **Feedback (UI):** Reactive `TerminalUI` for low-latency token streaming to stdout.
- **Orchestration:** `ReActAgentLoop` handles sequential tool execution and reasoning steps.

## 4. Current Implementation Status
- [x] **Core Kernel:** ReAct loop with sequential execution and low-latency streaming feedback.
- [x] **Model Gateway:** OpenAI Async implementation with `ChatCompletionAccumulator` and EventBus streaming.
- [x] **Skill System:** Modular expertise with dynamic discovery and activation.
- [x] **Network & System:** Web access and arbitrary shell command execution.
- [x] **Built-in Tools:**
    - `BashFileSystemTools`: `ls`, `cat` (with protection).
    - `BashTools`: `run_shell_command` (generic execution with 60s timeout).
    - `WebFetchTools`: `web_fetch` (async GET via WebClient).
    - `VertxFileSystemTools`: `jvm_ls`, `jvm_read` (Non-blocking).
    - `ToDoTools`: `todo_add`, `todo_list`, `todo_complete` (with compression).
    - `KnowledgeBaseTools`: `remember` (Persisted in `MEMORY.md`).
    - `SkillTools`: `list_available_skills`, `activate_skill`.
- [x] **Memory System:** Full Three-Tier implementation with retrieval and sliding window compression.
- [x] **Testing:** Extensive coverage including `AgentLoopIT`, `MemoryRetrievalIT`, `SkillIntegrationTest`, `WebFetchToolsTest`, and `BashToolsTest`.

## 5. Directory Structure
- `docs/`: Technical designs (Architecture, Memory, Modules, Requirements, Skills).
- `src/main/java/me/stream/ganglia/core/`:
    - `llm/`: Model abstractions and implementations.
    - `loop/`: ReAct loop orchestration.
    - `model/`: Domain models.
    - `skills/`: Skill management and injectors.
    - `tools/`: Tool execution and built-in sets.
    - `ui/`: Terminal feedback components.
- `src/test/`: Unit and Integration tests.
- `examples/`: Standalone usage examples.

## 6. Development Guidelines
- Always use **Vert.x Future** for asynchronous operations.
- Maintain **Sequential Tool Execution** within the loop to ensure reasoning between steps.
- Use **JDK 17 Text Blocks** for JSON schemas and large strings.
- Strictly adhere to the **3-tier memory model** defined in `docs/MEMORY_ARCHITECTURE.md`.