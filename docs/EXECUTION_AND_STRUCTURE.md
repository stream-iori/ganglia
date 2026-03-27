# Ganglia Core: Architecture & Execution Flow

> **Status:** In Development
> **Version:** 0.1.7-SNAPSHOT

This document provides a comprehensive overview of the structural relationships and the execution sequences of the Ganglia core system.

## 1. Structural Relationship Diagram

Ganglia follows a strict **Hexagonal (Ports and Adapters) Architecture**. The Kernel handles the pure reasoning logic and orchestrates tasks, interacting with the outside world exclusively through the Port layer. The Infrastructure layer implements these ports, allowing easy swapping of LLM providers, storage mechanisms, and tools.

```mermaid
graph TD
    subgraph API ["1. API / Adapter Layer"]
        WebUI["WebUI / WebSocket / JSON-RPC"]
        TUI["TerminalUI"]
    end

    subgraph Kernel ["2. Kernel Layer (The Heart)"]
        Loop["ReActAgentLoop"]
        Pipeline["InterceptorPipeline"]
        Dispatcher["ObservationDispatcher"]
        SchedFactory["AgentTaskFactory"]
        
        subgraph Tasks ["Tasks (AgentTask)"]
            ToolTask["StandardToolTask"]
            SubAgentTask["SubAgentTask"]
            SkillTask["SkillTask"]
        end
    end

    subgraph Port ["3. Port Layer (Contracts)"]
        PromptPort["PromptEngine"]
        ModelPort["ModelGateway"]
        SessionPort["SessionManager"]
        ToolPort["ToolSet"]
        MemoryPort["MemoryStore & DailyRecordManager"]
        HookPort["AgentInterceptor"]
    end

    subgraph Infra ["4. Infrastructure Layer (Implementations)"]
        NativeLLM["Native OpenAI/Anthropic Gateway"]
        LocalTools["BashTools / FileEditTools"]
        MCP["MCP Client Tools"]
        FSStore["FileSystemMemoryStore"]
        StateEngine["FileStateEngine"]
        ObservationHooks["ObservationCompressionHook"]
    end

    %% Relationships
    API -->|Sends Input| Loop
    Loop --> Pipeline
    Loop --> SchedFactory
    SchedFactory --> Tasks
    
    Loop --> Port
    Pipeline --> HookPort
    
    Infra -.->|Implements| Port
    Tasks --> ToolPort
    
    %% Specific implementations mapping to ports
    NativeLLM -.-> ModelPort
    LocalTools -.-> ToolPort
    FSStore -.-> MemoryPort
    ObservationHooks -.-> HookPort
```

## 2. Simplified Core Logic Flow

A high-level view of the primary reasoning cycle, focusing on the core interaction between the Agent, the LLM, and the Tools.

```mermaid
sequenceDiagram
    autonumber
    actor User
    participant Kernel as ReActAgentLoop
    participant Prompt as PromptEngine
    participant LLM as ModelGateway
    participant Tool as Tool Executor

    User->>Kernel: startTurn(userInput)
    
    loop ReAct Cycle
        Kernel->>Prompt: assemblePrompt(context)
        Prompt-->>Kernel: System + History + Tools
        
        Kernel->>LLM: generateRequest()
        LLM-->>Kernel: Thought + ToolCall
        
        alt Has ToolCall
            Kernel->>Tool: execute(args)
            Tool-->>Kernel: Observation (Result)
            Note over Kernel: Add Observation to History
        else Has Final Answer
            Note over Kernel: Reasoning complete
        end
    end

    Kernel-->>User: Final Response
```

## 3. Complete Execution Sequence Diagram

The following sequence diagram details the full lifecycle of a single user interaction ("Turn"). It highlights the integration of the `InterceptorPipeline`, the `PromptEngine` context assembly, the reasoning loop, tool execution, and memory compression.

```mermaid
sequenceDiagram
    autonumber
    actor User
    participant Kernel as ReActAgentLoop
    participant Pipe as InterceptorPipeline
    participant Disp as ObservationDispatcher
    participant Prompt as PromptEngine
    participant LLM as ModelGateway
    participant Tool as Tool Executor (Task)
    participant Mem as MemoryService / Store

    User->>Kernel: run(userInput, SessionContext)
    Kernel->>Disp: dispatch(TURN_STARTED)
    
    %% Pre-Turn Interception
    Kernel->>Pipe: executePreTurn(SessionContext, userInput)
    Pipe-->>Kernel: Modified SessionContext
    
    loop ReAct Cycle (Max N Iterations)
        %% Reasoning Phase
        Kernel->>Disp: dispatch(REASONING_STARTED)
        
        Kernel->>Prompt: prepareRequest(SessionContext)
        Note right of Prompt: Gathers ContextSources (Mandates, Skills, Memory, ToDo)<br/>and prunes history based on limits.
        Prompt-->>Kernel: LlmRequest (Messages + Tool Definitions)
        
        Kernel->>LLM: chatStream(LlmRequest)
        loop Token Streaming
            LLM->>Disp: dispatch(TOKEN_RECEIVED)
        end
        LLM-->>Kernel: ModelResponse
        Kernel->>Disp: dispatch(REASONING_FINISHED)
        
        %% Execution Phase
        alt Model requests Tool Calls
            loop For each ToolCall
                Kernel->>Pipe: executePreToolExecute(ToolCall)
                Pipe-->>Kernel: Validated ToolCall
                
                Kernel->>Disp: dispatch(TOOL_STARTED)
                Kernel->>Tool: execute(ToolCall, ExecutionContext)
                
                loop TTY Streaming
                    Tool->>Disp: dispatch(TOOL_OUTPUT_STREAM)
                end
                Tool-->>Kernel: ToolInvokeResult (Raw Observation)
                
                Kernel->>Pipe: executePostToolExecute(Result)
                Note right of Pipe: e.g., ObservationCompressionHook compresses<br/>outputs > 4000 chars and saves to MemoryStore.
                Pipe-->>Kernel: Final ToolInvokeResult
                
                Kernel->>Disp: dispatch(TOOL_FINISHED)
            end
            
            %% State Persist Check
            Kernel->>Kernel: Persist Session State
            
        else Final Answer provided
            Note right of Kernel: No tools called, reasoning complete.
            Kernel->>Kernel: Transition to Turn Complete
        end
    end
    
    %% Post-Turn Cleanup & Memory
    Kernel->>Disp: dispatch(TURN_FINISHED)
    Kernel->>Pipe: executePostTurn(SessionContext, FinalMessage)
    
    %% Async Reflection & Compression
    par Asynchronous Memory Maintenance
        Kernel->>Mem: publish(ganglia.memory.event)
        Note right of Mem: Triggers Daily Journaling & <br/>Context Compressor (if history > 70%)
    end

    Kernel-->>User: Final Output / Ready for next input
```

