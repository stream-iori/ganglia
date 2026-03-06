# Ganglia Module Decomposition

> **Status:** Implemented (v1.2.0)
> **Base:** Java 17, Vert.x 5.0.6

This document describes the implemented module structure of the Ganglia system, following the Hexagonal architecture.

## 1. Core Framework (Module: `ganglia-core`)

**Responsibility:** Orchestration of the Reasoning Loop, technical implementations, and domain port definitions.

### 1.1 Kernel (`work.ganglia.kernel`)
- **Reasoning Loop**: `StandardAgentLoop` (Thought -> Task -> Observation).
- **Task System**: `Schedulable` abstraction and concrete task types (`ToolTask`, `SubAgentTask`, `SkillTask`).
- **Scheduling**: `SchedulableFactory` for mapping intents to execution.

### 1.2 Port Layer (`work.ganglia.port`)
- **Chat Domain**: `Message`, `Role`, `Turn`, `SessionContext`.
- **Internal Contract**: `MemoryService`, `PromptEngine`, `SessionManager`, `StateEngine`, `SkillService`.
- **External Contract**: `ModelGateway`, `ToolExecutor`.

### 1.3 Infrastructure Layer (`work.ganglia.infrastructure`)
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
- **Frontend**: Vue 3 + Vite + Tailwind CSS.
- **Backend API**: `WebUIVerticle` (in `ganglia-core`) providing a SockJS/EventBus bridge.

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
