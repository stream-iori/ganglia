# Ganglia Development Plan

## Phase 1: Foundation & Persistence (Tier 1)
**Objective:** robust state management for the current session loop.

- [x] **State Serialization:**
    - [x] Design JSON schema for `SessionContext` (including `ToDoList` and `Turn` history).
    - [x] Implement `FileStateEngine`: Save/Load `SessionContext` to `.ganglia/state/session_<id>.json`.
    - [x] Ensure atomic writes to prevent corruption.
- [x] **Log Management:**
    - [x] Implement `LogManager`: Append raw turn interactions (Thought, Tool, Output) to `.ganglia/logs/<date>.md`.
    - [x] Integrate logging into `ReActAgentLoop` (via `StateEngine` or observer).

## Phase 2: Medium-Term Context & Compression (Tier 2)
**Objective:** Manage token window limits via smart summarization linked to tasks.

- [x] **Context Pruning Engine:**
    - [x] Implement `TokenCounter` (using JTokkit or similar) to monitor context usage.
    - [x] Implement `SlidingWindow` logic to identify when context exceeds limits.
- [x] **Compression Logic:**
    - [x] Develop `ContextCompressor`: Logic to take a list of `Turn` objects and ask the LLM to summarize them.
    - [x] Implement "Task-Turn" Lifecycle Hook:
        - [x] Detect when `todo_complete` is called.
        - [x] Trigger summarization of all Turns associated with that Task.
        - [x] Replace raw Turns in `SessionContext` with a summary note in `ToDoList`.

## Phase 3: Long-Term Knowledge (Tier 3)
**Objective:** Project-wide memory that persists across sessions.

- [x] **Knowledge Base Manager:**
    - [x] Implement `KnowledgeBase`: Read/Write `MEMORY.md`.
    - [x] Define standard sections for `MEMORY.md` (e.g., "User Preferences", "Project Conventions", "Architecture").
- [x] **Retrieval System:**
    - [x] Integrate `grep` / `read` capabilities specifically for `MEMORY.md` into the system prompt construction.
    - [x] **Agentic Search:** Verify the agent can autonomously decide to read `MEMORY.md` when lacking context.

## Phase 4: Integration & Tools
**Objective:** Expose memory capabilities to the agent.

- [x] **`remember` Tool:**
    - [x] Implement a tool for the agent to explicitly write facts to `MEMORY.md`.
- [x] **Context Injection:**
    - [x] Update `PromptEngine` to dynamically inject:
        - [x] The `ToDoList` (always).
        - [x] The compressed "Accomplishments" (from Tier 2).
        - [x] Relevant snippets from `MEMORY.md` (if explicitly retrieved or small enough).
- [x] **Verification:**
    - [x] Create Integration Test: "Long Context Session" where the agent performs multiple tasks, triggers compression, and successfully recalls early context via summary.

## Phase 5: Advanced Interaction (Interrupts)
**Objective:** Enable the agent to ask for help or selection from the user.

- [x] **Interrupt Mechanism:**
    - [x] Update `ToolDefinition` to include `isInterrupt`.
    - [x] Update `ReActAgentLoop` to pause when an interrupt tool is called.
- [x] **`ask_selection` Tool:**
    - [x] Implement `SelectionTools` with `ask_selection(question, options)`.
    - [x] Ensure options are presented clearly (e.g., indexed list).
- [x] **Resume Mechanism:**
    - [x] Implement method to resume session with user selection (injected as Tool Result).

## Phase 6: Skill System
**Objective:** Enable domain-specific expertise through modular packages.

- [x] **Skill Foundation:**
    - [x] Define `SkillPackage` and `SkillManifest` models.
    - [x] Implement `SkillRegistry` for local and classpath discovery.
- [x] **Lifecycle & Integration:**
    - [x] Implement `SkillManager` to track `activeSkillIds` in `SessionContext`.
    - [x] Develop `SkillPromptInjector` to aggregate and inject prompts from active skills into `PromptEngine`.
    - [x] Update `ToolExecutor` to support dynamic registration of tools from active skills.
- [x] **Agentic Interaction:**
    - [x] Implement `list_available_skills` tool.
    - [x] Implement `activate_skill` tool.
    - [x] Add automatic skill suggestion logic based on file pattern triggers.
- [x] **Verification:**
    - [x] Create a "Java Expert" demo skill and verify the agent can activate it and use its specialized prompts/tools.

