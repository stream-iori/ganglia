# Ganglia Daily Record Architecture (Implemented)

> **Status:** Implemented (v1.0.0)
> **Module:** `ganglia-core` (Memory System)
> **Related:** [Memory Architecture](MEMORY_ARCHITECTURE.md), [Context Engine](CONTEXT_ENGINE_DESIGN.md), [Memory Refactoring](MEMORY_REFACTORING_DESIGN.md)

## 1. Objective
To provide a project-wide, cross-session "Daily Journal" that captures key accomplishments and facts. This bridges ephemeral session history and static `MEMORY.md`, ensuring the agent maintains continuity across multiple sessions in a single day.

## 2. Storage Specification
- **Path:** `.ganglia/memory/daily-YYYY-MM-DD.md`
- **Format:** Markdown with structured headers.
- **Content Structure:**
  ```markdown
  # Daily Record: 2026-02-15

  ## [Session: interactive-demo-a1b2] 
  - **Goal:** Implement Gemini Model Gateway.
  - **Accomplishments:** 
    - Successfully integrated Google GenAI SDK.
  - **Technical Decisions:** Switched to direct field access for Gemini types.
  ```

## 3. Mechanism: The Event-Driven Reflector

The system uses an asynchronous, decoupled approach to generate summaries.

### 3.1 Triggering
When a `Turn` is completed, the `StandardAgentLoop` publishes a message to the Vert.x EventBus at `ganglia.memory.reflect`.

### 3.2 `MemoryService` (The Listener)
The `MemoryService` listens for reflection events and:
1.  **Invokes Utility Model:** Calls a secondary LLM (or the primary one) to summarize the turn.
2.  **Appends to File:** Uses `FileSystemDailyRecordManager` to atomically append the summary to the day's Markdown file.

## 4. Retrieval & Integration

### 4.1 Automatic Context Injection
The `ContextComposer` includes a `DailyContextSource` (Priority 9):
- **Role:** Injects the current day's summaries into the system prompt.
- **Benefit:** The agent "remembers" what it did in previous sessions today without bloating the history with raw tokens.

### 4.2 Agentic Retrieval
The agent can use `grep_search` on the `.ganglia/memory/` directory to review work from previous days.
