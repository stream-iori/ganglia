# Ganglia Memory Architecture

> **Philosophy:** "Memory as Code". Transparent, user-controlled, file-based, and tiered.

## 1. Overview

Ganglia implements a **Three-Tier Memory System** designed to balance high-fidelity reasoning (for immediate tasks) with long-term retention (for project context), all while managing token window limits efficiently.

## 2. The Three-Tier Structure

### Tier 1: Short-Term Memory (The "Turn")
*   **Scope:** A single User-Agent interaction cycle (e.g., "Fix bug X").
*   **Granularity:** Extremely high. Contains raw "Thoughts", exact "Tool Calls", and full "Observations" (e.g., file contents, command outputs).
*   **Storage:** In-memory `Turn` objects within `SessionContext`.
*   **Lifecycle:** Active only while the specific step is being executed. Once the step is complete, it is candidate for compression.

### Tier 2: Medium-Term Memory (The "Context Window")
*   **Scope:** The active session history.
*   **Granularity:** Hybrid.
    *   *Recent Turns:* Kept in full detail.
    *   *Older Turns:* Pruned or compressed based on token limits.
*   **Mechanism:** **Sliding Window with Semantic Pruning**.
    *   The `ReActAgentLoop` automatically prunes history to maintain a maximum of **2000 tokens** of conversation context per request.
*   **Integration with ToDo:**
    *   The **Plan/ToDo List** serves as the backbone of this tier.
    *   When a task in the Plan is marked `DONE`, the associated Turns are summarized into a concise "Result" (e.g., "Refactored User.java to Record").
    *   The raw Turns are evicted from the prompt context but saved to disk.

### Tier 3: Long-Term Memory (The "Project Knowledge")
*   **Scope:** Cross-session project lifespan.
*   **Storage:** 
    *   `MEMORY.md`: Curated "lessons learned", architectural decisions, and user preferences.
    *   `.ganglia/logs/`: Archived raw logs of past sessions (searchable via tools).
*   **Retrieval:** **Agentic Search**. The agent uses tools (`grep`, `read`) to actively look up information from this tier when needed.

## 3. Compression & Summarization Strategy

To prevent context overflow, Ganglia employs an aggressive summarization strategy linked to the Task lifecycle.

### The "Task-Turn" Cycle
1.  **Expansion:** User gives a goal -> Agent adds it to ToDo List.
2.  **Execution:** Agent performs multiple Turns (Reason -> Act -> Observe). These accumulate in the Context.
3.  **Completion:** Agent marks task as `DONE`.
4.  **Compression (The Hook):**
    *   **Trigger:** Task status change to `DONE`.
    *   **Action:** The system (or a background "Reflector" agent) takes all Turns associated with that task and generates a 1-2 sentence summary.
    *   **Replacement:** The raw Turns are removed from `SessionContext.previousTurns` and replaced by the summary in the `ToDoList` (e.g., as a "Result" note) or a dedicated "Completed Tasks" history block.

## 4. State Persistence

*   **Session State:** Serialized to `.ganglia/state/session_ID.json` after every Turn. Includes the ToDo list, active variables, and the compressed history.
*   **Resumption:** Loading a session restores the ToDo list and the *compressed* context, not the full raw history of every past command (unless specifically requested).

## 5. Memory-Tool Integration

*   **`todo_complete`:** Not just a status update. It signals the memory system to "Pack up this context".
*   **`remember`:** A specific tool for the agent to write important facts to `MEMORY.md` (promoting from Short/Medium to Long-term).