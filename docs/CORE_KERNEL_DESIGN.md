# Ganglia Core Kernel Architecture (Implemented)

> **Module:** `ganglia-core`
> **Status:** Implemented (v1.2.0)
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
    
    class StandardAgentLoop {
        -model: ModelGateway
        -scheduleableFactory: SchedulableFactory
        -sessionManager: SessionManager
        -promptEngine: PromptEngine
        +run(...)
        +resume(...)
        -runLoop(...)
    }
    
    AgentLoop <|.. StandardAgentLoop
    
    class Schedulable {
        <<Interface>>
        +id() String
        +execute(context: SessionContext) Future~SchedulableResult~
    }

    subgraph Tasks [Kernel Tasks]
        class ToolTask
        class SubAgentTask
        class SkillTask
        class TaskGraphTask
    end

    Schedulable <|.. ToolTask
    Schedulable <|.. SubAgentTask
    Schedulable <|.. SkillTask
    Schedulable <|.. TaskGraphTask

    subgraph Port [Port Layer - work.ganglia.port]
        class ModelGateway { <<Interface>> }
        class SessionManager { <<Interface>> }
        class ToolExecutor { <<Interface>> }
        class PromptEngine { <<Interface>> }
    end

    StandardAgentLoop --> ModelGateway : uses
    StandardAgentLoop --> SessionManager : uses
    StandardAgentLoop --> PromptEngine : uses
    ToolTask --> ToolExecutor : uses
```

## 2. Sequence Diagram: The ReAct Loop

The Kernel coordinates the flow between Prompt preparation, Model interaction, and Task execution.

```mermaid
sequenceDiagram
    autonumber
    participant User
    participant Loop as StandardAgentLoop
    participant Port as PortLayer (Prompt/Model/Session)
    participant Task as SchedulableTask

    User->>Loop: run(userInput, context, signal)
    Loop->>Port: startTurn & persist
    
    loop ReAct Cycle (Max Iterations)
        Note over Loop, Port: 1. Reasoning Phase
        Loop->>Port: prepareRequest(Context)
        Port-->>Loop: LlmRequest (Layered Prompts + Tools)
        
        Loop->>Port: chatStream(LlmRequest)
        Port-->>Loop: ModelResponse (Accumulated Content + ToolCalls)
        
        alt Has Tool Calls?
            Note over Loop, Task: 2. Execution Phase
            Loop->>Loop: create tasks via Factory
            
            loop For each ToolCall
                Loop->>Task: execute(Context)
                Task-->>Loop: SchedulableResult (Observation)
                Loop->>Port: publishObservation
            end
            
            Loop->>Port: persist(Context)
            
        else No Tool Calls (Final Answer)
            Loop->>Port: completeTurn & persist
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
