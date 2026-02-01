# Ganglia Core Kernel Design

> **Module:** `ganglia-core`
> **Status:** Detailed Design (UML & Sequence)
> **Package:** `me.stream.ganglia.core`

This document outlines the detailed design for the Core Kernel module using UML and Sequence diagrams.

## 1. Class Diagram: Core Components

This diagram illustrates the relationships between the main entities: `AgentLoop`, `ModelGateway`, `StateEngine`, and the Domain Models.

```mermaid
classDiagram
    class AgentLoop {
        <<Interface>>
        +run(userInput: String, context: SessionContext) CompletionStage~String~
    }
    
    class ReActAgentLoop {
        -model: ModelGateway
        -toolExecutor: ToolExecutor
        -stateEngine: StateEngine
        -promptEngine: PromptEngine
        -maxIterations: int
        +run(...)
    }
    
    AgentLoop <|.. ReActAgentLoop
    
    class ModelGateway {
        <<Interface>>
        +chat(history: List~Message~, tools: List~ToolDef~, opts: ModelOptions) CompletionStage~ModelResponse~
        +chatStream(...) Publisher~ModelChunk~
    }
    
    class StateEngine {
        <<Interface>>
        +loadSession(sessionId: String) CompletionStage~SessionContext~
        +saveSession(context: SessionContext) CompletionStage~Void~
        +createSession() SessionContext
    }
    
    class PromptEngine {
        <<Interface>>
        +buildSystemPrompt(context: SessionContext) String
    }
    
    class SessionContext {
        <<Record>>
        +sessionId: String
        +history: List~Message~
        +metadata: Map~String, Object~
        +activeSkillIds: List~String~
        +withNewMessage(msg: Message) SessionContext
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

    ReActAgentLoop --> ModelGateway : uses
    ReActAgentLoop --> StateEngine : persists state
    ReActAgentLoop --> PromptEngine : constructs prompts
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
    participant PromptEngine
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
        AgentLoop->>PromptEngine: buildSystemPrompt(Context)
        PromptEngine-->>AgentLoop: systemPrompt
        
        AgentLoop->>Model: chat(history + systemPrompt, availableTools)
        Model-->>AgentLoop: ModelResponse (Content + ToolCalls)
        
        AgentLoop->>Context: withNewMessage(AssistantMessage)
        
        alt Has Tool Calls?
            Note over AgentLoop, ToolExec: 3. Execution Phase
            loop For each ToolCall
                AgentLoop->>ToolExec: execute(ToolCall)
                ToolExec-->>AgentLoop: ToolResult (Observation)
                AgentLoop->>Context: withNewMessage(ToolMessage/Observation)
            end
            AgentLoop->>State: saveSession(Context)
            Note right of AgentLoop: Continue Loop -> Feed Observation back to Model
            
        else No Tool Calls (Final Answer)
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
        +chat(...)
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