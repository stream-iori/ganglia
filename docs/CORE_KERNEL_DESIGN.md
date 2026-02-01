# Ganglia Core Kernel Design

> **Module:** `ganglia-core`
> **Status:** Detailed Design
> **Package:** `me.stream.ganglia.core`

This document outlines the detailed class and interface definitions for the Core Kernel module.

## 1. Domain Models (Records)

Immutable data structures representing the core entities of the agent loop.

```java
package me.stream.ganglia.core.model;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Represents a single message in the conversation history.
 */
public record Message(
    String id,
    Role role, // SYSTEM, USER, ASSISTANT, TOOL
    String content,
    List<ToolCall> toolCalls, // Present if role is ASSISTANT and tools are called
    String toolCallId, // Present if role is TOOL (linking to the call)
    Instant timestamp
) {}

public enum Role {
    SYSTEM, USER, ASSISTANT, TOOL
}

/**
 * Represents a request for the agent to execute a tool.
 */
public record ToolCall(
    String id,
    String toolName,
    Map<String, Object> arguments // Parsed JSON arguments
) {}

/**
 * Represents the full context of a running session.
 */
public record SessionContext(
    String sessionId,
    List<Message> history,
    Map<String, Object> metadata, // Arbitrary session metadata
    List<String> activeSkillIds // Currently active skills
) {
    public SessionContext withNewMessage(Message msg) {
        // Returns a new SessionContext with the message appended
        // Implementation logic...
        return this;
    }
}
```

## 2. Model Abstraction (`ModelGateway`)

The interface for interacting with LLMs, abstracting away provider specifics (OpenAI, Anthropic).

```java
package me.stream.ganglia.core.llm;

import me.stream.ganglia.core.model.Message;
import me.stream.ganglia.core.model.ToolDefinition; // From tools module
import java.util.concurrent.Flow; // Java Flow API for streaming

public interface ModelGateway {
    
    /**
     * Sends a chat completion request to the LLM.
     */
    CompletionStage<ModelResponse> chat(
        List<Message> history,
        List<ToolDefinition> availableTools,
        ModelOptions options
    );

    /**
     * Streaming version of chat.
     */
    Flow.Publisher<ModelChunk> chatStream(
        List<Message> history,
        List<ToolDefinition> availableTools,
        ModelOptions options
    );
}

public record ModelResponse(
    String content,
    List<ToolCall> toolCalls,
    TokenUsage usage
) {}

public record TokenUsage(int promptTokens, int completionTokens) {}
```

## 3. The Agent Loop (`AgentLoop`)

The central orchestration engine implementing the ReAct logic.

```java
package me.stream.ganglia.core.loop;

import me.stream.ganglia.core.llm.ModelGateway;
import me.stream.ganglia.core.tools.ToolExecutor;
import me.stream.ganglia.core.model.SessionContext;

public interface AgentLoop {
    
    /**
     * Starts or resumes the agent loop with a user input.
     * Returns the final answer after the loop settles.
     */
    CompletionStage<String> run(String userInput, SessionContext context);
}

// Implementation
public class ReActAgentLoop implements AgentLoop {
    private final ModelGateway model;
    private final ToolExecutor toolExecutor;
    private final StateEngine stateEngine;
    private final int maxIterations;

    public CompletionStage<String> run(String userInput, SessionContext context) {
        // 1. Append User Input to Context
        // 2. Loop:
        //    a. Call Model (Context + Tools)
        //    b. If Text Content -> Stream to User (via UI callback)
        //    c. If Tool Call -> 
        //         i. Execute Tool (via ToolExecutor)
        //         ii. Append Tool Output (Observation) to Context
        //         iii. Continue Loop
        //    d. If Final Answer or No Tool Call -> Break
        // 3. Return Final Answer
    }
}
```

## 4. State Management (`StateEngine`)

Handles persistence and session lifecycle.

```java
package me.stream.ganglia.core.state;

import me.stream.ganglia.core.model.SessionContext;

public interface StateEngine {
    
    /**
     * Loads a session from disk/storage.
     */
    CompletionStage<SessionContext> loadSession(String sessionId);

    /**
     * Saves the current state of a session.
     * Must be atomic to ensure crash recovery.
     */
    CompletionStage<Void> saveSession(SessionContext context);
    
    /**
     * Creates a new empty session.
     */
    SessionContext createSession();
}
```

## 5. Prompt Engine (`PromptEngine`)

Constructs the dynamic system prompt.

```java
package me.stream.ganglia.core.prompt;

import me.stream.ganglia.core.model.SessionContext;

public interface PromptEngine {
    
    /**
     * Generates the System Message based on current context.
     * Injects:
     * - Base Persona
     * - Active Skills instructions
     * - Memory snippets (from Retrieval)
     * - Time/Date/OS Context
     */
    String buildSystemPrompt(SessionContext context);
}
```