## Phase 7: Low-Latency Streaming
**Objective:** Improve UX by providing real-time feedback during reasoning.

- [x] **Kernel Refactoring:**
    - [x] Update `ReActAgentLoop` to use `ModelGateway.chatStream`.
    - [x] Implement a standard EventBus addressing scheme for session-specific streams (`ganglia.stream.<sessionId>`).
- [x] **UI Integration:**
    - [x] Implement a listener in the main entry point (or UI layer) to pipe EventBus tokens to `stdout`.
- [x] **Tool Call Handling:**
    - [x] Ensure tool calls are still correctly accumulated and executed sequentially after the stream completes.
- [x] **Verification:**
    - [x] Verify that the agent's "Thought" process is visible to the user as it is being generated.

## Phase 8: Network & System Tools
**Objective:** Expand the agent's actuation capabilities with web access and generic shell execution.

- [x] **Web Capability:**
    - [x] Implement `WebFetchTools` using Vert.x `WebClient`.
    - [x] Tool: `web_fetch(url)` - Fetch content from a URL.
- [x] **General Shell Capability:**
    - [x] Implement `BashTools` for generic command execution.
    - [x] Tool: `run_shell_command(command)` - Execute arbitrary bash commands with timeout and safety guards.
- [x] **Integration:**
    - [x] Update `ToolsFactory` and `DefaultToolExecutor` to include new tools.
- [x] **Verification:**
    - [x] Write unit tests for both toolsets.
    - [x] Create an integration test where the agent fetches a page and runs a shell command based on the result.

## Phase 10: Core Guidelines System
**Objective:** Enable project-level behavioral steering via a `GANGLIA.md` file.

- [x] **Guideline Loading Logic:**
    - [x] Implement a `GuidelineLoader` (or update `PromptEngine`) to read `GANGLIA.md` from the project root.
    - [x] Implement fallback to default hardcoded guidelines.
- [x] **Prompt Integration:**
    - [x] Refactor `StandardPromptEngine` to replace the hardcoded "Guidelines" section with loaded content.
- [x] **Initialization:**
    - [x] Add logic to create a default `GANGLIA.md` if one doesn't exist during project initialization.
- [x] **Verification:**
    - [x] Verify that changing `GANGLIA.md` updates the agent's behavior in the next turn.

## Phase 11: Session & Turn Management
**Objective:** Provide a structured way to manage sessions and turns via `SessionManager`.

- [x] **Core Session API:**
    - [x] Create `SessionManager` interface.
    - [x] Implement `DefaultSessionManager` using `StateEngine`.
- [x] **Turn Management Enhancements:**
    - [x] Refactor `SessionContext` and `Turn` to better support lifecycle transitions (Start, Update, Complete).
    - [x] Update `ReActAgentLoop` to use `SessionManager` for state persistence.
- [x] **Refactoring & Example:**
    - [x] Move CLI session logic from `me.stream.Main` to `me.stream.example.GangliaExample`.
    - [x] Update `Main` to focus on core framework initialization and provide a clean entry point.

## Phase 12: Integration Scenarios & E2E Verification
**Objective:** Validate full agent logic through complex, multi-tool scenarios.

- [x] **Scenario Documentation:**
    - [x] Create `docs/INTEGRATION_SCENARIOS.md`.
- [x] **E2E Test Suite:**
    - [x] Implement `FullWorkflowIT` covering Web -> Shell -> Memory.
    - [x] Implement tests for Interrupt -> Resume in a real loop.
- [x] **Test Infrastructure Refactoring:**
    - [x] Move integration tests to `src/integration-test/java`.
    - [x] Configure `maven-failsafe-plugin` for separate execution of IT tests.
- [x] **Tool Refinement (TDD):**
    - [x] Enhance `WebFetchTools` with status code handling.
    - [x] Enhance `BashTools` with complex pipe command support.
- [x] **Final Check:**
    - [x] Verify all scenarios pass using real model calls (if configured) or high-fidelity mocks.

## Phase 15: Skill System Redesign (Script-based & File-driven)
**Objective:** Transition to a standardized, file-based skill system where tools are defined as scripts in `SKILL.md`, following the Gemini CLI standard.

- [x] **Core Model & Parsing:**
    - [x] Update `SkillManifest` to support the `tools` YAML array in frontmatter (name, command, schema).
    - [x] Enhance `MarkdownContextResolver` or `SkillManifest` to handle variable substitution logic (e.g., `${skillDir}`).
