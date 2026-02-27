# Ganglia Core Kernel Architecture (Implemented)

> **Module:** `ganglia-core`
> **Status:** Implemented (v1.0.0)
> **Package:** `me.stream.ganglia.core`

This document outlines the detailed design for the Core Kernel module using UML and Sequence diagrams.

## 1. Class Diagram: Core Components

This diagram illustrates the relationships between the main entities: `AgentLoop`, `ModelGateway`, `StateEngine`, and the Domain Models.

```mermaid
classDiagram
    class AgentLoop {
        <<Interface>>
        +run(userInput: String, context: SessionContext) Future~String~
    }
    
    class ReActAgentLoop {
        -model: ModelGateway
        -toolExecutor: ToolExecutor
        -sessionManager: SessionManager
        -promptEngine: PromptEngine
        -tokenCounter: TokenCounter
        -maxIterations: int
        +run(...)
        +resume(toolOutput: String, ...)
        -pruneHistory(history: List~Message~, maxTokens: int) List~Message~
    }
    
    AgentLoop <|.. ReActAgentLoop
    
    class ModelGateway {
        <<Interface>>
        +chat(history: List~Message~, tools: List~ToolDef~, opts: ModelOptions) Future~ModelResponse~
        +chatStream(...) Future~Void~
    }
    
    class StateEngine {
        <<Interface>>
        +loadSession(sessionId: String) Future~SessionContext~
        +saveSession(context: SessionContext) Future~Void~
        +createSession() SessionContext
    }
    
    class ContextEngine {
        <<Interface>>
        +buildSystemPrompt(context: SessionContext) Future~String~
    }
    
    class SessionContext {
        <<Record>>
        +sessionId: String
        +previousTurns: List~Turn~
        +currentTurn: Turn
        +metadata: Map~String, Object~
        +activeSkillIds: List~String~
        +modelOptions: ModelOptions
        +toDoList: ToDoList
        +withNewMessage(msg: Message) SessionContext
        +withModelOptions(opts: ModelOptions) SessionContext
        +withToDoList(list: ToDoList) SessionContext
        +flattenHistory() List~Message~
    }

    class Turn {
        <<Record>>
        +id: String
        +userMessage: Message
        +steps: List~Message~
        +response: Message
        +flatten() List~Message~
    }
    
    class Message {
        <<Record>>
        +id: String
        +role: Role
        +content: String
        +toolCalls: List~ToolCall~
    }
    
    class ToolCall {
        <<Record>>
        +id: String
        +toolName: String
        +arguments: Map~String, Object~
    }

    class ToolDefinition {
        <<Record>>
        +name: String
        +description: String
        +jsonSchema: String
        +isInterrupt: boolean
    }

    class ToolErrorResult {
        <<Record>>
        +toolName: String
        +errorType: ErrorType
        +message: String
        +exitCode: Integer
        +partialOutput: String
    }

    class ToolExecutionException {
        <<Exception>>
        +errorResult: ToolErrorResult
    }

    ToolExecutionException *-- ToolErrorResult
    ToolExecutor ..> ToolExecutionException : throws

    ReActAgentLoop --> ModelGateway : uses
    ReActAgentLoop --> StateEngine : persists state
    ReActAgentLoop --> ContextEngine : constructs layered prompts
    ReActAgentLoop ..> SessionContext : manipulates
    SessionContext *-- Message
    Message *-- ToolCall
```

## 2. Sequence Diagram: The ReAct Loop

This diagram details the flow of the `AgentLoop.run()` method, demonstrating the cycle of Thought -> Tool -> Observation.

```mermaid
sequenceDiagram
    autonumber
    participant User
    participant AgentLoop
    participant ContextEngine
    participant Context as SessionContext
    participant Model as ModelGateway
    participant ToolExec as ToolExecutor
    participant State as StateEngine

    User->>AgentLoop: run(userInput, context)
    
    Note over AgentLoop, Context: 1. Initialization
    AgentLoop->>Context: withNewMessage(UserMessage)
    AgentLoop->>State: saveSession(Context)

    loop ReAct Cycle (Max N times)
        Note over AgentLoop, Model: 2. Reasoning Phase
        AgentLoop->>AgentLoop: pruneHistory(2000 tokens)
        AgentLoop->>PromptEngine: buildSystemPrompt(Context)
        PromptEngine-->>AgentLoop: systemPrompt (Layered & Pruned to 2000 tokens)
        
        AgentLoop->>Model: chatStream(prunedHistory + systemPrompt, availableTools, streamAddr)
        
        par Real-time Feedback
            Model-->>User: [Stream] Publish tokens to EventBus (streamAddr)
        and Internal Accumulation
            Model-->>AgentLoop: ModelResponse (Accumulated Content + ToolCalls)
        end
        
        alt Has Tool Calls?
            Note over AgentLoop, ToolExec: 3. Execution Phase (Sequential)
            AgentLoop->>ToolExec: execute(All ToolCalls)
            ToolExec-->>AgentLoop: ToolResults (Observations)
            
            alt Any Tool Interrupted (e.g. ask_selection)?
                AgentLoop-->>User: Return Prompt/Interrupt
                User->>AgentLoop: resume(Feedback)
                AgentLoop->>AgentLoop: Execute remaining ToolCalls
            end
            
            AgentLoop->>State: saveSession(Context with Observations)
            Note right of AgentLoop: Continue Loop -> Feed Observations back to Model
            
        else No Tool Calls (Final Answer)
            AgentLoop->>State: completeTurn & saveSession
            Note right of AgentLoop: Break Loop
        end
    end

    Note over AgentLoop, User: 4. Finalization
    AgentLoop->>State: saveSession(Context)
    AgentLoop-->>User: Final Response String
```

## 3. Class Diagram: Model Abstraction

Detailing the hierarchy for supporting multiple LLM providers.

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
    
    class LocalModelGateway {
        -client: HttpClient
        +chat(...)
    }
    
    ModelGateway <|.. OpenAIModelGateway
    ModelGateway <|.. AnthropicModelGateway
    ModelGateway <|.. LocalModelGateway
    
    class ModelOptions {
        <<Record>>
        +temperature: double
        +maxTokens: int
        +modelName: String
    }
    
    ModelGateway ..> ModelOptions : uses
```