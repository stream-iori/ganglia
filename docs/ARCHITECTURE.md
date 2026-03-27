# Ganglia Architecture Documentation

> **Status:** In Development
> **Version:** 0.1.7-SNAPSHOT

## 1. System Overview

**Ganglia** is a Java-based Agent framework designed for integration into third-party applications. It prioritizes **simplicity, robustness, and transparency** over complex, opaque multi-agent graphs.

The core design philosophy follows a **Hexagonal (Ports & Adapters)** architecture, ensuring that the central reasoning loop is decoupled from specific model providers and technical implementations.

## 2. Core Design Principles

1. **Single Control Loop (The "ReAct" Loop):**
   - Avoid complex graphs or state machines for the core reasoning.
   - Use a flat message history processed by a single main loop.
   - Flow: `Input -> [Thought -> Tool -> Observation] * N -> Answer`.
2. **Hexagonal Decoupling:**
   - The **Kernel** (Reasoning) is isolated from **Infrastructure** (LLM, Storage).
   - All dependencies flow inward towards the Kernel and the Domain Port layer.
3. **Unified Observation Stream:**
   - All system activities (macro loop events and micro streaming tokens/TTY) are unified into a single EventBus-driven pipeline.
   - Decouples event generation (Tools/Gateways) from transport/UI logic.
4. **Tool-First Navigation:**
   - The agent explores codebases using tools (`grep`, `list`, `read`) rather than relying purely on pre-computed embeddings.
5. **Memory as Code:**
   - Memory is stored in **Markdown files** (`.ganglia/memory/MEMORY.md`, Daily Records).
   - It is transparent, editable, and version-controlled.
6. **Startup Self-Check & Configuration (0.1.6):**
   - The system automatically validates and initializes the necessary directory structure (`.ganglia/skills`, `memory`, `state`, `logs`, etc.) and configuration at bootstrap.
   - **ConfigLoader** handles recursive file resolution and Vert.x-based hot-reloading (IO), while **ConfigManager** maintains the state and provides domain-specific interfaces (SRP).

## 3. Logical Architecture (Hexagonal)

### 3.0 Architectural Layers

The system is organized into four primary hexagonal layers.

```mermaid
graph TD
    subgraph API ["1. API / Adapter Layer"]
        WebUI["WebUiVerticle (WebSocket / JSON-RPC 2.0)"]
        TUI["TerminalUI (Standalone Module)"]
        Demos["Example Applications"]
    end

    subgraph Kernel ["2. Kernel Layer (The Heart)"]
        Loop["ReActAgentLoop (Reasoning)"]
        Dispatcher["ObservationDispatcher (Unified Routing)"]
        SchedFactory["AgentTaskFactory (Orchestration)"]
    end

    subgraph Port ["3. Port Layer (Contracts & Domain)"]
        Chat["Chat Domain (Message, Turn, Context)"]
        IntPorts["Internal Ports (Memory, Prompt, ExecutionContext)"]
        ExtPorts["External Ports (ModelGateway, ToolSet)"]
    end

    subgraph Infra ["4. Infrastructure Layer (Implementation)"]
        Gateways["Native LLM Gateways (OpenAI, Anthropic Protocols)"]
        Tools["Standard Tool Implementations (Bash, FS)"]
        MCP["MCP Client (Stdio / SSE)"]
        Observability["TraceManager & WebUiEventPublisher (Event Consumers)"]
        Vertx["Vert.x Core (EventBus, WebClient)"]
    end

    API --> Kernel
    Kernel --> Port
    Infra -- implements --> Port
    Kernel -.-> Port
    Observability -.->|Consumes| Dispatcher
```

### 3.1 The Kernel Layer ("The Brain")

- **Reasoning Loop:** `ReActAgentLoop` manages the iterative cycle of Thought, Action, and Observation.
- **Observation Dispatcher:** `DefaultObservationDispatcher` acts as the central hub for all events. It broadcasts macro events (from the loop) and micro events (from tools via `ExecutionContext`) to a unified EventBus topic (`ganglia.observations.*`).
- **Robustness:** Includes failure policies, jittered exponential backoff retries, and configurable timeouts.

