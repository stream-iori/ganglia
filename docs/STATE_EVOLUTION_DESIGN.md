# State Evolution & Immutability Design

> **Status:** In Development
> **Version:** 0.1.7-SNAPSHOT
>
> **Package:** `work.ganglia.port.chat` (Models)
> **Related:** [Architecture](../ARCHITECTURE.md), [Core Kernel](CORE_KERNEL_DESIGN.md)

## 1. Core Design Philosophy: Immutability

All core state objects in Ganglia (e.g., `SessionContext`, `Turn`, `Message`) are implemented as Java 17 `record` types. We strictly follow the **Functional Update** pattern: **state, once created, is never mutated; any change produces a new snapshot**.

### 1.1 Morphism & Evolution

In mathematics and functional programming this pattern is called an **Endomorphism**:
* **Pattern**: `f(InitialState, Event) -> NewState`
* **Implementation**: Objects expose `with...` methods that return a new record instance.

```java
// Example: chained evolution of SessionContext
SessionContext nextContext = initialContext
    .withNewMessage(userMsg)
    .withToDoList(updatedList);
```

## 2. Why Immutable Records?

### 2.1 Eliminating Race Conditions (Concurrency Safety)

Ganglia runs on Vert.x's async non-blocking event loop. Because state is immutable, no `synchronized` keywords or complex locking are needed. A `SessionContext` snapshot can be safely passed across multiple async callbacks and EventBus listeners without risk of mid-flight mutation.

### 2.2 Lossless Debugging & Replay (Time-Travel Debugging)

Every snapshot is a precise point on the agent's execution trace.
* **Engineering value**: If the agent goes off-track during a complex ReAct cycle, the exact `SessionContext` at the moment of failure can be serialized, replayed locally, and used for regression testing.
* **Auditability**: The logging system records the sequence of state transitions, not just the final outcome.

### 2.3 Manual Lens Pattern

For deeply nested records (e.g., `Context -> Turn -> Message -> ToolObservation`), we simulate the functional **Lens** pattern by manually implementing `with...` methods at each level. This keeps code for modifying deep properties highly readable and structurally consistent.

## 3. Structural Integrity

### 3.1 Aggregation & Decoupling

By aggregating role-specific attributes into inner records (e.g., `ToolObservation` inside `Message`), we ensure:
1. **Semantic clarity**: Only `Role.TOOL` messages carry tool observation data.
2. **Type safety**: Eliminates the need for null checks or ambiguity from flattened fields.

### 3.2 Logical Purity

This pattern forces developers to write pure-function logic. `ReActAgentLoop` no longer "mutates state" — it "orchestrates the flow of state".

## 4. Summary

In Ganglia, `record` is more than a data container — it is an **immutable node in the state machine**. Through this functional-style design, we trade structural stability for logical robustness, ensuring the agent remains coherent and reliable when processing long-running, highly concurrent tasks.
