# Ganglia Project Context

**Status:** Implementation Phase (Core Functional)

## 1. Project Overview
Ganglia is a **Java 17** Agent framework built on **Vert.x Core 5.0.6**, designed for high-performance, non-blocking agentic workflows. It follows a "Simple & Robust" philosophy inspired by Claude Code, using a single **ReAct control loop** and a transparent, **file-based memory system**.

## 2. Technology Stack
- **Runtime:** Java 17-zulu (SDKMAN! managed)
- **Core:** Vert.x 5.0.6 (Reactive, Non-blocking I/O)
- **AI Integration:** OpenAI Java SDK 4.17.0 (Async/Stainless)
- **Logging:** SLF4J 2.0.16 + Log4j2 2.24.3
- **Testing:** JUnit 5, Mockito 5.15.2, Vertx-JUnit5

## 3. Logical Architecture
- **Brain (Model Layer):** `ModelGateway` abstraction supporting Async/Streaming interactions. `OpenAIModelGateway` is implemented.
- **Hands (Tooling):** `DefaultToolExecutor` orchestrates built-in and extension tools.
- **Memory (Context):** Three-tier system:
    - **Short-term:** `Turn` objects (Thought -> Act -> Observe).
    - **Medium-term:** Compressed `SessionContext` managed via ToDo lifecycle.
    - **Long-term:** `MEMORY.md` and project logs (Agentic Search).
- **Orchestration:** `ReActAgentLoop` handles sequential tool execution and reasoning steps.

## 4. Current Implementation Status
- [x] **Core Kernel:** ReAct loop with sequential execution and structured `ToolInvokeResult`.
- [x] **Model Gateway:** OpenAI Async implementation with `ChatCompletionAccumulator` and EventBus streaming.
- [x] **Built-in Tools:**
    - `BashFileSystemTools`: `ls`, `cat` (with timeout and 16MB memory protection).
    - `VertxFileSystemTools`: `jvm_ls`, `jvm_read` (Non-blocking).
    - `ToDoTools`: `todo_add`, `todo_list`, `todo_complete` (Persisted in `SessionContext`).
    - `KnowledgeBaseTools`: `remember` (Persisted in `MEMORY.md`).
- [x] **Infrastructure:** `ToolsFactory`, `SessionContext` with Turn-based granularity.
- [x] **Memory System:** Full Three-Tier implementation (Short, Medium, Long-term) with compression and retrieval.
- [x] **Testing:** Comprehensive unit tests and integration tests (`AgentLoopIT`, `MemoryRetrievalIT`) verified against real APIs.

## 5. Directory Structure
- `docs/`: Technical designs (Architecture, Memory, Modules, Requirements).
- `src/main/java/me/stream/ganglia/core/`:
    - `llm/`: Model abstractions and implementations.
    - `loop/`: ReAct loop orchestration.
    - `model/`: Domain models (Turn, Message, SessionContext).
    - `tools/`: Tool execution and built-in implementations.
- `src/test/`: Unit and Integration tests.
- `examples/`: Standalone usage examples (e.g., `kimi-example`).

## 6. Development Guidelines
- Always use **Vert.x Future** for asynchronous operations.
- Maintain **Sequential Tool Execution** within the loop to ensure reasoning between steps.
- Use **JDK 17 Text Blocks** for JSON schemas and large strings.
- Strictly adhere to the **3-tier memory model** defined in `docs/MEMORY_ARCHITECTURE.md`.