# Ganglia Daily Record Design (Daily Journal)

> **Status:** Initial Design
> **Module:** `ganglia-memory`
> **Related:** [Memory Architecture](MEMORY_ARCHITECTURE.md), [Context Engine](CONTEXT_ENGINE_DESIGN.md)

## 1. Objective
To provide a project-wide, cross-session "Daily Journal" that captures key accomplishments, technical decisions, and facts learned on a specific day. This bridging tier between ephemeral session history and static `MEMORY.md` helps the agent maintain continuity across multiple sessions in a single day.

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
    - Fixed API key configuration to support unified `LLM_API_KEY`.
  - **Technical Decisions:** Switched to direct field access for Gemini types due to SDK abstraction.

  ## [Session: bug-fix-loop-c3d4]
  - **Goal:** Fix VertxFileSystemToolsTest failures.
  - **Accomplishments:** Corrected tool names from `read_file` to `vertx_read` in the test suite.
  ```

## 3. Mechanism: The "Reflector" Loop

### 3.1 Extraction (The Reflection Hook)
When a `Turn` is completed or a `Session` is closed, the system triggers a **Reflection Step**:
1.  **Input:** The raw history of the turn/session.
2.  **Model Call:** A utility model (e.g., Haiku or GPT-4o-mini) is prompted to extract "Key Facts" and "Accomplishments".
3.  **Prompt Template:** 
    > "Summarize the following interaction into 2-3 bullet points focusing ONLY on what was actually done or learned. Use technical terms."

### 3.2 Persistence
- Use `FileStateEngine` or a dedicated `DailyLogManager` to append to the day's file.
- Ensures atomic writes to prevent corruption if multiple sessions try to log simultaneously (using Vert.x FileSystem locking or serialized queue).

## 4. Retrieval & Integration

### 4.1 Automatic Context Injection
The `ContextEngine` will automatically search for the current day's record and inject a summary into the system prompt:
- **Priority:** 9 (Between Current Plan and long-term MEMORY.md).
- **Benefit:** Allows the agent to remember what it just did in a *previous* session today without reading thousands of raw tokens.

### 4.2 Agentic Retrieval
The agent can use `grep_search` or `read_file` on the `.ganglia/memory/` directory to "look back" at previous days' work.

## 5. Implementation Steps
1.  Implement `DailyRecordManager` to handle file I/O and formatting.
2.  Add a `reflect` method to `ContextCompressor` to generate the summary points.
3.  Update `ReActAgentLoop` to trigger the manager at the end of each successful turn.
4.  Update `ContextResolver` to include `DailySource`.
