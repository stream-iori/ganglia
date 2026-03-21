# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Test Commands

This project uses `just` as a task runner and Maven for Java builds.

```bash
# Setup
just setup                    # mvn clean install -DskipTests + npm install

# Build
mvn clean install -DskipTests # Build all Java modules
just build-all                # Full production build (UI + Backend JAR)

# Run
just backend                  # Start WebUIDemo on port 8080
just frontend                 # Vite dev server on port 5173

# Tests
just test-backend             # Unit tests: mvn test -pl ganglia-core,ganglia-web,ganglia-terminal
just test-it                  # Integration tests: mvn verify -pl integration-test
just test-it-one AgentLoopIT  # Single IT: mvn verify -Dit.test=AgentLoopIT -pl integration-test
just test-frontend            # Frontend: cd ganglia-webui && npx vitest run
just coverage                 # JaCoCo coverage report
```

## Architecture

Ganglia is a Java 17 agent framework on Vert.x 5.0.6 using **Hexagonal (Ports & Adapters)** architecture with a **ReAct reasoning loop**.

### Module Layout

|       Module        |                                                                  Purpose                                                                   |
|---------------------|--------------------------------------------------------------------------------------------------------------------------------------------|
| `ganglia-core`      | Kernel (reasoning loop, task scheduling), Ports (domain models, interfaces), Infrastructure (LLM gateways, memory, state, prompts, skills) |
| `ganglia-coding`    | Coding tools: BashTools, FileEditTools, WebFetchTools                                                                                      |
| `ganglia-web`       | WebSocket server + JSON-RPC 2.0 endpoint (WebUIVerticle)                                                                                   |
| `ganglia-terminal`  | JLine 3 TUI with Flexmark Markdown rendering                                                                                               |
| `ganglia-webui`     | React 18 + TypeScript + Vite frontend (shadcn/ui, Zustand)                                                                                 |
| `ganglia-example`   | Demo apps (WebUIDemo, AutonomousAgentDemo, etc.)                                                                                           |
| `ganglia-swe-bench` | SWE-bench evaluation harness                                                                                                               |
| `integration-test`  | E2E tests with mock LLM responses via E2ETestHarness                                                                                       |

### Four Hexagonal Layers

1. **API / Adapter**: Entry points — `WebUIVerticle` (WebSockets), `TerminalUI` (console)
2. **Kernel**: `StandardAgentLoop` (ReAct), `SchedulableFactory` (maps tool calls to tasks), `AgentTaskFactory`
3. **Port**: Domain models (`Message`, `Turn`, `SessionContext`) + service interfaces (`ModelGateway`, `ToolSet`, `MemoryService`, `PromptEngine`, `SessionManager`)
4. **Infrastructure**: Implementations — `OpenAIModelGateway`, `AnthropicModelGateway`, `RetryingModelGateway`, `FileStateEngine`, `StandardPromptEngine`, tool implementations

### Package Structure (`work.ganglia.*`)

- `kernel.loop` — Agent reasoning loop
- `port.external` — `ModelGateway`, `ToolSet`
- `port.internal` — `MemoryService`, `SkillService`, `PromptEngine`, `SessionManager`
- `port.chat` — `Message`, `Turn`, `SessionContext`
- `infrastructure.external.llm` — LLM gateway implementations
- `infrastructure.external.tool` — Tool implementations
- `infrastructure.internal.{memory,prompt,skill,state}` — Internal service implementations
- `config` — Configuration management
- `api` — WebUI verticle

### Key Patterns

- **All async via `io.vertx.core.Future`** — never block; always compose with `.compose()`, `.map()`, etc.
- **Bootstrap**: `Ganglia.bootstrap(Vertx, BootstrapOptions)` wires everything and returns a `Ganglia` record
- **5-layer prompt assembly**: Kernel, Process, Capability, Rule, Context — managed by `StandardPromptEngine` + `ContextComposer`
- **Unified observation stream**: All events go through `ObservationDispatcher` on EventBus (`ganglia.observations.*`). Tools/gateways must NOT use `vertx.eventBus()` directly for observations.
- **Three-tier memory**: Turns (ephemeral) → Sessions (compressed via `ContextCompressor`) → Long-term (`MEMORY.md` + daily journals in `.ganglia/memory/`)
- **Native LLM protocols**: No third-party SDKs; OpenAI and Anthropic protocols implemented directly via Vert.x WebClient
- **Plugin architecture**: `ToolSetProvider`, `ContextSource`, `SkillLoader` (FileSystem/JAR)

### Runtime Directory (`.ganglia/`)

Auto-created at startup: `config.json`, `state/`, `memory/`, `skills/`, `logs/`, `trace/`

## Development Conventions

- Java 17 features: records for immutable data, text blocks for JSON schemas
- Interfaces for all service contracts
- Logger: `private static final Logger logger = LoggerFactory.getLogger(ClassName.class);`
- Testing: JUnit 5 + `@ExtendWith(VertxExtension.class)`, Mockito, Google Jimfs for filesystem isolation
- Integration tests use `E2ETestHarness` for deterministic mock-LLM scenarios
- LLM requests must enforce timeouts (default 60s); retries handled by `RetryingModelGateway`

