# AGENTS.md — Ganglia Project Instructions

## 1. Project Overview

Ganglia is a **Java 17** AI Agent framework built on **Vert.x 5.0.6**, designed for high-performance, non-blocking agentic workflows. It follows a **Hexagonal (Ports & Adapters)** architecture with a robust ReAct reasoning loop, pluggable memory via SPI, and multi-frontend support (Terminal, WebUI).

**Version:** 0.1.7-SNAPSHOT

## 2. Technology Stack

|    Category     |                                   Technology                                    |
|-----------------|---------------------------------------------------------------------------------|
| Runtime         | Java 17-zulu (SDKMAN! managed)                                                  |
| Core            | Vert.x 5.0.6 (Reactive, Non-blocking I/O)                                       |
| LLM Integration | Native OpenAI & Anthropic protocol (no third-party SDKs)                        |
| Terminal UI     | JLine 3.25.1, CommonMark (ANSI Markdown)                                        |
| Web UI          | React 18 + TypeScript (Vite), Vert.x Web (WebSocket + JSON-RPC 2.0)             |
| Networking      | Vert.x WebClient 5.0.6                                                          |
| Logging         | SLF4J 2.0.16 + Log4j2                                                           |
| Testing         | JUnit 5, Mockito, Vertx-JUnit5, E2E Simulation Harness                          |
| Code Quality    | Spotless (Google Java Format), Checkstyle (google_checks.xml), SpotBugs, JaCoCo |

## 3. Module Structure

```
ganglia-parent (pom.xml)
├── ganglia-harness                  # Core: kernel, ports, infrastructure (no memory impl)
├── ganglia-local-file-memory        # File-based memory SPI implementation
├── ganglia-observability            # Independent Trace Studio backend and REST API
├── coding-agent/                    # Directory grouping (NOT a Maven module)
│   ├── ganglia-coding               # Coding agent builder + tools (bash, file-edit, web-fetch)
│   ├── ganglia-coding-web            # WebSocket + JSON-RPC 2.0 web UI backend
│   ├── ganglia-terminal             # JLine 3 terminal UI
│   ├── ganglia-swe-bench            # SWE-bench evaluation with Docker sandboxing
│   └── ganglia-coding-webui         # React multi-page frontend (NOT a Maven module)
├── integration-test                 # E2E simulation scenarios
└── ganglia-example                  # Demo apps (WebUIDemo)
```

> **Note:** `coding-agent/` is a plain directory, not a Maven aggregator. The four sub-modules (`ganglia-coding`, `ganglia-coding-web`, `ganglia-terminal`, `ganglia-swe-bench`) are listed directly in the root `pom.xml` as `coding-agent/ganglia-*`. `ganglia-coding-webui` is a standalone Vite/React project, not managed by Maven.

### Dependency Graph

```
ganglia-harness
    ↑
ganglia-local-file-memory
    ↑
ganglia-coding, ganglia-coding-web, ganglia-terminal, ganglia-observability
    ↑
integration-test, ganglia-example, ganglia-swe-bench
```

## 4. Build & Development Commands

|             Command             |                                              Description                                              |
|---------------------------------|-------------------------------------------------------------------------------------------------------|
| `mvn clean install -DskipTests` | Full build (skip tests)                                                                               |
| `just test-backend`             | Unit tests: ganglia-harness, ganglia-local-file-memory, ganglia-coding, ganglia-coding-web, ganglia-terminal |
| `just test-it`                  | Integration tests                                                                                     |
| `just test-it-one <ClassName>`  | Single integration test                                                                               |
| `just frontend`                 | Vite dev server (port 5173)                                                                           |
| `just backend`                  | WebUI backend (port 8080)                                                                             |
| `just obs`                      | Observability Studio backend (port 8081)                                                              |
| `just ui-watch`                 | Frontend watch mode (auto-rebuild dist/)                                                              |
| `just coverage`                 | JaCoCo coverage report                                                                                |
| `just build-all`                | Full production build (UI + Backend JAR)                                                              |
| `just clean`                    | Clean all build artifacts                                                                             |

## 5. Architecture

### 5.1 Hexagonal Layers

- **Kernel** (`ganglia-harness/kernel/`): ReAct reasoning loop, task scheduling, sub-agents
  - `GangliaKernel` — Main orchestrator, late-binding assembly via `AgentEnv`
  - `ReActAgentLoop` — Iterative Thought → Action → Observation cycle
  - `AgentTaskFactory` / `DefaultAgentTaskFactory` — Maps LLM tool calls to executable tasks
  - `DefaultGraphExecutor` — DAG execution with mission context propagation and `inputMapping` wiring
  - `Blackboard` / `InMemoryBlackboard` — Append-only cross-cycle fact store with optimistic concurrency
- **Observability** (`ganglia-observability/`): Trace Studio, hierarchical execution tracking
  - `ObservabilityVerticle` — Independent REST API and UI server (Port 8081)
  - `StructuredTraceManager` — Writes JSONL traces with parent-child span relationships
- **Port** (`ganglia-harness/port/`): Domain interfaces and models
  - `chat/` — Message, Turn, SessionContext (immutable records)
  - `internal/` — PromptEngine, ModelGateway, ContextOptimizer, SessionManager, StateEngine, Blackboard
  - `internal/memory/` — MemoryService, MemorySystemProvider, MemorySystem, MemorySystemConfig (SPI)
  - `external/` — ToolSet, ExecutionContext
  - `kernel/subagent/blackboard/` — Fact, FactStatus (domain records for Blackboard)
  - `mcp/` — MCP protocol support
