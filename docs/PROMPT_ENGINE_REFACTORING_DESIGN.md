# Prompt Engine Architecture

> **Status:** In Development
> **Version:** 0.1.5
>
> **Package:** `work.ganglia.port.internal.prompt` (Contract) / `work.ganglia.infrastructure.internal.prompt` (Impl)
> **Related:** [Architecture](../ARCHITECTURE.md), [Core Kernel](CORE_KERNEL_DESIGN.md)

## 1. Objective

Refactor the `PromptEngine` to be the primary authority for LLM interaction preparation. It centralizes system prompt construction, history pruning, and tool definition resolution.

## 2. Core Models

### 2.1 `LlmRequest`

A comprehensive DTO defined in `work.ganglia.port.external.llm`.

```java
public record LlmRequest(
    List<Message> messages,
    List<ToolDefinition> tools,
    ModelOptions options
) {}
```

## 3. Interfaces & Ports

### 3.1 `PromptEngine` (Port)

Defines the contract for preparing LLM inputs.

```java
public interface PromptEngine {
    /**
     * Prepares the full request for the LLM.
     * Includes layered system prompts, pruned history, and tool definitions.
     */
    Future<LlmRequest> prepareRequest(SessionContext context, int iteration);
}
```

### 3.2 `ContextSource` (Port)

Standardized provider for system prompt fragments (Persona, Mandates, Env, etc.).
- **`WorkflowContextSource`**: Interface for the **Process Layer** (e.g., Research-Strategy-Execution).
- **`GuidelineContextSource`**: Interface for the **Rule Layer** (e.g., Senior Engineer guidelines).

## 4. `StandardPromptEngine` (Infrastructure)

- **Context Composition:** Uses `ContextComposer` to aggregate fragments from various `ContextSource` implementations.
- **History Pruning:** Intelligently prunes `Turn` history based on `contextLimit` from configuration.
- **Factory Integration:** Collaborates with `SchedulableFactory` to fetch `ToolDefinition` objects relevant to the active context (e.g., skill-specific tools).

## 5. Interaction Flow

```mermaid
sequenceDiagram
    participant Loop as StandardAgentLoop
    participant PE as StandardPromptEngine
    participant CC as ContextComposer
    participant SF as SchedulableFactory

    Loop->>PE: prepareRequest(SessionContext, iteration)
    PE->>CC: buildSystemPrompt(SessionContext)
    CC-->>PE: Aggregated Markdown
    PE->>SF: getAvailableDefinitions(context)
    SF-->>PE: List<ToolDefinition>
    PE->>PE: pruneHistory(context)
    PE-->>Loop: LlmRequest
```

## 6. Implementation Detail: Constants

The engine uses centralized constants from `work.ganglia.util.Constants`:
- `FILE_GANGLIA_MD`: Root instructions.
- `DEFAULT_GANGLIA_DIR`: Core data folder.
