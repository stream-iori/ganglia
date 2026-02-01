# AI Agent Capabilities Brainstorming
> **Goal:** Create a Java-based Agent library/framework (`Caprice`) for third-party integration, inspired by the "Keep It Simple" philosophy of Claude Code.

## 0. Core Philosophy: Simplicity & Robustness
*   **Single Control Loop:** Avoid complex multi-agent graphs. Use a single main thread with a flat message history.
*   **Tool-First Code Navigation:** Prefer LLM-driven search (`grep`, `glob`, `read`) over complex vector RAG for codebases.
*   **Small Model Offloading:** Utilize smaller, faster models (e.g., Haiku, Flash) for routine tasks (summarization, file reading) to reduce cost and latency.
*   **Steerability:** Heavy reliance on structured prompts (XML, Examples) to control behavior rather than complex code logic.

## 1. Core Connectivity (The "Model Layer")
*   **Unified Model Interface:** `ModelProvider`, `ChatClient` interfaces hiding provider differences.
*   **Smart Routing:** Logic to route simple tasks (git history, summarization) to cheaper models and complex reasoning to SOTA models.
*   **Streaming Support:** Native reactive streams (Java Flow / Reactor) for real-time token feedback.
*   **Multi-modal Support:** First-class support for Image/Audio inputs.
*   **Structured Output:** JSON mode handling for strictly typed Java POJOs.

## 2. Memory System (The "Context")
> **Philosophy:** Memory as Code. Transparent, user-editable, and Git-tracked.

*   **Structure: The Two-Layer Model**
    *   **Layer 1: Ephemeral/Stream:** Daily session logs (e.g., `.ganglia/logs/2026-01-31.md`) capturing raw interactions and decisions.
    *   **Layer 2: Curated Knowledge:** A dedicated `MEMORY.md` file in the project root.
        *   Stores user preferences, architectural decisions, and "lessons learned".
        *   *Manual/Auto Curation:* Agent periodically summarizes key insights from Layer 1 to Layer 2.
*   **Retrieval: Agentic Search > Context Stuffing**
    *   Avoid stuffing the prompt with massive context.
    *   **Active Retrieval:** The agent uses its own tools (`Grep`, `Read`) to query `MEMORY.md` and log files when it needs context (e.g., "Check MEMORY.md for database conventions").
*   **Vector RAG (Optional):** Demoted to a fallback for querying massive external documentation, not primary project memory.
*   **State Persistence:**
    *   Session state is serialized to local JSON/Markdown files in `.ganglia/state/`, allowing resumption without complex database setups.

## 3. Tooling & Actuation (The "Hands")
*   **Self-Managed To-Do List:** A dedicated tool/system for the agent to maintain its own plan, preventing context drift in long sessions.
*   **Hybrid Toolset:**
    *   *Low-Level:* `Bash`, `FileWrite`.
    *   *High-Level:* `Grep`, `Glob`, `Edit` (specialized for code), `WebFetch`.
*   **Function Calling Framework:** Annotation-based (`@AgentTool`) with auto JSON Schema generation.
*   **Sandbox Execution:** Safe environment for untrusted operations.
*   **Human-in-the-Loop:** "Ask User" tool for confirmations and disambiguation.

## 4. Reasoning & Planning (The "Workflow")
*   **The Single "ReAct" Loop:**
    *   `Input -> [Thought -> Tool Select -> Execution -> Observation] * n -> Final Answer`.
    *   Maintain a flat list of messages.
*   **Task Decomposition:**
    *   Agent creates and updates its own To-Do list.
    *   Use of "Cloning" (Single-level delegation): For complex sub-tasks, spawn a transient child agent instance, then merge its result back as a tool output.
*   **Prompt Engineering (The "Brain"):**
    *   **Algorithmic Prompts:** Defining decision trees and heuristics clearly in the system prompt.
    *   **XML & Markdown:** Extensive use of `<system-reminder>`, `<good-example>`, `<bad-example>` tags.
    *   **Tone & Style:** Strict instructions (e.g., "No fluff", "Don't explain unless asked") to maintain a professional CLI persona.

## 5. Enterprise Readiness (Java Specifics)
*   **Observability:** OpenTelemetry integration for tracing "Thoughts" and tool executions.
*   **Framework Integration:** Spring Boot Starter & Quarkus Extensions.
*   **Safety:** 
    *   "IMPORTANT" / "NEVER" prompt directives for safety boundaries.
    *   PII masking and prompt injection defenses.