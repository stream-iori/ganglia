# Prompt Engine Refactoring Design

> **Status:** Draft
> **Related:** [Architecture](ARCHITECTURE.md), [Core Kernel](CORE_KERNEL_DESIGN.md), [Context Engine Design](CONTEXT_ENGINE_DESIGN.md)

## 1. Objective
Refactor the `PromptEngine` to be the primary authority for LLM interaction preparation. It will move away from just building a "System Prompt string" to building a full `LlmRequest` (or `PromptContext`) that includes:
- Layered System Prompts (using existing `ContextEngine` logic).
- Pruned/Compressed Conversation History.
- Model Options (with fallback and dynamic selection).
- Tool Definitions (consistent with current session/skill state).

This refactoring reduces the complexity of `ReActAgentLoop` by centralizing prompt-related concerns.

## 2. Core Models

### 2.1 `LlmRequest`
A comprehensive DTO representing the input for a `ModelGateway` call.
```java
public record LlmRequest(
    List<Message> messages,
    List<ToolDef> tools,
    ModelOptions options
) {}
```

## 3. Revised Interface: `PromptEngine`

```java
public interface PromptEngine {
    /**
     * Prepares the full request for the LLM.
     * 1. Builds System Prompt via ContextEngine.
     * 2. Prunes the session history to fit the token window.
     * 3. Resolves ModelOptions (including fallbacks).
     * 4. Fetches available tools (filtered by active skills/context).
     */
    Future<LlmRequest> prepareRequest(SessionContext context, int iteration);

    /**
     * Prunes a list of messages based on token limits.
     */
    List<Message> pruneHistory(List<Message> history, int maxTokens);
}
```

## 4. `StandardPromptEngine` Enhancements

- **History Pruning Logic:** Move `pruneHistory` from `ReActAgentLoop` into `StandardPromptEngine`.
- **Model Options Fallback:** Move the logic for choosing the right `ModelOptions` (and fallback values) here.
- **Context Composition:** Combine the `System Prompt`, the `User Task`, and the `History` into a final message list.
- **Configuration Integration:** Use `ConfigManager` to set default limits (e.g., history token window).

## 5. ReActAgentLoop Refactoring

The `reason` step in `ReActAgentLoop` becomes significantly simpler:

```java
private Future<ModelResponse> reason(SessionContext context, int iteration) {
    return promptEngine.prepareRequest(context, iteration)
        .compose(request -> {
            return model.chatStream(
                request.messages(), 
                request.tools(), 
                request.options(), 
                context.sessionId()
            );
        });
}
```

## 6. Token Budget Management (Multi-tier)
The `PromptEngine` can now manage the total token budget more effectively:
- **System Prompt Budget:** e.g., 2000 tokens (Managed by `ContextComposer`).
- **History Budget:** e.g., 4000 tokens (Managed by `pruneHistory`).
- **Total Input Limit:** Ensuring the sum doesn't exceed the model's `maxTokens` or hard limits.

## 7. Interaction Flow

```mermaid
sequenceDiagram
    participant Loop as ReActAgentLoop
    participant PE as PromptEngine
    participant CC as ContextComposer
    participant Model as LLM

    Loop->>PE: prepareRequest(SessionContext, iteration)
    PE->>CC: buildSystemPrompt(SessionContext)
    CC-->>PE: systemPromptString
    PE->>PE: pruneHistory(history, limit)
    PE->>PE: resolveModelOptions(context)
    PE-->>Loop: LlmRequest(messages, tools, options)
    
    Loop->>Model: chatStream(LlmRequest)
```

## 8. Benefits
- **Separation of Concerns:** `ReActAgentLoop` focuses on state transitions, `PromptEngine` focuses on LLM input preparation.
- **Testability:** `PromptEngine` can be tested in isolation to verify history pruning and prompt construction logic.
- **Flexibility:** Easier to implement dynamic model selection (e.g., using different models for different stages) without touching the agent loop.