- [x] **Script Tool Execution Engine:**
    - [x] Implement `ScriptToolSet`: A dynamic toolset that spawns external processes based on `SKILL.md` templates.
    - [x] Implement parameter injection and sanitization for script commands.
- [x] **Lazy Loading & Dynamic Registration:**
    - [x] Refactor `SkillService` to support metadata-only discovery and lazy-loading of full instructions.
    - [x] Update `DefaultToolExecutor` to dynamically register script tools upon skill activation.
- [x] **Scope & Discovery:**
    - [x] Update `FileSystemSkillLoader` to scan both project-local `.ganglia/skills/` and user-global `~/.ganglia/skills/`.
- [x] **Verification:**
    - [x] Port `git-smart-commit` to the new script-based format.
    - [x] Create `SkillSystemIT` to verify the full flow: Discovery -> Activation (Interrupt) -> Script Tool Execution -> Observation.

## Phase 13: Systemic Context Engine (GEMINI.md Mechanism)
**Objective:** Decouple prompt construction from code using file-driven context.

- [x] **Core Architecture:**
    - [x] Define `ContextSource` and `ContextFragment` models.
    - [x] Implement `MarkdownContextResolver` to parse files by headers.
- [x] **Dynamic Injection:**
    - [x] Implement `EnvironmentSource` to inject OS and directory structure info.
    - [x] Add support for variable replacement in Markdown files.
- [x] **Orchestration:**
    - [x] Create `ContextComposer` with priority-based pruning.
    - [x] Refactor `StandardPromptEngine` to use the new Engine.
- [x] **Verification:**
    - [x] Test agent adaptability by modifying `GANGLIA.md` at runtime.

## Phase 14: Configuration & Hot Reloading
**Objective:** Decouple model and system parameters from code using a JSON config file with dynamic reloading.

- [x] **Config Schema & Loading:**
    - [x] Design JSON schema for `ganglia-config.json` (model name, temperature, max tokens, utility model, etc.).
    - [x] Implement `ConfigManager` to load and parse the configuration using Vert.x.
- [x] **Integration:**
    - [x] Update `DefaultSessionManager` and `OpenAIModelGateway` to use the values from `ConfigManager` instead of hardcoded defaults.
    - [x] Ensure `ModelOptions` can be initialized from the config.
- [x] **Hot Reloading Mechanism:**
    - [x] Implement a file watcher to monitor `ganglia-config.json`.
    - [x] Update the internal configuration state in real-time when the file is modified.
- [x] **Verification:**
    - [x] Add unit tests for configuration parsing and default fallback.
    - [x] Verify that changing the model name in the JSON file affects the next agent turn without a restart.

## Phase 16: Standard Engineering Tools
**Objective:** Enhance the agent's ability to discover and modify the codebase using high-performance, structured tools.

- [x] **Filesystem Write Capability:**
    - [x] Implement `write_file` tool: Support creating or overwriting files with content.
    - [x] Integrate with `VertxFileSystemTools` for non-blocking I/O.
- [x] **Advanced Search & Discovery:**
    - [x] Implement `grep_search` tool: Provide recursive, regex-based text searching across the project.
    - [x] Implement `glob` tool: Support finding files matching patterns (e.g., `**/*.java`, `docs/*.md`).
- [x] **Integration & Refinement:**
    - [x] Update `ToolsFactory` and `DefaultToolExecutor` to include the new toolset.
    - [x] Ensure proper error handling for large search results or restricted permissions.
- [x] **Verification:**
    - [x] Write unit tests for search and write logic.
    - [x] Create an E2E scenario: "Code Exploration", where the agent uses `glob` to locate files and `grep_search` to find specific implementations.

## Phase 17: Enhanced User Interaction
**Objective:** Complete the `ask_selection` capability to support both structured selection and free-form feedback, ensuring robust recovery in error scenarios.

- [x] **Unified Interaction Tool:**
    - [x] Implement `ask_selection` tool: Support `text` input and `choice` (selection).
    - [x] Ensure non-blocking stdin handling in `InteractiveDemo` using EventBus.
- [x] **Loop Integration:**
    - [x] Refine `ReActAgentLoop` resume logic to handle multi-turn interaction naturally.
    - [x] Fix message duplication in turn completion.
- [x] **Error Recovery Enhancement:**
    - [x] Update `ErrorHandlingReActDemo` to use `ask_selection` when ambiguous situations arise.
