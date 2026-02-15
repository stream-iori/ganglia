# Ganglia 核心内核设计 (Core Kernel)

> **模块**：`ganglia-core`
> **状态**：详细设计（UML 与时序图）
> **包名**：`me.stream.ganglia.core`

本文档使用 UML 和时序图概述了核心内核模块的详细设计。

## 1. 类图：核心组件

此图展示了主要实体之间的关系：`AgentLoop`、`ModelGateway`、`SessionManager` 和领域模型。

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
        +chatStream(...) Future~ModelResponse~
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
        +intermediateSteps: List~Message~
        +finalResponse: Message
        +flatten() List~Message~
    }
    
    class Message {
        <<Record>>
        +id: String
        +role: Role
        +content: String
        +toolCalls: List~ToolCall~
        +toolCallId: String
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
    ToolExecutor ..> ToolExecutionException : 抛出

    ReActAgentLoop --> ModelGateway : 使用
    ReActAgentLoop --> SessionManager : 管理状态
    ReActAgentLoop --> PromptEngine : 构建分层提示词
    ReActAgentLoop ..> SessionContext : 操纵
    SessionContext *-- Message
    Message *-- ToolCall
```

## 2. 时序图：ReAct 循环

此图详细说明了 `AgentLoop.run()` 方法的流程，展示了“思考 -> 工具 -> 观察”的循环。

```mermaid
sequenceDiagram
    autonumber
    participant User
    participant AgentLoop
    participant PromptEngine
    participant Context as SessionContext
    participant Model as ModelGateway
    participant ToolExec as ToolExecutor
    participant SM as SessionManager

    User->>AgentLoop: run(userInput, context)
    
    Note over AgentLoop, Context: 1. 初始化
    AgentLoop->>SM: startTurn(context, userMessage)
    AgentLoop->>SM: saveSession(Context)

    loop ReAct 循环 (最多 N 次)
        Note over AgentLoop, Model: 2. 推理阶段
        AgentLoop->>AgentLoop: pruneHistory (修剪历史以适应 Token 限制)
        AgentLoop->>PromptEngine: buildSystemPrompt(Context)
        PromptEngine-->>AgentLoop: systemPrompt (分层且已修剪)
        
        AgentLoop->>Model: chatStream(history + systemPrompt, availableTools, streamAddr)
        
        par 实时反馈
            Model-->>User: [流式] 推送 Token 到 EventBus (streamAddr)
        and 内部累积
            Model-->>AgentLoop: ModelResponse (累积内容 + 工具调用)
        end
        
        AgentLoop->>SM: addStep(AssistantMessage)
        
        alt 包含工具调用?
            Note over AgentLoop, ToolExec: 3. 执行阶段 (顺序执行)
            
            AgentLoop->>ToolExec: execute(All ToolCalls)
            
            alt 工具被中断 (例如 ask_selection)?
                ToolExec-->>AgentLoop: Interrupt Result
                AgentLoop-->>User: 返回提示词 / 中断信号
                User->>AgentLoop: resume(反馈内容)
                AgentLoop->>AgentLoop: 继续执行剩余工具调用
            end

            ToolExec-->>AgentLoop: ToolResults (观察结果)
            AgentLoop->>SM: addStep(ToolMessages)
            
            AgentLoop->>SM: saveSession(Context)
            Note right of AgentLoop: 继续循环 -> 将观察结果反馈给模型
            
        else 无工具调用 (最终回答)
            Note right of AgentLoop: 跳出循环
        end
    end

    Note over AgentLoop, User: 4. 完成阶段
    AgentLoop->>SM: completeTurn(AssistantMessage)
    AgentLoop->>SM: saveSession(Context)
    AgentLoop-->>User: 最终回答字符串
```

## 3. 类图：模型抽象

详细说明支持多个 LLM 提供商的层级结构。

```mermaid
classDiagram
    class ModelGateway {
        <<Interface>>
        +chat(...)
        +chatStream(...)
    }
    
    class OpenAIModelGateway {
        -client: OpenAIClientAsync
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
    
    ModelGateway ..> ModelOptions : 使用
```
