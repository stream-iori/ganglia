# Ganglia Core Kernel Architecture

> **Status:** In Development
> **Version:** 0.1.7-SNAPSHOT
>
> **Module:** `ganglia-harness`
> **Package:** `work.ganglia.kernel`

This document outlines the detailed design for the Core Kernel module, emphasizing its isolation within the Hexagonal architecture.

## 1. Class Diagram: Kernel vs. Port

The Kernel represents the pure logic of the reasoning loop, interacting with the environment only through stable interfaces (Ports).

```mermaid
classDiagram
    class AgentLoop {
        <<Interface>>
        +run(userInput: String, context: SessionContext, signal: AgentSignal) Future~String~
        +stop(sessionId: String)
    }
    
    class ReActAgentLoop {
        -model: ModelGateway
        -taskFactory: AgentTaskFactory
        -sessionManager: SessionManager
        -promptEngine: PromptEngine
        -dispatcher: ObservationDispatcher
        +run(...)
        +resume(...)
        -runLoop(...)
    }

    AgentLoop <|.. ReActAgentLoop

    class AgentTask {
        <<Interface>>
        +id() String
        +execute(context: SessionContext, executionContext: ExecutionContext) Future~AgentTaskResult~
    }

    subgraph Tasks [Kernel Tasks]
        class StandardToolTask
        class SubAgentTask
        class SkillTask
        class TaskGraphTask
    end

    AgentTask <|.. StandardToolTask
    AgentTask <|.. SubAgentTask
    AgentTask <|.. SkillTask
    AgentTask <|.. TaskGraphTask

    subgraph Port [Port Layer - work.ganglia.port]
        class ModelGateway { <<Interface>> }
        class SessionManager { <<Interface>> }
        class ToolSet { <<Interface>> }
        class PromptEngine { <<Interface>> }
        class ObservationDispatcher { <<Interface>> }
        class ExecutionContext { <<Interface>> }
    end

    ReActAgentLoop --> ModelGateway : uses
    ReActAgentLoop --> SessionManager : uses
    ReActAgentLoop --> PromptEngine : uses
    ReActAgentLoop --> ObservationDispatcher : uses
    StandardToolTask --> ToolSet : uses
    StandardToolTask ..> ExecutionContext : provides
```

## 2. Sequence Diagram: The ReAct Loop

The Kernel coordinates the flow between Prompt preparation, Model interaction, and Task execution, utilizing a unified dispatcher for observations.

```mermaid
sequenceDiagram
    autonumber
    participant User
    participant Loop as ReActAgentLoop
    participant Disp as ObservationDispatcher
    participant Port as PortLayer (Prompt/Model/Session)
    participant Task as AgentTask

    User->>Loop: run(userInput, context, signal)
    Loop->>Disp: dispatch(TURN_STARTED)
    
    loop ReAct Cycle (Max Iterations)
        Note over Loop, Port: 1. Reasoning Phase
        Loop->>Disp: dispatch(REASONING_STARTED)
        Loop->>Port: prepareRequest(Context)
        Port-->>Loop: LlmRequest
        
        Loop->>Port: chatStream(LlmRequest, ExecutionContext)
        
        loop Token Streaming
            Port->>Disp: dispatch(TOKEN_RECEIVED)
        end
        
        Port-->>Loop: ModelResponse (Finalized)
        Loop->>Disp: dispatch(REASONING_FINISHED)
        
        alt Has Tool Calls?
            Note over Loop, Task: 2. Execution Phase
            Loop->>Loop: create tasks via Factory
            
            loop For each ToolCall
                Loop->>Disp: dispatch(TOOL_STARTED)
                Loop->>Task: execute(Context, ExecutionContext)
                
                loop TTY Streaming
                    Task->>Disp: dispatch(TOOL_OUTPUT_STREAM)
                end
                
                Task-->>Loop: AgentTaskResult (Observation)
                Loop->>Disp: dispatch(TOOL_FINISHED)
            end
            
            Loop->>Port: saveSession(Context)
            
        else No Tool Calls (Final Answer)
            Loop->>Disp: dispatch(TURN_FINISHED)
            Note right of Loop: Break Loop
        end
    end

    Loop-->>User: Final Response String
```

## 3. Data Integrity & Immutability

The Kernel relies on immutable records from the `work.ganglia.port.chat` package:
- **`Message`**: Represents a single interaction (User, Assistant, Tool).
- **`Turn`**: A collection of Reasoning/Action steps.
- **`SessionContext`**: The complete state of a session, evolved using functional updates (e.g., `context.withNewTurn(...)`).

## 4. Decoupling Rationale

By separating the **Kernel** from **Infrastructure** via **Ports**:
1. **Model Agnostic**: Changing from OpenAI to Anthropic requires no changes to the reasoning loop logic.
2. **Persistence Agnostic**: The Kernel doesn't know if state is saved to a JSON file or a database.
3. **Testability**: The Kernel can be fully unit tested by mocking the Port interfaces.

### 4.1 Dependency Assembly (0.1.6)

To resolve circular dependencies between `AgentLoopFactory` and `AgentTaskFactory` without reflection or proxies, `GangliaKernel` employs a **Late-Binding Assembly** pattern:
- **AgentEnv** acts as the central registry.
- **AgentLoopFactory** is defined as a lambda that pulls the `taskFactory` from the `AgentEnv` only when a loop is actually created.
- This ensures a clean, one-way dependency flow during bootstrapping while allowing the runtime to remain fully interconnected.