- [x] **Verification:**
    - [x] Write unit tests for `ask_selection` with various input schemas.
    - [x] Create E2E demo for "Interactive Troubleshooting".
    
    ## Phase 18: Advanced Observability & Traceability
    **Objective:** Enable deep introspection of the agent's reasoning and tool execution steps via structured events and persistent trace logs.
    
    - [x] **Structured Event Model:**
        - [x] Define `ObservationEvent` and `ObservationType` (e.g., `THOUGHT`, `TOOL_CALL`, `TOOL_RESULT`, `ERROR`).
        - [x] Implement a unified EventBus addressing scheme for observations: `ganglia.observations.<sessionId>`.
    - [x] **Observability Configuration:**
        - [x] Add `ganglia.observability.enabled` and `ganglia.observability.trace_path` to `ConfigManager`.
        - [x] Support hot-reloading for the observability toggle.
    - [x] **Trace Persistence (TraceManager):**
        - [x] Implement `TraceManager` to subscribe to observation events.
        - [x] Implement date-based file rotation (e.g., `trace-2026-02-14.md`).
        - [x] Format events into structured Markdown for high readability.
    - [x] **Loop Instrumentation:**
        - [x] Update `ReActAgentLoop` to emit `ObservationEvent` at every key lifecycle stage (Reasoning, Acting, Iterating).
    - [x] **UI Layer Enhancement:**
        - [x] Update `TerminalUI` to consume `ObservationEvent` instead of raw strings.
        - [x] Implement specialized rendering for Thoughts (italics/subtle color) and Tool Calls (boxes/icons).
    - [x] **Verification:**
            - [x] Verify `trace.md` generation across multiple days.
            - [x] Ensure low performance overhead when observability is disabled.
        
        ## Phase 19: Anthropic Claude Integration
        **Objective:** Support Anthropic Claude models via a dedicated `ModelGateway` implementation, providing an alternative to OpenAI.
        
        - [x] **Anthropic Model Gateway:**
            - [x] Implement `AnthropicModelGateway` using the `anthropic-java` SDK.
            - [x] Map Ganglia's `Message` and `Role` models to Anthropic's `MessageParam` and `ContentBlock`.
            - [x] Implement specific handling for `SYSTEM` messages (Anthropic requires a separate top-level `system` parameter).
        - [x] **Streaming & Observations:**
            - [x] Implement `chatStream` using Anthropic's `createStreaming` API.
            - [x] Ensure `ObservationEvent` (especially `TOKEN_RECEIVED`) is correctly published during the stream.
        - [x] **Structured Error Mapping:**
            - [x] Map `AnthropicServiceException` and its subclasses to the structured `LlmException`.
            - [x] Extract `status_code`, `error.type`, and `request_id` from Anthropic responses.
        - [x] **Multi-Provider Configuration:**
            - [x] Update `ConfigManager` to support a `provider` setting (`openai` | `anthropic`).
            - [x] Add support for `ANTHROPIC_API_KEY` and `ANTHROPIC_BASE_URL` environment variables.
            - [x] Update `Main.bootstrap` to dynamically instantiate the correct `ModelGateway` based on configuration.
        - [x] **Verification:**
            - [x] Implement `AnthropicModelGatewayTest` using mocks.
            - [x] Create a "Claude Demo" to verify tool execution and reasoning with `claude-3-5-sonnet`.

        ## Phase 20: Google Gemini Integration
        **Objective:** Support Google Gemini models via a dedicated `ModelGateway` implementation using the Google GenAI SDK.

        - [x] **Gemini Model Gateway:**
            - [x] Implement `GeminiModelGateway` using the `google-genai` SDK.
            - [x] Map Ganglia's `Message`, `Role`, and `ToolCall` models to Gemini's `Content`, `Part`, and `FunctionCall`.
            - [x] Ensure proper handling of `system_instruction` in Gemini.
        - [x] **Streaming & Observations:**
            - [x] Implement `chatStream` using Gemini's streaming generation API.
            - [x] Publish `ObservationEvent` tokens to the EventBus during streaming.
        - [x] **Multi-Provider Configuration:**
            - [x] Add `gemini` as a valid provider in `ConfigManager`.
            - [x] Support `GOOGLE_API_KEY` environment variable.
            - [x] Update `Main.bootstrap` to support the Gemini provider.
        - [x] **Verification:**
            - [ ] Implement `GeminiModelGatewayTest` (Deferred due to complex SDK mocking).
            - [x] Create a "Gemini Demo" to verify full-cycle reasoning and tool use with `gemini-2.0-flash`.

