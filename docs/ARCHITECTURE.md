# Caprice Architecture Documentation

> **Status:** Draft / Initial Design
> **Version:** 0.1.0

## 1. System Overview

**Caprice** is a Java-based Agent framework designed for integration into third-party applications. It prioritizes **simplicity, robustness, and transparency** over complex, opaque multi-agent graphs.

The core design philosophy is inspired by **Claude Code**: a single, powerful control loop that utilizes a hybrid toolset and a transparent, file-based memory system.

## 2. Core Design Principles

1.  **Single Control Loop (The "ReAct" Loop):**
    *   Avoid complex graphs or state machines.
    *   Use a flat message history processed by a single main thread.
    *   Flow: `Input -> [Thought -> Tool -> Observation] * N -> Answer`.

2.  **Tool-First Navigation:**
    *   The agent explores codebases using tools (`grep`, `glob`, `read`) rather than relying on pre-computed, opaque vector embeddings (RAG).
    *   "Agentic Search" allows the model to form its own queries and refine them based on feedback.

3.  **Memory as Code:**
    *   Memory is stored in **Markdown files** (`MEMORY.md`, Session Logs) within the user's project.
    *   It is transparent, editable, and version-controlled by Git.

4.  **Steerability via Prompting:**
    *   Behavior is controlled by extensive, structured prompts (XML, Examples) rather than hard-coded logic.
    *   Adherence to "System Reminders" and "Tone" guidelines.

## 3. Logical Architecture

### 3.1 The Model Layer ("The Brain")
*   **Unified Interface:** Abstractions (`ModelProvider`, `ChatClient`) hide the specifics of LLM providers (OpenAI, Anthropic, etc.).
*   **Smart Routing:**
    *   **Fast Model (e.g., Haiku):** Handles routine tasks like file summarization, git history reading, and token counting.
    *   **Smart Model (e.g., GPT-4o, Sonnet):** Handles the main ReAct loop, reasoning, and planning.
*   **Streaming:** Built on Java Flow API / Reactor for real-time user feedback.

### 3.2 Tooling & Actuation ("The Hands")
*   **Definition:** Tools are defined via Java annotations (`@AgentTool`) with automatic JSON Schema generation.
*   **Hybrid Toolset:**
    *   **Low-Level:** `Bash` execution, `FileWrite` (Atomic).
    *   **High-Level:** `Grep`, `Glob`, `Edit` (Smart code replacement), `WebFetch`.
*   **Safety:**
    *   **Sandbox:** Execution of untrusted code in isolated environments.
    *   **Human-in-the-Loop:** Tools marked as "Sensitive" require explicit user confirmation.

### 3.3 The Memory System ("The Context")
See [Memory Architecture](MEMORY_ARCHITECTURE.md) for details.

*   **Ephemeral Layer:** Daily session logs (Stream of consciousness).
*   **Curated Layer:** `MEMORY.md` (Key facts, preferences, decisions).
*   **Retrieval:** The agent actively queries these files using its tools.

### 3.4 Workflow Management
*   **To-Do List:** The agent maintains a self-managed task list to prevent getting lost in long sessions.
*   **Cloning (Transient Delegation):**
    *   For complex sub-tasks, the agent can spawn a "clone" (a fresh instance with specific context).
    *   The clone performs the task and returns the result as a tool output.
    *   This keeps the main context window clean.

## 4. Data Flow (ReAct Loop)

```mermaid
graph TD
    User[User Input] -->|Injects| Context[Context & Memory Files]
    Context -->|Constructs| Prompt[System Prompt + History]
    Prompt -->|Request| LLM[LLM (Smart Model)]
    
    LLM -->|Thought & Call| Router{Action Router}
    
    Router -->|Tool Call| ToolExecutor[Tool Executor]
    Router -->|Final Answer| UserOutput[User Display]
    
    ToolExecutor -->|Execute| FS[File System / Bash]
    ToolExecutor -->|Execute| Web[Web / API]
    
    FS -->|Result| Obs[Observation]
    Web -->|Result| Obs
    
    Obs -->|Append to History| Prompt
    
    subgraph "Memory Maintenance"
        Log[Session Log] -.->|Summarize| MemFile[MEMORY.md]
    end
```

## 5. Technology Stack

*   **Language:** Java 17+
*   **Core Framework:** Vert.x (Reactive, Non-blocking I/O)
*   **LLM Client:** OpenAI-Java (Official) / LangChain4j (Potential future)
*   **Observability:** OpenTelemetry
*   **Testing:** JUnit 5
