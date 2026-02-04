# Ganglia Requirements Documentation

> **Status:** Draft / Requirements Definition
> **Version:** 0.1.0

## 1. Functional Requirements (Functionality)

### 1.1 Core Agent Loop (ReAct)
#### 1.1.1 Normal Flow
*   **Input Processing:** The system shall accept natural language input from the user.
*   **Reasoning (Thought):** The agent shall generate a "Thought" explaining its reasoning before taking action.
*   **Tool Selection:** The agent shall select the appropriate tool from the available toolset based on the user's request.
*   **Execution:** The system shall execute the selected tool and capture its output (Observation).
*   **Iteration:** The ReAct loop shall continue (Thought -> Tool -> Observation) until a final answer is derived or a termination condition is met.
*   **Response:** The system shall provide a final natural language response to the user.

#### 1.1.2 Exception Handling
*   **Structured Tool Failures:** The system shall capture structured tool execution errors via `ToolErrorResult` (e.g., Timeout, Size Limit Exceeded, Command Failed) and feed the detailed context back to the agent for self-correction.
*   **Memory Protection:** The system shall enforce a maximum output size (e.g., 16MB) for all tool executions to prevent memory exhaustion.
*   **Loop Limits:** The system shall enforce a maximum number of iterations (e.g., 20 steps) to prevent infinite loops.

### 1.2 Tooling System ("The Hands")
*   **Built-in Tools (Standard Library):**
    *   **Local Filesystem (Bash-based):** Optimized system commands (e.g., `ls`, `cat`) with mandatory timeout and output size limits.
    *   **Local Filesystem (Vert.x-based):** Native non-blocking Java operations for higher stability in concurrent environments.
    *   **HTTP/Network (Curl-based):** Robust networking implemented via `curl`.
    *   **ToDo & Plan Management:** Agent-managed internal task list persisted in session context.
*   **Extension Tools (User-defined):**
    *   **Custom Implementation:** The framework shall allow users to define domain-specific tools in Java.
    *   **Discovery:** Extension tools shall be registered via classpath scanning (annotations) or external JAR loading.
*   **Tool Definition & Schema:**
    *   **Annotations:** Tools shall be defined using `@AgentTool` annotations on Java methods.
    *   **Auto-Schema:** The system shall automatically generate OpenAI-compatible JSON schemas from method signatures.
*   **Sandboxing & Safety:**
    *   **Scope Restriction:** Bash-based tools shall be confined to the project root or specified safe directories.
    *   **Interrupts:** Tools marked as `@Sensitive` must pause for user confirmation.

### 1.3 Memory & Context ("The Brain")
*   **Ephemeral Memory:** The system shall log all interactions (thoughts, tool calls, results) to a daily markdown file (`.ganglia/logs/YYYY-MM-DD.md`).
*   **Curated Memory:** The system shall support a `MEMORY.md` file for long-term user preferences and project context.
*   **Active Retrieval:** The agent shall be able to search its own memory files (`grep` logs or `MEMORY.md`) to recall past information.
*   **Context Management:** The system shall intelligently prune the context window to stay within token limits while preserving the most relevant information.

### 1.4 Human-in-the-Loop & Interaction
*   **Planning Phase:**
    *   **Proposal:** For complex requests, the system shall generate a structured execution plan (JSON list of steps) *before* execution.
    *   **Approval:** The system shall require user approval or modification of the plan before proceeding.
*   **Runtime Interrupts:**
    *   **Sensitive Actions:** Execution of `@Sensitive` tools (e.g., `delete`, `deploy`) must trigger a blocking user confirmation prompt.
    *   **Clarification:** The agent shall use an `ask_user` tool to request missing information during execution.
    *   **User Selection:** The agent shall use `ask_selection` to present a list of options (e.g., disambiguating file names, choosing a design pattern) and await a specific choice.
    *   **Pause/Resume:** The system shall support pausing the ReAct loop to await user input.

### 1.5 Skill System ("The Expertise")
*   **Definition:** A Skill shall be a packageable unit containing:
    *   **Domain Knowledge:** specialized system prompts, documentation, or heuristics (e.g., "How to write idiomatic Go code").
    *   **Specialized Tools:** Java-based tools specific to that domain (e.g., `go_fmt`, `aws_deploy`).
*   **Lifecycle Management:** The system shall support installing, activating, and deactivating skills dynamically.
*   **Context Injection:** When a skill is active, its relevant prompts and "best practices" shall be injected into the agent's context.
*   **Discovery:** The agent shall be able to discover available skills and recommend their activation based on the user's task.

## 2. Non-Functional Requirements (Quality Attributes)

### 2.1 Reliability & Stability
*   **Crash Recovery:** The system shall serialize session state to disk (`.ganglia/state/`) after every step, allowing recovery from crashes without data loss.
*   **Rate Limiting:** The system shall handle API rate limits (e.g., OpenAI 429 errors) with exponential backoff.
*   **Error Isolation:** A failure in a tool execution should not crash the main agent loop.

### 2.2 Usability & Experience
*   **Latency:** The system shall support streaming responses (tokens) to the UI to minimize perceived latency.
*   **Transparency:** All agent actions ("Thoughts", Tool Calls) must be visible to the user in real-time.
*   **Editability:** Users must be able to manually edit the generated `MEMORY.md` and log files to correct the agent's understanding.

### 2.3 Observability & Debugging
*   **Tracing:** The system shall emit OpenTelemetry traces for every ReAct step and tool execution.
*   **Logging:** Detailed logs of raw prompts and LLM completions shall be available (configurable) for debugging.
*   **Audit Trail:** The markdown log files shall serve as a human-readable audit trail of all agent actions.

### 2.4 Security
*   **API Key Management:** API keys must be loaded from environment variables or secure storage, never logged or committed to git.
*   **Prompt Injection:** The system shall include basic safeguards (system prompt instructions) against prompt injection attacks.
*   **Sandbox Isolation:** The execution sandbox must prevent access to the host system beyond the allowed working directory.

### 2.5 Extensibility
*   **Plugin Architecture:** Third-party developers shall be able to add new tools by providing a JAR with annotated classes.
*   **Model Agnostic:** The core logic shall be decoupled from specific LLM providers via the `ModelProvider` interface.
