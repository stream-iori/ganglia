# Memory & Usage Decoupling (Implemented)

> **Status:** Implemented (v1.2.0)
> **Related:** [Architecture](../ARCHITECTURE.md), [Core Kernel](CORE_KERNEL_DESIGN.md)

## 1. Objective
Decouple cognitive background tasks (Memory Reflection, Token Tracking) from the primary reasoning loop to ensure high responsiveness and modularity.

## 2. Event-Driven Architecture

The system uses the Vert.x EventBus for all non-critical, auxiliary tasks. Addresses are centralized in `work.ganglia.util.Constants`.

### 2.1 Core EventBus Addresses
| Address | Constant | Description |
| :--- | :--- | :--- |
| `ganglia.memory.event` | `ADDRESS_MEMORY_EVENT` | Trigger summarization & journaling. |
| `ganglia.usage.record` | `ADDRESS_USAGE_RECORD` | Persist actual token consumption. |
| `ganglia.usage.estimate` | `ADDRESS_USAGE_ESTIMATE` | Real-time usage estimation for UI. |

## 3. Interaction Flow

```mermaid
sequenceDiagram
    participant Loop as StandardAgentLoop (Kernel)
    participant EB as EventBus
    participant Mem as MemoryService (Port)
    participant Usage as TokenUsageManager (Infra)

    Loop->>EB: publish(ADDRESS_USAGE_RECORD, usage)
    EB->>Usage: persistUsage(usage)

    Note over Loop: ... executes ReAct cycle ...

    Loop->>EB: send(ADDRESS_MEMORY_EVENT, turnData)
    EB->>Mem: handleReflect(turnData)
    Mem->>LLM: reflect(turnData)
    LLM-->>Mem: summary
    Mem->>FS: appendToDailyLog(summary)
```

## 4. Key Components

- **`MemoryService` (Port)**: Acts as the registry for memory-related background listeners.
- **`TokenUsageManager` (Infrastructure)**: An implementation that tracks costs and quota per session.
- **`StandardAgentLoop` (Kernel)**: Only responsible for firing these "fire-and-forget" events at specific lifecycle hooks.

## 5. Benefits
- **Zero Latency Impact**: Memory reflection (which requires an LLM call) happens entirely in the background.
- **Observability**: External services can subscribe to `ganglia.observations.*` to monitor the agent's internal state in real-time.
