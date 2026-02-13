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
