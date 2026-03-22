# Ganglia Module Decomposition

> **Status:** In Development
> **Version:** 0.1.6

## 1. Core Framework (Module: `ganglia-harness`)

**Responsibility:** Orchestration of the Reasoning Loop, technical implementations, and domain port definitions.

### 1.1 Kernel (`work.ganglia.kernel`)

- **Reasoning Loop**: `StandardAgentLoop` (Thought -> Task -> Observation).
- **Dependency Assembly (v1.4.0)**: `GangliaKernel` handles the decoupled assembly of loops and factories using late-binding via `AgentEnv`.
- **Task System**: `Schedulable` abstraction and concrete task types (`ToolTask`, `SubAgentTask`, `SkillTask`).
- **Scheduling**: `SchedulableFactory` for mapping intents to execution.
- **Observation**: `DefaultObservationDispatcher` for unified event routing.

### 1.2 Port Layer (`work.ganglia.port`)

- **Chat Domain**: `Message`, `Role`, `Turn`, `SessionContext`.
- **Internal Contract**: `MemoryService`, `PromptEngine`, `SessionManager`, `StateEngine`, `SkillService`, `ExecutionContext`, `ObservationDispatcher`, `ModelConfigProvider`.
- **External Contract**: `ModelGateway`, `ToolSet`.

### 1.3 Infrastructure Layer (`work.ganglia.infrastructure`)

- **Configuration (v1.4.0)**: `ConfigLoader` (IO/Watcher) and `ConfigManager` (State/Registry).
- **LLM Integration**: Native OpenAI and Anthropic protocol implementations using Vert.x `WebClient`.
- **Tooling**: `BashTools`, `FileEditTools`, `ToDoTools`.
- **Cognitive Impl**: `StandardPromptEngine`, `ContextCompressor`.
- **Persistence**: `FileStateEngine`, `FileSystemDailyRecordManager`.

## 2. Terminal UI (Module: `ganglia-terminal`)

**Responsibility:** Rich interactive command-line interface.
- **TerminalUI**: JLine 3 based controller with EventBus streaming support.
- **MarkdownRenderer**: ANSI renderer for console output.

## 3. Web UI (Module: `ganglia-webui`)

**Responsibility:** Modern browser-based control center.
- **Frontend**: Vue 3 + Vite + Tailwind CSS. Implements the 3x3 Interaction Matrix.
- **Advanced Features**: Multi-session history management, terminal log filtering, integrated Diff review, and reactive workspace file tree.
- **Backend API**: `WebUIVerticle` (in `ganglia-web`) providing a native WebSocket server with JSON-RPC 2.0 protocol support and recursive file system monitoring.

## 4. Integration Testing (Module: `integration-test`)

**Responsibility:** Cross-module verification and complex scenario simulation.
- **E2ETestHarness**: Declarative scenario testing using `StubModelGateway`.
- **Scenarios**: Automated IT cases for memory, skills, and multi-agent cooperation.

## 5. SWE-bench Module (Module: `ganglia-swe-bench`)

**Responsibility:** Automated evaluation on software engineering benchmarks.
- **SWEBenchEvaluator**: Driver for benchmark execution.
- **Sandbox**: Docker-based execution environments.

## 6. Technology Stack Summary

- **Reactive Runtime**: Vert.x 5.0.6 (Event Loop, EventBus, Futures).
- **LLM Protocols**: Native HTTP/SSE implementation (No SDKs).
- **UI & Rendering**: JLine 3, Vue 3, Flexmark.
- **Testing**: JUnit 5, Mockito, Vertx-JUnit5.

