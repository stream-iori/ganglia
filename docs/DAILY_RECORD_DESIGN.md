# Ganglia Daily Record Architecture

> **Status:** In Development
> **Version:** 0.1.5
>
> **Module:** `ganglia-core`
> **Related:** [Memory Architecture](MEMORY_ARCHITECTURE.md), [Context Engine](CONTEXT_ENGINE_DESIGN.md)

## 1. Objective

To provide a project-wide "Daily Journal" that captures key accomplishments. This bridges ephemeral session history and static `MEMORY.md`.

## 2. Storage Specification

- **Path:** `.ganglia/memory/daily-YYYY-MM-DD.md`
- **Mechanism:** Asynchronous summarization of completed turns.

## 3. Mechanism: The Event-Driven Reflector

### 3.1 Triggering

When a `Turn` is completed, the `StandardAgentLoop` (Kernel) publishes a message to the EventBus.
- **Address**: `ganglia.memory.event` (Defined in `Constants.ADDRESS_MEMORY_EVENT`).

### 3.2 `MemoryService` (The Port Listener)

Located in `work.ganglia.port.internal.memory`. It listens for events and delegates to the `DailyJournalModule`.

1. **Summarize**: Uses a utility model to generate a concise Markdown bullet point.
2. **Persist**: Uses `FileSystemDailyRecordManager` to append to the daily file.

## 4. Automatic Context Injection

The `DailyContextSource` (Infrastructure) is added to the `PromptEngine` with **Priority 9**. It reads the current day's Markdown file and injects it into every new session's system prompt.
