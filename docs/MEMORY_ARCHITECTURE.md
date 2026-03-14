> **Status:** Implemented (v1.5.0)
> **Package:** `work.ganglia.port.internal.memory` / `work.ganglia.infrastructure.internal.memory`
> **Philosophy:** "Memory as Code". Transparent, user-controlled, file-based, and tiered.

## 1. Overview

Ganglia implements a **Three-Tier Memory System** designed to balance high-fidelity reasoning with long-term retention.

## 2. The Three-Tier Structure

### Tier 1: Short-Term Memory (The "Turn")
*   **Scope:** A single User-Agent interaction cycle.
*   **Implementation:** In-memory `Turn` objects within `SessionContext`.
*   **Timeline:** Starting from v1.5.0, the UI preserves a full literal timeline of thoughts and tool calls, including duration metrics for each step.

### Tier 2: Medium-Term Memory (The "Context Window")
*   **Scope:** The active session history.
*   **Mechanism:** **Proactive Rolling Compression** (Triggered at 70% threshold).
*   **Action:** `ContextCompressor` (Reflector) generates a dense "State Summary" to replace raw turns.

### Tier 2.5: Daily Journal (Cross-Session)
*   **Scope:** All activity within a project in a single day.
*   **Implementation:** `DailyRecordManager` persists summaries to `.ganglia/memory/daily-YYYY-MM-DD.md`.
*   **Role:** Bridges the gap between session details and permanent project knowledge.
*   **Loading:** **Passive**. The journal for the current day is automatically injected into the system prompt.

### Tier 3: Long-Term Memory (The "Project Knowledge")
*   **Scope:** Cross-session project lifespan.
*   **Implementation:** 
    *   `MEMORY.md`: Curated "lessons learned" and preferences.
    *   `.ganglia/logs/`: Archived raw logs.
*   **Addition:** **Active**. The agent uses the `remember` tool to append facts. Users can also manually edit this file.
*   **Retrieval:** **Agentic / On-Demand**. The agent is instructed that it has access to `MEMORY.md` and should use `grep_search` or `read_file` to recall context when needed. This prevents context bloat while keeping knowledge accessible.

## 3. Compression & Summarization Strategy
...

To prevent context overflow, Ganglia employs an aggressive summarization strategy.

### The "Task-Turn" Cycle
1.  **Expansion:** User gives a goal -> Agent adds it to `ToDoList`.
2.  **Execution:** Agent performs multiple Turns (Reason -> Act -> Observe).
3.  **Completion:** Agent marks task as `DONE` via `ToDoTools`.
4.  **Compression (The Hook):** `MemoryService` (EventBus listener) triggers reflection on the completed turn/task.

## 4. State Persistence

*   **Session State:** `FileStateEngine` serializes to `.ganglia/state/session_ID.json`.
*   **Resumption:** Loading a session restores the `ToDoList` and compressed history.

## 5. Memory-Tool Integration

*   **`todo_complete`**: Signals the memory system to pack up context.
*   **`remember`**: Tool for the agent to write facts to `MEMORY.md`.
*   **`grep_search`**: Search over logs and memory files.