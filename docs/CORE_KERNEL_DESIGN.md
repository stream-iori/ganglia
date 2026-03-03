# Ganglia Core Kernel Architecture (Implemented)

> **Module:** `ganglia-core`
> **Status:** Implemented (v1.1.0)
> **Package:** `me.stream.ganglia.core`

This document outlines the detailed design for the Core Kernel module using UML and Sequence diagrams.

## 1. Class Diagram: Core Components

This diagram illustrates the relationships between the main entities: `AgentLoop`, `ModelGateway`, `ScheduleableFactory`, and the `Scheduleable` tasks.

```mermaid
classDiagram
    class AgentLoop {
        <<Interface>>
        +run(userInput: String, context: SessionContext) Future~String~
    }
    
    class ReActAgentLoop {
        -model: ModelGateway
        -scheduleableFactory: ScheduleableFactory
        -sessionManager: SessionManager
        -promptEngine: PromptEngine
        -tokenCounter: TokenCounter
        +run(...)
        +resume(toolOutput: String, ...)
        -executeScheduleablesSequentially(...)
    }
    
    AgentLoop <|.. ReActAgentLoop
    
    class ScheduleableFactory {
        <<Interface>>
        +create(call: ToolCall, context: SessionContext) Scheduleable
        +getAvailableDefinitions(context: SessionContext) List~ToolDefinition~
    }

    class Scheduleable {
        <<Interface>>
        +id() String
        +name() String
        +execute(context: SessionContext) Future~ScheduleResult~
    }

    class StandardToolTask {
        -toolExecutor: ToolExecutor
    }
    class SubAgentTask {
        -vertx: Vertx
    }
    class SkillTask {
        -skillRuntime: SkillRuntime
    }
    class TaskGraphTask {
        -graphExecutor: GraphExecutor
    }

    Scheduleable <|.. StandardToolTask
    Scheduleable <|.. SubAgentTask
    Scheduleable <|.. SkillTask
    Scheduleable <|.. TaskGraphTask

    ReActAgentLoop --> ScheduleableFactory : uses to create tasks
    ScheduleableFactory ..> Scheduleable : produces
    
    class ModelGateway {
        <<Interface>>
        +chat(...) Future~ModelResponse~
        +chatStream(...) Future~Void~
    }
    
    class SessionContext {
        <<Record>>
        +sessionId: String
        +previousTurns: List~Turn~
        +currentTurn: Turn
        +metadata: Map~String, Object~
        +toDoList: ToDoList
    }

    ReActAgentLoop --> ModelGateway : uses
    ReActAgentLoop --> SessionManager : persists state
    ReActAgentLoop --> PromptEngine : prepares LLM inputs
```

## 2. Sequence Diagram: The ReAct Loop

This diagram details the flow of the `AgentLoop.run()` method, demonstrating the orchestration of `Scheduleable` tasks.

```mermaid
sequenceDiagram
    autonumber
    participant User
    participant AgentLoop
    participant PromptEngine
    participant Model as ModelGateway
    participant Factory as ScheduleableFactory
    participant Task as ScheduleableTask
    participant SM as SessionManager

    User->>AgentLoop: run(userInput, context)
    AgentLoop->>SM: startTurn & save
    
    loop ReAct Cycle (Max N times)
        Note over AgentLoop, Model: 1. Reasoning Phase
        AgentLoop->>PromptEngine: prepareRequest(Context)
        PromptEngine-->>AgentLoop: LlmRequest (Layered Prompts + Tools)
        
        AgentLoop->>Model: chatStream(LlmRequest)
        Model-->>AgentLoop: ModelResponse (Accumulated Content + ToolCalls)
        
        alt Has Tool Calls?
            Note over AgentLoop, Factory: 2. Scheduling Phase
            AgentLoop->>Factory: create(ToolCall)
            Factory-->>AgentLoop: ScheduleableTask
            
            Note over AgentLoop, Task: 3. Execution Phase (Sequential)
            AgentLoop->>Task: execute(Context)
            Task-->>AgentLoop: ScheduleResult (Observation)
            
            alt Any Task Interrupted (e.g. ask_selection)?
                AgentLoop-->>User: Return Prompt/Interrupt
                User->>AgentLoop: resume(Feedback)
                AgentLoop->>AgentLoop: Execute remaining Tasks
            end
            
            AgentLoop->>SM: addStep & save
            Note right of AgentLoop: Continue Loop -> Feed Observations back to Model
            
        else No Tool Calls (Final Answer)
            AgentLoop->>SM: completeTurn & save
            Note right of AgentLoop: Break Loop
        end
    end

    AgentLoop-->>User: Final Response String
```

## 3. Class Diagram: Model Abstraction

(Remains unchanged from v1.0.0)

```mermaid
classDiagram
    class ModelGateway {
        <<Interface>>
        +chat(...)
        +chatStream(...)
    }
    
    class OpenAIModelGateway {
        -client: OpenAIClient
        -vertx: Vertx
        +chat(...)
        +chatStream(...)
    }
    
    class AnthropicModelGateway {
        -client: AnthropicClient
        +chat(...)
    }
    
    ModelGateway <|.. OpenAIModelGateway
    ModelGateway <|.. AnthropicModelGateway
    
    class ModelOptions {
        <<Record>>
        +temperature: double
        +maxTokens: int
        +modelName: String
    }
    
    ModelGateway ..> ModelOptions : uses
```