## Phase 21: Daily Journal Memory System
**Objective:** Capture and persist cross-session daily accomplishments to improve project-wide continuity.

- [x] **Daily Record Manager:**
    - [x] Implement `DailyRecordManager`: Handle append-only logic for `.ganglia/memory/daily-*.md`.
    - [x] Integrate with Vert.x FileSystem for atomic updates.
- [x] **Reflection Logic:**
    - [x] Enhance `ContextCompressor` with a `reflect` method to generate concise bullet points from a Turn.
- [ ] **Context Integration:**
    - [x] Implement `DailyContextSource` for the `ContextEngine`.
    - [x] Inject the current day's journal into the system prompt at Priority 9.
- [ ] **Verification:**
    - [ ] Create an E2E test where an agent in Session A performs an action, and an agent in Session B (started later) correctly identifies that action via the Daily Record.

## Phase 22: Memory & Usage Logic Decoupling
**Objective:** Decouple memory reflection and token usage tracking from the ReAct loop using EventBus.

- [x] **Event Infrastructure:**
    - [x] Define EventBus addresses for Memory Reflection and Token Usage recording.
- [x] **Memory Service:**
    - [x] Implement `MemoryService` to handle `ganglia.memory.reflect` events.
    - [x] Move reflection and daily recording logic from `ReActAgentLoop` to `MemoryService`.
- [x] **Usage Manager:**
    - [x] Implement `TokenUsageManager` (or similar) to handle `ganglia.usage.record` events.
- [x] **ReAct Loop Refactoring:**
    - [x] Remove direct dependencies on `ContextCompressor` and `DailyRecordManager` from `ReActAgentLoop`.
    - [x] Publish EventBus messages for memory reflection and token usage.
- [x] **Bootstrap Update:**
    - [x] Register `MemoryService` and `TokenUsageManager` in `Main.bootstrap`.
- [x] **Verification:**
    - [x] Verify that Daily Records are still generated correctly.
    - [x] Verify that Token Usage is recorded/logged as expected.

## Phase 23: PromptEngine Refactoring
**Objective:** Centralize LLM request preparation, history pruning, and model options management in PromptEngine.

- [x] **Core Models:**
    - [x] Define `LlmRequest` record to encapsulate messages, tools, and options.
- [x] **PromptEngine Interface Update:**
    - [x] Add `prepareRequest` and `pruneHistory` methods to the `PromptEngine` interface.
- [x] **StandardPromptEngine Implementation:**
    - [x] Implement `pruneHistory` logic (moved from `ReActAgentLoop`).
    - [x] Implement `prepareRequest` to orchestrate system prompt construction, history pruning, tool resolution, and model options fallback.
- [x] **ReActAgentLoop Refactoring:**
    - [x] Simplify `reason` method to use `promptEngine.prepareRequest`.
    - [x] Remove `pruneHistory` and `ModelOptions` fallback logic from `ReActAgentLoop`.
- [x] **Verification:**
    - [x] Update `ReActAgentLoopTest` and `StandardPromptEngineTest` to reflect the new interface and behavior.
    - [x] Ensure full ReAct cycle still works as expected.

## Phase 24: Prompt Construction Refinement (Tools & Skills)
**Objective:** Consolidate all tool and skill-related prompt construction within PromptEngine using ContextSource.

- [x] **ToolContextSource:**
    - [x] Implement `ToolContextSource` to inject tool-specific guidelines and best practices.
- [x] **StandardPromptEngine Update:**
    - [x] Register `ToolContextSource` to dynamically enhance system prompt based on available tools.
- [x] **Skill Integration:**
    - [x] Ensure `SkillContextSource` is properly integrated into the unified prompt construction flow.
- [x] **Verification:**
    - [x] Verify that the system prompt now includes clear instructions for tool usage.

## Phase 25: Surgical File Replacement Tool
**Objective:** Implement a robust, surgical file editing tool (`replace_in_file`) to allow the agent to make precise modifications without overwriting entire files.

- [x] **Core Engine (`FileEditor`):**
    - [x] Implement non-blocking literal string replacement logic using Vert.x FileSystem.
    - [x] Implement atomic write pattern (write to `.tmp` then `move`).
    - [x] Implement strict occurrence counting and validation.
