# Clawdbot Memory Architecture
> **Core Concept:** Transparent, user-controlled, file-based memory system.

## 1. Philosophy: Memory as Code
*   **Transparency:** Memory is not hidden in an opaque vector database or cloud service. It is stored as plain text (Markdown).
*   **User Ownership:** The memory files live in the user's project/repo. They can be read, edited, and version-controlled (Git) just like code.
*   **Debuggability:** If the agent has a "false memory" or hallucination, the user can simply open the file and fix it.

## 2. The Two-Layer Memory Structure
*   **Short-Term / Ephemeral (The Stream):**
    *   Stored in daily or session-based files (e.g., `logs/2023-10-27.md`).
    *   Captures the raw "stream of consciousness", full conversation logs, and immediate actions.
    *   High-volume, low-curation.
*   **Long-Term / Curated (The Knowledge Base):**
    *   Stored in a dedicated `MEMORY.md` (or `system_context.md`) file.
    *   Contains crystallized knowledge: User preferences, key project decisions, architectural constraints, and "lessons learned".
    *   **The "Refinement" Process:** The agent (or user) periodically summarizes important bits from the Short-Term logs and moves them into the Long-Term memory file.

## 3. Retrieval Mechanism: Search > Injection
*   **Context Window Limits:** Instead of trying to inject *all* memory into the prompt (which is expensive and confusing), use retrieval.
*   **Mechanism:**
    *   When the agent needs to recall something, it uses its **Tools** (`grep`, `read_file`) to search its own memory files.
    *   This treats "Memory" just like another part of the codebase.
    *   *Agentic Retrieval:* The agent actively queries its memory ("What did the user say about the database schema yesterday?") rather than passively receiving it.

## 4. Advantages over Vector RAG
*   **Simplicity:** No need for a separate vector DB (Milvus/Pinecone) infrastructure.
*   **Context Preservation:** Text files preserve the *structure* and *relationship* of information better than chunked vectors.
*   **Portability:** The memory moves with the project repo.
