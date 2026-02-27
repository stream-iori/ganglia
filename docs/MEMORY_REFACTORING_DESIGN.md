# Memory & Usage Decoupling (Implemented)

> **Status:** Implemented (v1.0.0)
> **Related:** [Architecture](ARCHITECTURE.md), [Core Kernel](CORE_KERNEL_DESIGN.md), [Memory Architecture](MEMORY_ARCHITECTURE.md)

## 1. Objective
Decouple non-core logic (Memory Reflection, Daily Records, Token Usage Tracking) from the primary `ReActAgentLoop` to ensure a cleaner, more focused control loop and to allow these tasks to run fully asynchronously without affecting the agent's main execution flow.

## 2. Event-Driven Architecture

The system will transition to an event-driven model for auxiliary tasks using the Vert.x EventBus.

### 2.1 EventBus Addresses
| Address | Description | Payload |
| :--- | :--- | :--- |
| `ganglia.memory.reflect` | Trigger reflection and daily recording. | `{ "sessionId": "...", "goal": "...", "turn": { Turn object } }` |
| `ganglia.usage.record` | Record token usage for a session. | `{ "sessionId": "...", "usage": { TokenUsage object } }` |

## 3. New Components

### 3.1 `MemoryService`
**Responsibility:** Aggregates memory-related background tasks.
- **Listeners:** Listens on `ganglia.memory.reflect`.
- **Logic:**
    1. Extracts `Turn` and `goal` from the message.
    2. Calls `ContextCompressor.reflect(turn)` to generate a summary.
    3. Calls `DailyRecordManager.record(sessionId, goal, summary)` to persist the daily record.
- **Resilience:** Errors during reflection or recording are logged but do not impact the caller.

### 3.2 `TokenUsageManager`
**Responsibility:** Tracks and persists token usage statistics.
- **Listeners:** Listens on `ganglia.usage.record`.
- **Logic:**
    1. Extracts `TokenUsage` and `sessionId`.
    2. Aggregates usage (e.g., in-memory map or persistent store).
    3. (Optional) Emits a "usage summary" event when thresholds are reached.

## 4. `ReActAgentLoop` Refactoring

- **Simplified Constructor:** Remove dependencies on `ContextCompressor` and `DailyRecordManager`.
- **Passive Notification:**
    - At the end of a turn, publish a JSON message to `ganglia.memory.reflect`.
    - Every time a `ModelResponse` is received, publish a JSON message to `ganglia.usage.record`.

## 5. Interaction Flow

```mermaid
sequenceDiagram
    participant Loop as ReActAgentLoop
    participant EB as EventBus
    participant Mem as MemoryService
    participant Usage as TokenUsageManager
    participant Model as LLM

    Loop->>Model: chatStream(...)
    Model-->>Loop: ModelResponse(usage)
    Loop->>EB: publish(ganglia.usage.record, usage)
    EB->>Usage: handleRecord(usage)

    Note over Loop: ... executes tools ...

    Loop->>EB: publish(ganglia.memory.reflect, turn)
    EB->>Mem: handleReflect(turn)
    Mem->>Model: reflect(turn)
    Model-->>Mem: summary
    Mem->>FS: record(summary)
    
    Loop-->>User: Final Answer
```

## 6. Benefits
- **Separation of Concerns:** `ReActAgentLoop` only cares about the reasoning loop.
- **Asynchronous Execution:** Memory reflection (which involves another LLM call) runs in the background.
- **Extensibility:** New listeners can be added to the EventBus without modifying the core loop (e.g., a billing service, a dashboard).
