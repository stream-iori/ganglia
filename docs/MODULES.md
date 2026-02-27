# Ganglia Module Decomposition (Implemented)

> **Status:** Implemented (v1.0.0)
> **Base:** Java 17, Vert.x 5.0.6

This document describes the implemented module structure of the Ganglia system.

## 1. Core Framework (Module: `ganglia-core`)

**Responsibility:** Orchestration of the main ReAct loop, model abstraction, memory management, and tool execution engine.

- **Components:**
  - `ReActAgentLoop`: Core reasoning loop (Thought -> Tool -> Observation).
  - `ModelGateway`: Abstraction for LLM providers (OpenAI, Anthropic, Gemini).
  - `ContextEngine`: Layered system prompt construction via `ContextSource` and `ContextComposer`.
  - `MemorySystem`: Turn-based history, Session compression, and Daily Logs (`DailyRecordManager`).
  - `ToolExecutor`: Dynamic discovery and execution of `ToolSet` implementations.
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
  - `IntegrationScenarios`: 20+ automated IT cases covering all core capabilities.

## 4. Examples & Demos (Module: `ganglia-example`)

**Responsibility:** Showcasing framework capabilities and providing starting points for users.

- **Components:**
  - `InteractiveChatDemo`: A full-featured interactive CLI.
  - `AutonomousReActDemo`: Showcasing the agent's ability to solve tasks autonomously.
  - `SubAgentCooperationDemo`: Demonstrating parent-child agent delegation.

## 5. Technology Stack Summary

- **Core:** Vert.x (EventBus, Futures, FileSystem).
- **LLM SDKs:** OpenAI Java, Anthropic Java, Google GenAI.
- **UI:** JLine 3, Flexmark.
- **Testing:** JUnit 5, Mockito, Vertx-JUnit5.
