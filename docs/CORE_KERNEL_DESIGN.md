# Ganglia Core Kernel Architecture (Implemented)

> **Module:** `ganglia-core`
> **Status:** Implemented (v1.1.0)
> **Package:** `me.stream.ganglia.core`

This document outlines the detailed design for the Core Kernel module using UML and Sequence diagrams.

## 1. Class Diagram: Core Components

This diagram illustrates the relationships between the main entities: `AgentLoop`, `ModelGateway`, `SchedulableFactory`, and the `Schedulable` tasks.

```mermaid
classDiagram
    class AgentLoop {
        <<Interface>>
        +run(userInput: String, context: SessionContext, signal: AgentSignal) Future~String~
    }
    
    class StandardAgentLoop {
        -model: ModelGateway
        -scheduleableFactory: SchedulableFactory
        -sessionManager: SessionManager
        -promptEngine: PromptEngine
        -tokenCounter: TokenCounter
        +run(...)
        +resume(toolOutput: String, ...)
        -executeSchedulablesSequentially(...)
    }
    
    AgentLoop <|.. StandardAgentLoop
    
    class SchedulableFactory {
        <<Interface>>
        +create(call: ToolCall, context: SessionContext) Schedulable
        +getAvailableDefinitions(context: SessionContext) List~ToolDefinition~
    }

    class Schedulable {
        <<Interface>>
        +id() String
        +name() String
        +execute(context: SessionContext) Future~SchedulableResult~
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

    Schedulable <|.. StandardToolTask
    Schedulable <|.. SubAgentTask
    Schedulable <|.. SkillTask
    Schedulable <|.. TaskGraphTask

    StandardAgentLoop --> SchedulableFactory : uses to create tasks
    SchedulableFactory ..> Schedulable : produces
    
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

    class SessionManager {
        <<Interface>>
        +addSteeringMessage(sessionId: String, msg: String)
        +pollSteeringMessages(sessionId: String) List~String~
    }

    StandardAgentLoop --> ModelGateway : uses
    StandardAgentLoop --> SessionManager : persists state & polls messages
    StandardAgentLoop --> PromptEngine : prepares LLM inputs
```

## 2. Sequence Diagram: The ReAct Loop

This diagram details the flow of the `AgentLoop.run()` method, demonstrating the orchestration of `Schedulable` tasks.

```mermaid
sequenceDiagram
    autonumber
    participant User
    participant AgentLoop
    participant PromptEngine
    participant Model as ModelGateway
    participant Factory as SchedulableFactory
    participant Task as SchedulableTask
    participant SM as SessionManager

    User->>AgentLoop: run(userInput, context, signal)
    AgentLoop->>SM: startTurn & save
    
    loop ReAct Cycle (Max N times)
        Note over AgentLoop, Model: 1. Reasoning Phase
        AgentLoop->>SM: pollSteeringMessages()
        opt Has Steering Messages
            AgentLoop->>AgentLoop: Inject to Context
        end

        AgentLoop->>PromptEngine: prepareRequest(Context)
        PromptEngine-->>AgentLoop: LlmRequest (Layered Prompts + Tools)
        
        AgentLoop->>Model: chatStream(LlmRequest)
        Model-->>AgentLoop: ModelResponse (Accumulated Content + ToolCalls)
        
        alt Has Tool Calls?
            Note over AgentLoop, Factory: 2. Scheduling Phase
            AgentLoop->>Factory: create(ToolCall)
            Factory-->>AgentLoop: SchedulableTask
            
            Note over AgentLoop, Task: 3. Execution Phase (Sequential)
            AgentLoop->>SM: pollSteeringMessages()
            opt Has Steering Messages
                AgentLoop->>AgentLoop: Inject to Context & Abort pending Tasks
            end

            AgentLoop->>Task: execute(Context)
            Task-->>AgentLoop: SchedulableResult (Observation)
            
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
