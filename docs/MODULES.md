# Ganglia Module Decomposition

> **Status:** Draft / High-Level Design
> **Based on:** Requirements v0.1.0

This document decomposes the Ganglia system into logical modules based on functional and non-functional requirements.

## 1. Core Kernel (Module: `ganglia-core`)

**Responsibility:** Orchestration of the main ReAct loop, model abstraction, and state management.

- **Components:**
  - `AgentLoop`: Implements the `Input -> Thought -> Tool -> Observation` cycle.
  - `ModelGateway`: Unified interface (`ModelProvider`) for LLM providers (OpenAI, etc.) with streaming support.
  - `StateEngine`: Manages the current session state, including history maintenance and serialization for crash recovery.
  - `PromptEngine`: Constructs dynamic system prompts using templates and context.

## 2. Memory System (Module: `ganglia-memory`)

**Responsibility:** Managing ephemeral and long-term context ("The Brain").

*   **Components:**
    *   `LogManager`: Handles writing daily markdown logs (`.ganglia/logs/`).
    *   `KnowledgeBase`: Manages the curated `MEMORY.md` file (reading/updating).
    *   `ContextPruner`: Token counting and strategy for keeping the context window within limits (sliding window/summarization).
    *   `ContextCompressor`: Logic to summarize completed Tasks/Turns into concise history.
    *   `retrieval-engine`: Internal logic for "Active Retrieval" (searching memory files).

## 3. Tooling & Execution (Module: `ganglia-tools`)

**Responsibility:** Discovery, definition, and safe execution of tools ("The Hands").

*   **Components:**
    *   `ToolRegistry`: Scans and registers classes annotated with `@AgentTool`.
    *   `SchemaGenerator`: Converts Java method signatures into JSON Schema for the LLM.
    *   `ToolExecutor`: Invokes tools and handles structured error mapping (`ToolErrorResult`).
    *   `ExecutionGuard`: Enforces timeouts and memory output limits (16MB).
    *   **Standard Library:**
        *   `bash-tools`: Native command execution (ls, cat).
        *   `vertx-fs-tools`: Non-blocking Java FS.
        *   `net-tools`: HTTP client.
        *   `todo-tools`: Plan & Task management.


## 4. Skill System (Module: `ganglia-skills`)
**Responsibility:** Packaging and management of industry-specific expertise (Knowledge + Tools).

*   **Components:**
    *   `SkillPackage`: Defines the structure of a skill (Manifest, Prompts, JARs).
    *   `SkillManager`: Handles lifecycle (install, activate, deactivate) and dependency resolution.
    *   `SkillPromptInjector`: Merges skill-specific prompts and heuristics into the active system prompt.
    *   `SkillRegistry`: A repository or catalog of available skills (Local & Remote).

## 5. Interaction & Planning (Module: `ganglia-interaction`)

**Responsibility:** Human-in-the-Loop workflows and high-level planning.

- **Components:**
  - `Planner`: Specialized sub-agent logic for decomposing requests into a `List<Step>`.
  - `ApprovalFlow`: Manages the "Plan -> Review -> Approve" state machine.
  - `InterruptManager`: Intercepts `@Sensitive` tool calls and pauses execution for user confirmation.
  - `UserInterface`: Abstraction for receiving input and streaming output/events to the user.

## 6. Infrastructure & Support (Module: `ganglia-infra`)

**Responsibility:** Cross-cutting concerns and enterprise readiness.

- **Components:**
  - `Telemetry`: OpenTelemetry integration for tracing and metrics.
  - `ConfigLoader`: Loading settings and API keys from env/files.
  - `ExtensionLoader`: Mechanism for loading third-party tool JARs.
  - `Reliability`: Circuit breakers for API calls and retry logic.
