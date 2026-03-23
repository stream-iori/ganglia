# Ganglia Project Context

## 1. Project Overview

Ganglia is a **Java 17** Agent framework built on **Vert.x 5.0.6**, designed for high-performance, non-blocking agentic workflows. It follows a **Hexagonal (Ports & Adapters)** architecture, featuring a robust **Kernel** loop, a unified **Port** layer for domain models and interfaces, and a native **Infrastructure** layer for LLM integrations and file-based memory.

## 2. Technology Stack

- **Runtime:** Java 17-zulu (SDKMAN! managed)
- **Core:** Vert.x 5.0.6 (Reactive, Non-blocking I/O)
- **UI & Rendering:** JLine 3, Flexmark (ANSI Markdown), Vue 3 (WebUI)
- **Networking:** Vert.x WebClient 5.0.6
- **LLM Integration:** Native implementation of OpenAI and Anthropic protocols (No third-party SDKs)
- **Logging:** SLF4J 2.0.16 + Log4j2 2.24.3
- **Testing:** JUnit 5, Mockito, Vertx-JUnit5, **E2E Simulation Harness**

## 3. Core Capabilities (Implemented)

### 3.1 Reasoning & Orchestration

- **Kernel Loop:** `StandardAgentLoop` (in `work.ganglia.kernel.loop`) handles iterative reasoning and sequential task execution.
- **Dependency Assembly (0.1.6):** `GangliaKernel` uses an improved late-binding assembly pattern (DIP) to resolve circular dependencies between loops and task factories without hacky proxies.
- **Task Scheduling:** `SchedulableFactory` maps LLM tool calls to executable `Schedulable` tasks (Standard Tools, Sub-Agents, Skills, DAGs).
- **Hierarchical Context:** `StandardPromptEngine` with `ContextComposer` stacks five layers: **Kernel** (Persona/Mandates), **Process** (Workflow), **Rule** (Guidelines/Tools), **Capability** (Skills), and **Context** (Env/Plan/Memory).
- **Sub-Agents:** `SubAgentTask` for transient delegation and `GraphExecutor` for DAG-based execution.

### 3.2 Implemented Toolsets

- **FileSystem:** `BashFileSystemTools` (ls, cat, grep, find).
- **Bash:** `BashTools` for generic shell command execution.
- **FileEdit:** `FileEditTools` for precise line-based search and replace.
- **Interaction:** `InteractionTools` (`ask_selection`) for human-in-the-loop flows.
- **Workflow:** `ToDoTools` for managing agent-led plans and task status.
- **Memory:** `KnowledgeBaseTools`, **`RecallMemoryTools`** (fetch compressed observations).
- **Search:** `grep_search` and `web_fetch`.

### 3.3 Memory & State

- **Three-Tier Memory:** Turns (ephemeral), Sessions (compressed via `ContextCompressor`), and Long-term (`.ganglia/memory/MEMORY.md` & Daily Logs).
- **Context Compression & Hybrid Search (0.1.5):**
  - `MemoryStore`: File-based storage for `MemoryEntry` with hybrid search (keyword, category, tags).
  - `ObservationCompressor`: LLM-powered real-time compression of large tool outputs (>4000 chars).
  - `TimelineLedger`: Automated Markdown-based system medical record (`TIMELINE.md`).
  - `Progressive Disclosure`: Injecting memory indexes into system prompts via `MemoryContextSource`.
- **Daily Journal:** `DailyRecordManager` persists cross-session accomplishments to `.ganglia/memory/daily-*.md`.
- **Persistence:** `FileStateEngine` ensures session continuity across restarts via JSON serialization.

### 3.4 Configuration & Bootstrapping (0.1.6)

- **SRP Configuration:** `ConfigLoader` handles file IO and watchers, while `ConfigManager` acts as the pure registry and multi-domain provider.
- **DRY Accessors:** Functional helpers eliminate boilerplate for safe nested configuration retrieval.
- **Recursive Resolution:** Configuration files are automatically resolved across parent directories for workspace flexibility.

### 3.5 Skill System

- **Dynamic Loading:** `FileSystemSkillLoader` and `JarSkillLoader` for script/JAR skills.
- **Expertise Injection:** Skills inject domain-specific prompts and tools into the active context via `SkillTask`.

### 3.6 Testing & Verification

- **E2E Simulation:** `E2ETestHarness` allows for declarative scenario testing without real LLM costs.
- **Deterministic Assertions:** Verify output, file existence, and memory state within mock-driven loops.

## 4. Directory Structure

- `pom.xml`: Parent POM.
- `ganglia-harness/`:
  - `kernel/`: Core reasoning and task execution.
  - `port/`: Domain interfaces and data models (chat, internal, external).
  - `infrastructure/`: Technical implementations (LLM gateways, state persistence, tool implementations).
  - `api/`: External entry points (WebUI Verticle).
  - `config/` & `util/`: Shared configuration (`ConfigLoader`, `ConfigManager`) and utilities.
- `ganglia-terminal/`: Decoupled UI layer using JLine 3 and Markdown rendering.
- `ganglia-webui/`: Modern Vue 3 based web interface.
- `ganglia-swe-bench/`: SWE-bench evaluation module with Docker sandboxing.
- `integration-test/`: Automated IT and E2E simulation scenarios.
- `ganglia-example/`: Usage examples including the `WebUIDemo`.
- `docs/`: Technical designs and core documentation.

## 5. Development Guidelines

- Always use **Vert.x Future** for asynchronous operations.
- Maintain **Sequential Task Execution** within the loop via the `Schedulable` interface.
- Use **JDK 17 Text Blocks** for JSON schemas and large strings.
- Strictly adhere to the **3-tier memory model** defined in `docs/MEMORY_ARCHITECTURE.md`.
- **Unified Observation Stream**: All system activities MUST be reported via the `ObservationDispatcher` or `ExecutionContext`. Tools and Gateways MUST NOT use `vertx.eventBus()` directly for observations.
- **Network Resilience**: LLM requests MUST enforce a `timeout` (default 60s). Retries for network errors MUST be handled by the `RetryingModelGateway`.
- Use `just` for development tasks (e.g., `just frontend`, `just backend`, `just ui-watch`).

