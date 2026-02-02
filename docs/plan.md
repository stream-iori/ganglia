# Ganglia Development Plan: Memory System

> **Goal:** Implement the Three-Tier Memory Architecture to enable long-running, context-aware agent sessions.

## Phase 1: Foundation & Persistence (Tier 1)
**Objective:** robust state management for the current session loop.

- [x] **State Serialization:**
    - [x] Design JSON schema for `SessionContext` (including `ToDoList` and `Turn` history).
    - [x] Implement `FileStateEngine`: Save/Load `SessionContext` to `.ganglia/state/session_<id>.json`.
    - [x] Ensure atomic writes to prevent corruption.
- [x] **Log Management:**
    - [x] Implement `LogManager`: Append raw turn interactions (Thought, Tool, Output) to `.ganglia/logs/<date>.md`.
    - [x] Integrate logging into `ReActAgentLoop` (via `StateEngine` or observer).

## Phase 2: Medium-Term Context & Compression (Tier 2)
**Objective:** Manage token window limits via smart summarization linked to tasks.

- [x] **Context Pruning Engine:**
    - [x] Implement `TokenCounter` (using JTokkit or similar) to monitor context usage.
    - [x] Implement `SlidingWindow` logic to identify when context exceeds limits.
- [x] **Compression Logic:**
    - [x] Develop `ContextCompressor`: Logic to take a list of `Turn` objects and ask the LLM to summarize them.
    - [x] Implement "Task-Turn" Lifecycle Hook:
        - [x] Detect when `todo_complete` is called.
        - [x] Trigger summarization of all Turns associated with that Task.
        - [x] Replace raw Turns in `SessionContext` with a summary note in `ToDoList`.

## Phase 3: Long-Term Knowledge (Tier 3)
**Objective:** Project-wide memory that persists across sessions.

- [x] **Knowledge Base Manager:**
    - [x] Implement `KnowledgeBase`: Read/Write `MEMORY.md`.
    - [x] Define standard sections for `MEMORY.md` (e.g., "User Preferences", "Project Conventions", "Architecture").
- [x] **Retrieval System:**
    - [x] Integrate `grep` / `read` capabilities specifically for `MEMORY.md` into the system prompt construction.
    - [x] **Agentic Search:** Verify the agent can autonomously decide to read `MEMORY.md` when lacking context.

## Phase 4: Integration & Tools
**Objective:** Expose memory capabilities to the agent.

- [x] **`remember` Tool:**
    - [x] Implement a tool for the agent to explicitly write facts to `MEMORY.md`.
- [x] **Context Injection:**
    - [x] Update `PromptEngine` to dynamically inject:
        - [x] The `ToDoList` (always).
        - [x] The compressed "Accomplishments" (from Tier 2).
        - [x] Relevant snippets from `MEMORY.md` (if explicitly retrieved or small enough).
- [x] **Verification:**
    - [x] Create Integration Test: "Long Context Session" where the agent performs multiple tasks, triggers compression, and successfully recalls early context via summary.