- **Infrastructure** (`ganglia-harness/infrastructure/`): Technical implementations
  - `external/` — Model gateways (OpenAI, Anthropic), ToolsFactory
  - `internal/` — Prompt engine, state persistence, context optimization

### 5.2 Memory SPI Pattern

Memory is pluggable via `java.util.ServiceLoader`:

```
MemorySystemProvider (SPI interface in ganglia-harness)
    └── FileSystemMemoryProvider (implementation in ganglia-local-file-memory)
            └── Creates MemorySystem record containing:
                MemoryStore, ObservationCompressor, ContextCompressor,
                TimelineLedger, DailyRecordManager, LongTermMemory,
                MemoryService, MemoryContextSource
```

`GangliaKernel` discovers the provider at startup:

```java
ServiceLoader.load(MemorySystemProvider.class).findFirst().orElseThrow(...)
```

### 5.3 Prompt Context Layering

`StandardPromptEngine` + `ContextComposer` stack five priority-based layers:

1. **Kernel** — Persona, mandates
2. **Process** — Workflow directives
3. **Rule** — Guidelines, tool descriptions
4. **Capability** — Skills injection
5. **Context** — Environment, plan, memory index

Fragments are pruned by priority when token budget is exceeded.

### 5.4 Model Gateways

- `AbstractModelGateway` — DRY base with `normalizeEndpoint`, `collectToolCalls`, `buildStreamingResponse`, semaphore (max 5 concurrent calls)
- `OpenAIModelGateway` / `AnthropicModelGateway` — Native protocol implementations
- `RetryingModelGateway` — Exponential backoff retry wrapper

### 5.5 Coding Agent

`CodingAgentBuilder` (in `ganglia-coding`) assembles:
- Tools: BashFileSystemTools, BashTools, FileEditTools, WebFetchTools
- Context sources: CodingPersonaContextSource, CodingWorkflowSource, CodingGuidelineSource, FileContextSource
- PathMapper support for environment isolation

### 5.6 Observation System

All system activities flow through `ObservationDispatcher` → Vert.x EventBus. Tools and gateways MUST NOT use `vertx.eventBus()` directly.

## 6. Development Guidelines

### Code Conventions

- **Async:** All async operations use `Vert.x Future` — never block
- **Logger field:** Always named `logger` (not `log`)
- **Java 17 features:** Text blocks for JSON schemas and large strings
- **Immutable domain:** Use Java records for domain models (Message, Turn, SessionContext)
- **Defensive Copying:** Core domain objects MUST use `List.copyOf()` or `Map.copyOf()` in constructors to ensure immutability
- **Sequential task execution:** Within the loop via `AgentTask` interface

### Architecture Rules

- **Unified Observation Stream:** All observations go through `ObservationDispatcher` or `ExecutionContext`. Every event MUST include `spanId` and `parentSpanId` for tree visualization.
- **Network Resilience:** LLM requests enforce timeout (default 60s), retries via `RetryingModelGateway` with child span tracking
- **Memory Isolation:** Memory implementations live in `ganglia-local-file-memory`, not in `ganglia-harness`
- **SPI for Extensibility:** Pluggable subsystems use `ServiceLoader` (e.g., `MemorySystemProvider`)

### Code Quality

- **Formatting:** Spotless with Google Java Format (runs at compile phase)
- **Static Analysis:** Checkstyle with google_checks.xml + suppressions in `config/checkstyle-suppressions.xml`
- **Coverage:** JaCoCo reports via `just coverage`
- Run `mvn spotless:apply` to auto-fix formatting before committing

## 7. Testing

- **Unit tests:** JUnit 5 + Mockito + Vertx-JUnit5
- **In-memory FS:** Google Jimfs for filesystem tests
- **E2E Simulation:** `E2ETestHarness` for declarative scenario testing without real LLM costs
- **Three-tier memory model:** See `docs/MEMORY_ARCHITECTURE.md`

## 8. Key Documentation

All design docs are in `docs/`:

|           Document           |                         Topic                         |
|------------------------------|-------------------------------------------------------|
| ARCHITECTURE.md              | Hexagonal layers, observation stream, memory system   |
| CORE_KERNEL_DESIGN.md        | ReAct loop, Schedulable tasks, kernel/port separation |
| MODULES.md                   | Module decomposition                                  |
| MEMORY_ARCHITECTURE.md       | Three-tier memory model                               |
| MEMORY_REFACTORING_DESIGN.md | Memory module extraction to SPI                       |
| PROMPT_ENGINE_DECOUPLING.md  | Context composition                                   |
| CONTEXT_ENGINE_DESIGN.md     | Multi-layer context stacking                          |
| SUB_AGENT_DESIGN.md          | Sub-agent delegation                                  |
| SUB_AGENT_GRAPH_DESIGN.md    | DAG-based task graph execution                        |
| SKILLS_DESIGN.md             | Dynamic skill system                                  |
| ROBUSTNESS_DESIGN.md         | Failure handling and resilience                       |
| SESSION_MANAGEMENT_DESIGN.md | Session lifecycle                                     |
| STATE_EVOLUTION_DESIGN.md    | State transitions                                     |
| DAILY_RECORD_DESIGN.md       | Daily journal system                                  |

