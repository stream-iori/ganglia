# Ganglia Module Decomposition (Implemented)

> **Status:** Implemented (v1.1.0)
> **Base:** Java 17, Vert.x 5.0.6

This document describes the implemented module structure of the Ganglia system.

## 1. Core Framework (Module: `ganglia-core`)

**Responsibility:** Orchestration of the main ReAct loop, model abstraction, scheduling, and state management.

- **Components:**
  - `ReActAgentLoop`: Core reasoning loop (Thought -> Task -> Observation).
  - `ScheduleableFactory`: Unified scheduling layer that maps LLM intents to executable tasks (Tools, Sub-Agents, Skills).
  - `ModelGateway`: Abstraction for LLM providers (OpenAI, Anthropic, Gemini).
  - `ContextEngine`: Layered system prompt construction via `ContextSource` and `ContextComposer`.
  - `MemorySystem`: Turn-based history, Session compression, and Daily Logs (`DailyRecordManager`).
  - `ToolExecutor`: Execution engine for standard, primitive tools (Bash, FileSystem).
  - `SkillService`: Management of domain-specific expertise.

## 2. Terminal UI (Module: `ganglia-terminal`)

**Responsibility:** Rich interactive command-line interface.

- **Components:**
  - `TerminalUI`: JLine 3 based terminal controller with EventBus streaming support.
  - `MarkdownRenderer`: Flexmark-based ANSI renderer for Markdown content.

## 3. Integration Testing (Module: `integration-test`)

**Responsibility:** Verification of complex scenarios and cross-module workflows.

- **Components:**
  - `E2ETestHarness`: Declarative scenario testing using `StubModelGateway`.
  - `IntegrationScenarios`: Automated IT cases covering sub-agents, DAGs, skills, and memory.

## 4. SWE-bench Module (Module: `ganglia-swe-bench`)

**Responsibility:** Automated evaluation of the agent on software engineering benchmarks.

- **Components:**
  - `SWEBenchEvaluator`: Benchmarking driver.
  - `SandboxManager`: Docker-based isolated execution environments.

## 5. Examples & Demos (Module: `ganglia-example`)

**Responsibility:** Showcasing framework capabilities and providing starting points for users.

- **Components:**
  - `InteractiveChatDemo`: A full-featured interactive CLI.
  - `AutonomousReActDemo`: Showcasing the agent's ability to solve tasks autonomously.

## 6. Technology Stack Summary

- **Core:** Vert.x (EventBus, Futures, FileSystem).
- **LLM SDKs:** OpenAI Java, Anthropic Java, Google GenAI.
- **UI:** JLine 3, Flexmark.
- **Testing:** JUnit 5, Mockito, Vertx-JUnit5.
