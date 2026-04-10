# Ganglia Module Decomposition

> **Status:** In Development
> **Version:** 0.1.7-SNAPSHOT

## 1. Core Framework (Module: `ganglia-harness`)

**Responsibility:** Orchestration of the Reasoning Loop, technical implementations, and domain port definitions.

### 1.1 Kernel (`work.ganglia.kernel`)

- **Reasoning Loop**: `ReActAgentLoop` (Thought -> Task -> Observation).
- **Dependency Assembly (0.1.6)**: `GangliaKernel` handles the decoupled assembly of loops and factories using late-binding via `AgentEnv`.
- **Task System**: `AgentTask` abstraction and concrete task types (`StandardToolTask`, `SubAgentTask`, `SkillTask`).
- **Sub-Agent Graph**: `TaskNode` (with `missionContext`, `ExecutionMode`, `IsolationLevel`), `DefaultGraphExecutor` (mission-aware prompts, `inputMapping` wiring), `Blackboard` / `InMemoryBlackboard` (cross-cycle fact store), `Fact` / `FactStatus`.
- **Scheduling**: `AgentTaskFactory` / `DefaultAgentTaskFactory` for mapping intents to execution.
- **Observation**: `DefaultObservationDispatcher` for unified event routing.

### 1.2 Port Layer (`work.ganglia.port`)

- **Chat Domain**: `Message`, `Role`, `Turn`, `SessionContext`.
- **Internal Contract**: `MemoryService`, `PromptEngine`, `SessionManager`, `StateEngine`, `SkillService`, `ExecutionContext`, `ObservationDispatcher`, `ModelConfigProvider`, `Blackboard`.
- **External Contract**: `ModelGateway`, `ToolSet`.

### 1.3 Infrastructure Layer (`work.ganglia.infrastructure`)

- **Configuration (0.1.6)**: `ConfigLoader` (IO/Watcher) and `ConfigManager` (State/Registry).
- **LLM Integration**: Native OpenAI and Anthropic protocol implementations using Vert.x `WebClient`.
- **Tooling**: `BashTools`, `FileEditTools`, `ToDoTools`.
- **Cognitive Impl**: `StandardPromptEngine`, `ContextCompressor`.
- **Persistence**: `FileStateEngine`, `FileSystemDailyRecordManager`.

## 2. Terminal UI (Module: `ganglia-terminal`)

**Responsibility:** Rich interactive command-line interface.
- **TerminalUI**: JLine 3 based controller with EventBus streaming support.
- **MarkdownRenderer**: ANSI renderer for console output.

## 3. Observability (Module: `ganglia-observability`)

**Responsibility:** Advanced execution tracing and diagnostic UI.
- **ObservabilityVerticle**: Dedicated REST API and static file server for Trace Studio (Port 8081).
- **Trace Persistence**: Structured logging of spans and metrics to JSONL.

## 4. Web UI (Module: `ganglia-coding-webui`)

**Responsibility:** Modern browser-based control center.
- **Multi-Page Entry**: Supports both `index.html` (Coding/Chat) and `trace.html` (Trace Studio).
- **Frontend**: React 18 + TypeScript + Vite + Tailwind CSS + shadcn/ui.
- **Advanced Features**: Multi-session history management, terminal log filtering, integrated Diff review, and tree-based execution visualization.

## 5. Web UI Backend (Module: `ganglia-coding-web`)

**Responsibility:** WebSocket server for the Coding UI.
- **WebUiVerticle**: Native WebSocket server with JSON-RPC 2.0 protocol support.
- **System Assembler**: Orchestrates coding-specific toolsets and agents.

## 6. Integration Testing (Module: `integration-test`)

**Responsibility:** Cross-module verification and complex scenario simulation.
- **E2ETestHarness**: Declarative scenario testing using `StubModelGateway`.
- **Scenarios**: Automated IT cases for memory, skills, and multi-agent cooperation.

## 5. SWE-bench Module (Module: `ganglia-swe-bench`)

**Responsibility:** Automated evaluation on software engineering benchmarks.
- **SweBenchEvaluator**: Driver for benchmark execution.
- **Sandbox**: Docker-based execution environments.

## 6. Technology Stack Summary

- **Reactive Runtime**: Vert.x 5.0.6 (Event Loop, EventBus, Futures).
- **LLM Protocols**: Native HTTP/SSE implementation (No SDKs).
- **UI & Rendering**: JLine 3, React 18 + TypeScript + shadcn/ui, Flexmark.
- **Testing**: JUnit 5, Mockito, Vertx-JUnit5.

