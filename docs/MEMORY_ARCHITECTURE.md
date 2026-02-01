# Caprice Memory Architecture

> **Philosophy:** "Memory as Code". Transparent, user-controlled, and file-based.

## 1. Overview

Unlike traditional agent frameworks that rely heavily on hidden Vector Databases (RAG), Caprice treats memory as transparent files within the user's project. This allows users to read, edit, and version-control the agent's "knowledge".

## 2. The Two-Layer Structure

### Layer 1: Ephemeral Memory (The Stream)
*   **Storage:** `.ganglia/logs/YYYY-MM-DD.md`
*   **Content:**
    *   Full conversation history.
    *   Raw "Thoughts" (Chain of Thought).
    *   Tool execution logs (Inputs and Outputs).
*   **Purpose:**
    *   Immediate context for the current session.
    *   Audit trail for debugging hallucinations.
    *   Source material for curation.

### Layer 2: Curated Memory (The Knowledge Base)
*   **Storage:** `MEMORY.md` (in project root)
*   **Content:**
    *   **User Preferences:** "Always use JUnit 5", "Prefer record classes".
    *   **Architectural Decisions:** "We use Hexagonal Architecture".
    *   **Lessons Learned:** "The API at x.com requires header Y".
    *   **Project Context:** "The auth module is in `src/auth`".
*   **Purpose:**
    *   Long-term retention across sessions.
    *   Injecting "common sense" specific to the project.

## 3. Retrieval Mechanism: "Agentic Search"

Instead of passively stuffing the context window with RAG chunks, Caprice relies on **Active Retrieval**.

1.  **Trigger:** The agent recognizes a need for information (e.g., "I need to know the database schema").
2.  **Action:** The agent uses its tools:
    *   `Grep` to search for keywords in `MEMORY.md` or code.
    *   `Read` to inspect specific logs or documentation.
3.  **Synthesis:** The agent reads the search results and incorporates them into its current reasoning.

**Why this approach?**
*   **Accuracy:** Code search tools (`grep`) are often more precise than semantic vector search for technical keywords.
*   **Context:** The agent sees the information in its original file context, not as an isolated chunk.
*   **Control:** The user can explicitly guide the agent to "Read MEMORY.md".

## 4. State Persistence

Session state is not stored in a complex database.
*   **Resumption:** To resume a session, the system simply re-loads the specific Markdown log file into the context window (pruning if necessary).
*   **Portability:** If the user pushes the `.ganglia` folder to Git, the "mind" of the agent travels with the code.
