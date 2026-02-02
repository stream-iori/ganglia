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

- [ ] **Context Pruning Engine:**
    - [ ] Implement `TokenCounter` (using JTokkit or similar) to monitor context usage.
    - [ ] Implement `SlidingWindow` logic to identify when context exceeds limits.
- [ ] **Compression Logic:**
    - [ ] Develop `ContextCompressor`: Logic to take a list of `Turn` objects and ask the LLM to summarize them.
    - [ ] Implement "Task-Turn" Lifecycle Hook:
        - [ ] Detect when `todo_complete` is called.
        - [ ] Trigger summarization of all Turns associated with that Task.
        - [ ] Replace raw Turns in `SessionContext` with a summary note in `ToDoList`.

## Phase 3: Long-Term Knowledge (Tier 3)
**Objective:** Project-wide memory that persists across sessions.

- [ ] **Knowledge Base Manager:**
    - [ ] Implement `KnowledgeBase`: Read/Write `MEMORY.md`.
    - [ ] Define standard sections for `MEMORY.md` (e.g., "User Preferences", "Project Conventions", "Architecture").
- [ ] **Retrieval System:**
    - [ ] Integrate `grep` / `read` capabilities specifically for `MEMORY.md` into the system prompt construction.
    - [ ] **Agentic Search:** Verify the agent can autonomously decide to read `MEMORY.md` when lacking context.

## Phase 4: Integration & Tools
**Objective:** Expose memory capabilities to the agent.

- [ ] **`remember` Tool:**
    - [ ] Implement a tool for the agent to explicitly write facts to `MEMORY.md`.
- [ ] **Context Injection:**
    - [ ] Update `PromptEngine` to dynamically inject:
        - [ ] The `ToDoList` (always).
        - [ ] The compressed "Accomplishments" (from Tier 2).
        - [ ] Relevant snippets from `MEMORY.md` (if explicitly retrieved or small enough).
- [ ] **Verification:**
    - [ ] Create Integration Test: "Long Context Session" where the agent performs multiple tasks, triggers compression, and successfully recalls early context via summary.
