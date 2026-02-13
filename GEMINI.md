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

## 4. Directory Structure
- `pom.xml`: Parent POM.
- `ganglia-core/`: Core framework code and unit tests.
    - `src/main/java/me/stream/ganglia/`:
        - `core/`: ReAct loop, model, prompt, state, session.
        - `memory/`: Memory system.
        - `skills/`: Skill system.
        - `tools/`: Tooling system.
        - `ui/`: Terminal UI.
- `integration-test/`: Dedicated module for integration tests (IT).
    - `src/test/java/me/stream/ganglia/it/`: Integration test cases.
- `docs/`: Technical designs and documentation.
- `examples/`: usage examples.

## 5. Development Guidelines
- Always use **Vert.x Future** for asynchronous operations.
- Maintain **Sequential Tool Execution** within the loop to ensure reasoning between steps.
- Use **JDK 17 Text Blocks** for JSON schemas and large strings.
- Strictly adhere to the **3-tier memory model** defined in `docs/MEMORY_ARCHITECTURE.md`.
- Prefix `mvn` or `java` commands with `source "$HOME/.sdkman/bin/sdkman-init.sh" && sdk env` to ensure the correct Java 17 environment.
- Run all tests (unit and integration) using `source "$HOME/.sdkman/bin/sdkman-init.sh" && sdk env && mvn verify`.
