# Ganglia Session Management

> **Status:** In Development
> **Version:** 0.1.5
>
> **Package:** `work.ganglia.port.internal.state` (Contract) / `work.ganglia.infrastructure.internal.state` (Impl)
> **Related:** [Architecture](../ARCHITECTURE.md), [Core Kernel](CORE_KERNEL_DESIGN.md)

## 1. Introduction

The `SessionManager` provides a high-level API for managing the lifecycle of agent sessions. It abstracts the complexities of state persistence, turn tracking, and context initialization, allowing clients to interact with the agent through stable session identifiers.

## 2. Core Concepts

### 2.1 `SessionContext` (Review)

The `SessionContext` is the immutable state of a session at a specific point in time. It contains:
- `sessionId`: Unique identifier.
- `previousTurns`: History of completed turns.
- `currentTurn`: The turn currently being executed.
- `toDoList`: The active plan.
- `activeSkillIds`: Skills currently enabled.

### 2.2 `Turn` (Review)

A `Turn` represents a single User-Agent interaction. It tracks:
- `userMessage`: The initial input.
- `steps`: Intermediate thoughts and tool calls.
- `response`: The final answer.

## 3. Session Manager interface

The `SessionManager` acts as the primary coordinator for session operations.

```java
public interface SessionManager {
    /**
     * Retrieves an existing session or creates a new one if it doesn't exist.
     */
    Future<SessionContext> getSession(String sessionId);

    /**
     * Persists the current state of a session.
     */
    Future<Void> saveSession(SessionContext context);

    /**
     * Creates a new, empty session context with default options.
     */
    SessionContext createSession();

    /**
     * Archives or deletes a session.
     */
    Future<Void> closeSession(String sessionId);
}
```

## 4. Implementation Details

### 4.1 `DefaultSessionManager`

- **Dependency:** Uses `StateEngine` for physical persistence (JSON files).
- **Caching:** May implement a basic in-memory cache for active sessions to reduce I/O during the Reasoning Loop.
- **Initialization:** Ensures that new sessions are initialized with the default `ModelOptions` and an empty `ToDoList`.

### 4.2 Turn Lifecycle Management

The `SessionManager` should provide helpers to transition between turns:
1. **Start Turn:** Move `currentTurn` to `previousTurns` (if exists) and create a new `currentTurn`.
2. **Update Turn:** Append steps (thoughts/tool calls) to the `currentTurn`.
3. **Complete Turn:** Set the final response.

## 5. Sequence: Session Interaction

```mermaid
sequenceDiagram
    participant Client
    participant SM as SessionManager
    participant State as StateEngine
    participant Loop as AgentLoop

    Client->>SM: getSession(sessionId)
    SM->>State: loadSession(sessionId)
    State-->>SM: SessionContext
    SM-->>Client: SessionContext

    Client->>Loop: run(input, context)
    Loop->>Loop: Execute ReAct Steps
    Loop->>SM: saveSession(updatedContext)
    SM->>State: saveSession(updatedContext)
    Loop-->>Client: Final Response
```

## 6. Client-side Session Tracking & History

While the backend is the source of truth for session content, the WebUI manages session persistence and switching:
1.  **LocalStorage Persistence**: Recent `sessionId`s are tracked in the browser's LocalStorage.
2.  **Session Switcher**: A dedicated Sidebar tab allowing users to jump between different problem-solving contexts.
3.  **Bootstrap Sync**: Upon switching or reloading, the UI sends a JSON-RPC `SYNC` request to the backend to hydrate the message stream and retrieve the full execution history.

## 7. Integration with `Main`

...
The `Main` class (or its equivalent in `example`) will use the `SessionManager` to manage the lifecycle of the user interaction, rather than manually instantiating `SessionContext`.
