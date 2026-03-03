# Ganglia Project Context

**Status:** Core Implemented (v1.1.0)

## 1. Project Overview
Ganglia is a **Java 17** Agent framework built on **Vert.x Core 5.0.6**, designed for high-performance, non-blocking agentic workflows. It follows a "Simple & Robust" philosophy, using a decoupled **Scheduling Abstraction** layer, a single **Standard Reasoning Loop**, a decoupled **Terminal UI**, and a transparent **file-based memory system**.

## 2. Technology Stack
- **Runtime:** Java 17-zulu (SDKMAN! managed)
- **Core:** Vert.x 5.0.6 (Reactive, Non-blocking I/O)
- **UI & Rendering:** JLine 3, Flexmark (ANSI Markdown)
- **Networking:** Vert.x WebClient 5.0.6
- **AI Integration:** OpenAI Java SDK, Anthropic Java, Google GenAI
- **Logging:** SLF4J 2.0.16 + Log4j2 2.24.3
- **Testing:** JUnit 5, Mockito, Vertx-JUnit5, **E2E Simulation Harness**

## 3. Core Capabilities (Implemented)

### 3.1 Reasoning & Orchestration
- **Reasoning Loop:** `StandardAgentLoop` handles iterative reasoning and sequential task execution.
- **Scheduling Layer:** `SchedulableFactory` maps LLM tool calls to executable `Schedulable` tasks (Standard Tools, Sub-Agents, Skills, DAGs).
- **Hierarchical Context:** `StandardPromptEngine` with `ContextComposer` stacks Persona, Mandates, Env, Skills, and Memory.
- **Sub-Agents:** `SubAgentTask` for transient delegation and `GraphExecutor` for DAG-based task execution.

### 3.2 Implemented Toolsets
- **FileSystem:** `BashFileSystemTools` (ls, cat, grep, find).
- **Bash:** `BashTools` for generic shell command execution.
- **FileEdit:** `FileEditTools` for precise line-based search and replace.
- **Interaction:** `InteractionTools` (`ask_selection`) for human-in-the-loop flows.
- **Workflow:** `ToDoTools` for managing agent-led plans and task status.
- **Memory:** `KnowledgeBaseTools` for reading/updating `MEMORY.md`.
- **Search:** `grep_search`, `glob`, and `web_fetch`.

### 3.3 Memory & State
- **Three-Tier Memory:** Turns (ephemeral), Sessions (compressed via `ContextCompressor`), and Long-term (`MEMORY.md` & Daily Logs).
- **Daily Journal:** `DailyRecordManager` persists cross-session accomplishments to `.ganglia/memory/daily-*.md`.
- **Persistence:** `FileStateEngine` ensures session continuity across restarts via JSON serialization.

### 3.4 Skill System
- **Dynamic Loading:** `FileSystemSkillLoader` and `JarSkillLoader` for script/JAR skills.
- **Expertise Injection:** Skills inject domain-specific prompts and tools into the active context via `SkillTask`.

### 3.5 Testing & Verification
- **E2E Simulation:** `E2ETestHarness` allows for declarative scenario testing without real LLM costs.
- **Deterministic Assertions:** Verify output, file existence, and memory state within mock-driven loops.

## 4. Directory Structure
- `pom.xml`: Parent POM.
- `ganglia-core/`: Core reasoning, scheduling, model gateways, memory, and tools.
- `ganglia-terminal/`: Decoupled UI layer using JLine 3 and Markdown rendering.
- `ganglia-swe-bench/`: SWE-bench evaluation module with Docker sandboxing.
- `integration-test/`: Automated IT and E2E simulation scenarios.
- `ganglia-example/`: Usage examples including the `InteractiveChatDemo` CLI.
- `docs/`: Technical designs and v1.1.0 documentation.

## 5. Development Guidelines
- Always use **Vert.x Future** for asynchronous operations.
- Maintain **Sequential Task Execution** within the loop via the `Schedulable` interface.
- Use **JDK 17 Text Blocks** for JSON schemas and large strings.
- Strictly adhere to the **3-tier memory model** defined in `docs/MEMORY_ARCHITECTURE.md`.
- Run all tests using `source "$HOME/.sdkman/bin/sdkman-init.sh" && sdk env && mvn verify`.
