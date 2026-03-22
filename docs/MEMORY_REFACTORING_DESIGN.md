# Ganglia Context Compression & Memory Architecture

> **Status:** In Development
> **Version:** 0.1.5

To solve the "amnesia" problem and context window bloat in Ganglia, we have introduced three new core capabilities based on the "Progressive Disclosure" and "Hybrid Search" principles.

## 1. Core Components

### `MemoryStore` (Long-term Storage)

- **Interface:** `work.ganglia.port.internal.memory.MemoryStore`
- **Implementation:** `work.ganglia.infrastructure.internal.memory.FileSystemMemoryStore`
- **Purpose:** Stores `MemoryEntry` records as JSON files in `.ganglia/memory/entries/`.
- **Hybrid Search:** Supports multi-dimensional filtering by keyword (String match), category (Enum), and tags (KV pairs).
- **Progressive Disclosure:** Maintains a lightweight in-memory index (`MemoryIndexItem`) for fast prompt injection.

### `ObservationCompressor` (Real-time Compression)

- **Interface:** `work.ganglia.port.internal.memory.ObservationCompressor`
- **Implementation:** `work.ganglia.infrastructure.internal.memory.LlmObservationCompressor`
- **Purpose:** Intercepts large tool outputs (threshold: 4000 characters) in the kernel loop and uses an LLM to generate a concise summary.
- **Workflow:** Compressed observations are stored in `MemoryStore` and replaced in the chat history with a summary containing a Recall ID.

### `TimelineLedger` (System Medical Record)

- **Interface:** `work.ganglia.port.internal.memory.TimelineLedger`
- **Implementation:** `work.ganglia.infrastructure.internal.memory.MarkdownTimelineLedger`
- **Purpose:** Automatically records system-critical events, decisions, and refactorings into `.ganglia/memory/TIMELINE.md`.

## 2. Integration Details

### Kernel Integration

`StandardToolTask` intercepts successful tool executions. If the output is too long, it:
1. Calls `ObservationCompressor` to get a summary.
2. Generates a unique 8-character ID.
3. Saves the original output and summary as a `MemoryEntry` in `MemoryStore`.
4. Returns a message to the agent: *"Output was very long and has been compressed. ID: [id]. Summary: [summary]. Use recall_memory tool to view full content."*

### Prompt Integration

`MemoryContextSource` pulls the 10 most recent memory index items from `MemoryStore` and injects them into the system prompt. This allows the Agent to see what it "knows" without loading full contents.

### Recall Tool

`RecallMemoryTools` provides the `recall_memory(id)` tool, allowing the Agent to fetch the full content of any compressed observation or stored memory on demand.

## 3. Storage Structure

```
.ganglia/
└── memory/
    ├── entries/
    │   ├── abcdef12.json
    │   └── ...
    ├── TIMELINE.md
    └── MEMORY.md (Standard knowledge base)
```