### 3.2 The Port Layer ("The Contracts")

- **Internal Ports:** Define how the Kernel interacts with its internal systems. Crucially includes `ExecutionContext` which allows tools to emit stream chunks without knowing about Vert.x.
- **External Ports:** Define interactions with LLMs (`ModelGateway`) and Environment (`ToolSet`).
- **Domain Models:** Records like `Message`, `SessionContext`, and `ObservationEvent` ensure data integrity.

### 3.3 The Infrastructure Layer ("The Hands")

- **Configuration Engine (0.1.6):**
  - `ConfigLoader`: Responsible for file path resolution, ensuring default config files exist, and initializing the Vert.x `ConfigRetriever` for watching.
  - `ConfigManager`: Implements multi-domain providers (`ModelConfigProvider`, `AgentConfigProvider`, etc.) and provides a clean, DRY API for the Kernel to access settings.
- **Native LLM Gateways:** Implements protocols via Vert.x `WebClient`. Supports explicit timeouts and broadened retry logic for connection resets.
- **MCP Client:** Implements the Model Context Protocol. Allows Ganglia to dynamically load tools from external MCP servers (configured via `.ganglia/.mcp.json`).
- **Event Consumers:** `WebUiEventPublisher` and `TraceManager` are decoupled from the loop; they simply consume the unified observation stream and forward it to WebSockets or Files.
- **Persistence:** Unified via `FileSystemUtil`, ensuring standard structures like `.ganglia/config.json`, `.mcp.json`, and session state folders exist.

## 4. The Memory System

- **Three-Tier Architecture:**
  - **Short-Term (Turn):** Raw interaction details.
  - **Medium-Term (Session):** Managed via rolling compression.
  - **Daily Journal:** Cross-session summaries in `.ganglia/memory/daily-*.md`.
  - **Long-Term (Project):** `.ganglia/memory/MEMORY.md`.
- **Deduplication & Timeline:** The system preserves a literal timeline of thoughts and tool calls, calculating and displaying execution/reasoning duration for every step.

## 5. Human-in-the-Loop & Steering

Ganglia supports an asynchronous **"Steering & Abort"** mechanism:

1. **Soft Steering:** Users can inject new instructions into a session queue at any time. The Kernel checks this between reasoning steps.
2. **Hard Abort:** An `AgentSignal` provides **active cancellation**. It allows for immediate termination of network calls (HTTP stream reset), tool executions, and token publishing, ensuring the UI stops instantly.
3. **Interrupts:** Sensitive tools (like `ask_selection`) can pause the loop to await explicit user input via modal forms.

## 6. WebUI Observation & Control (The 3x3 Matrix)

Starting from 0.1.7, the WebUI is built with **React 18 + TypeScript + shadcn/ui**:
- **Glance (Low Load)**: Real-time Phase indicators, Mini-mode ToolCards, and **Continuous Timeline** with duration metrics.
- **Inspect (Medium Load)**: A side-drawer `Inspector` with TTY virtualization (TanStack Virtual), regex-based log filtering, and high-fidelity Code/Diff viewers (Prism.js).
- **Block (High Load)**: Modal `AskUserForm` with embedded Diff context for high-stakes authorization and decision making.

## 7. Technology Stack

- **Language:** Java 17+
- **Core Framework:** Vert.x 5 (Reactive, Non-blocking I/O)
- **Transport:** Native WebSockets (RFC 6455)
- **Protocol:** JSON-RPC 2.0
- **UI:** React 18, Vite, Tailwind CSS, Zustand, shadcn/ui
- **Monitoring:** JDK WatchService (Recursive native hooks)
- **Testing:** JUnit 5, Mockito, JaCoCo, Vitest + RTL (WebUI)

