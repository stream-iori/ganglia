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

## Phase 5: Advanced Interaction (Interrupts)
**Objective:** Enable the agent to ask for help or selection from the user.

- [x] **Interrupt Mechanism:**
    - [x] Update `ToolType` to include `INTERRUPT`.
    - [x] Update `ReActAgentLoop` to pause when an interrupt tool is called.
- [x] **`ask_selection` Tool:**
    - [x] Implement `SelectionTools` with `ask_selection(question, options)`.
    - [x] Ensure options are presented clearly (e.g., indexed list).
- [x] **Resume Mechanism:**
    - [x] Implement method to resume session with user selection (injected as Tool Result).

## Phase 6: Skill System
**Objective:** Enable domain-specific expertise through modular packages.

- [x] **Skill Foundation:**
    - [x] Define `SkillPackage` and `SkillManifest` models.
    - [x] Implement `SkillRegistry` for local and classpath discovery.
- [x] **Lifecycle & Integration:**
    - [x] Implement `SkillManager` to track `activeSkillIds` in `SessionContext`.
    - [x] Develop `SkillPromptInjector` to aggregate and inject prompts from active skills into `PromptEngine`.
    - [x] Update `ToolExecutor` to support dynamic registration of tools from active skills.
- [x] **Agentic Interaction:**
    - [x] Implement `list_available_skills` tool.
    - [x] Implement `activate_skill` tool.
    - [x] Add automatic skill suggestion logic based on file pattern triggers.
- [x] **Verification:**
    - [x] Create a "Java Expert" demo skill and verify the agent can activate it and use its specialized prompts/tools.

## Phase 7: Low-Latency Streaming
**Objective:** Improve UX by providing real-time feedback during reasoning.

- [x] **Kernel Refactoring:**
    - [x] Update `ReActAgentLoop` to use `ModelGateway.chatStream`.
    - [x] Implement a standard EventBus addressing scheme for session-specific streams (`ganglia.stream.<sessionId>`).
- [x] **UI Integration:**
    - [x] Implement a listener in the main entry point (or UI layer) to pipe EventBus tokens to `stdout`.
- [x] **Tool Call Handling:**
    - [x] Ensure tool calls are still correctly accumulated and executed sequentially after the stream completes.
- [x] **Verification:**
    - [x] Verify that the agent's "Thought" process is visible to the user as it is being generated.