- [x] **Tool Encapsulation (`FileEditTools`):**
    - [x] Create `FileEditTools` to wrap the engine for the ReAct loop.
    - [x] Define JSON Schema for `replace_in_file` requiring exact matching with context.
    - [x] Implement error handling for `MATCH_FAILURE` and `AMBIGUITY_FAILURE`.
- [x] **Integration:**
    - [x] Register the new tool in `ToolsFactory`.
    - [ ] Update System Prompt via `ToolContextSource` to prefer `replace_in_file` over `write_file` for modifications.
- [x] **Verification:**
    - [x] Write unit tests for `FileEditTools` covering various edge cases (special characters, line endings).
    - [x] Create an integration test: "Surgical Refactoring", where the agent reads a file and performs a targeted replacement.

## Phase 26: Graceful Shutdown & Demo Refactoring
**Objective:** Implement a unified graceful shutdown mechanism for all demo applications to handle asynchronous background tasks properly.

- [x] **Shared Utility (`DemoUtil`):**
    - [x] Implement `gracefulShutdown(vertx)` with timer-based delay and clean process exit.
- [x] **Demo Refactoring:**
    - [x] Update all example demos (`BaseDemo`, `AutonomousReActDemo`, `ClaudeDemo`, `ComplexWorkflowDemo`, `ErrorHandlingReActDemo`, `GeminiDemo`, `InteractiveDemo`) to use `DemoUtil`.
- [x] **Kernel Support:**
    - [x] Overload `Main.bootstrap` to support injecting custom `ModelGateway` for mock-free integration testing.
- [x] **Verification:**
    - [x] Verify `AutonomousReActDemo` runs without `RejectedExecutionException` noise.

## Phase 27: Sub-Agent Graph Orchestration System
**Objective:** Enable the primary Orchestrator Agent to delegate tasks as a Directed Acyclic Graph (DAG), supporting both parallel and sequential execution of focused, short-lived Sub-Agents.

- [x] **Core Models & Tooling:**
    - [x] Implement `TaskNode` and `TaskEdge` models for DAG representation.
    - [x] Create `SubAgentGraphTools` with the `propose_task_graph` tool.
    - [x] Implement `isInterrupt=true` for the `propose_task_graph` tool to ensure human-in-the-loop approval.
- [x] **Graph Executor Implementation:**
    - [x] Implement `GraphExecutor`: Handle topological sorting and concurrent execution using Vert.x `Future.all()`.
    - [x] Create `ContextScoper` to isolate and prune history before passing it to child agents.
- [x] **Specialized Personas & Reporting:**
    - [x] Define "Investigator" and "Refactorer" persona templates with restricted toolsets.
    - [x] Implement logic to return a structured "Summary Report" from each child node and a final aggregated report for the parent.
- [x] **Recursion & Parallelism Control:**
    - [x] Implement hard limits on nesting depth (max 1 level) and concurrency (implicitly handled by non-blocking Vert.x futures, but recursion is limited).
- [x] **Verification:**
    - [x] Create an integration test where a Parent Agent proposes a graph with two parallel "Investigator" tasks followed by a sequential "Synthesis" task.
    - [x] Verify that the CLI correctly interrupts for user approval and that the graph executes as intended.

## Phase 28: Unit Test Refactoring & De-Mocking
**Objective:** Improve test reliability and maintainability by replacing heavy Mockito usage with high-fidelity Fakes and Stubs.

- [x] **Test Infrastructure (Stubs & Fakes):**
    - [x] Implement `InMemoryStateEngine` and `InMemoryLogManager` for state persistence testing.
    - [x] Implement `StubModelGateway` with response queuing and streaming support.
    - [x] Implement `StubToolExecutor` for controlled tool interaction testing.
- [x] **Core Logic Refactoring:**
    - [x] Refactor `ReActAgentLoopTest` to use Fakes and Stubs, focusing on state validation over behavior verification.
    - [x] Refactor `SessionManagerTest` to use `InMemoryStateEngine`.
    - [x] Refactor `MemoryServiceTest` to use real EventBus communication and `StubModelGateway`.
- [x] **Standardization:**
    - [x] Align test package structure with source code.
    - [x] Reduce `MockitoExtension` usage in favor of real object composition where feasible (e.g., `ConfigManager`, `TokenCounter`).
- [x] **Verification:**
    - [x] Ensure all refactored tests pass with `mvn test`.
    - [x] Verify that test execution time and readability are improved.

        
