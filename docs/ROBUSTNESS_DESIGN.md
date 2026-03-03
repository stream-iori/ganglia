# Ganglia Robustness Design

> **Status:** Initial Design
> **Module:** `ganglia-core`
> **Related:** [Architecture](ARCHITECTURE.md), [Core Kernel](CORE_KERNEL_DESIGN.md)

## 1. Objective
To ensure Ganglia remains stable, responsive, and recoverable in the face of LLM unpredictability, network instability, and tool execution failures. Robustness is treated as a first-class citizen through structured feedback loops and defensive resource management.

## 2. LLM Call Robustness

### 2.1 Structured Exception Mapping
The `ModelGateway` abstracts provider-specific errors into a unified `LLMException` hierarchy:
- `RateLimitException`: Triggers exponential backoff.
- `ContextOverflowException`: Triggers aggressive history pruning or summarization.
- `AuthenticationException`: Critical failure, alerts the user.

### 2.2 Retry with Backoff
Core Reasoning Loops use Vert.x timers to implement jittered exponential backoff for transient errors (500, 502, 503, 504, 429).

### 2.3 Model Fallback
If the `primary` model fails consistently or hits quota limits, the system can automatically downgrade to the `utility` model defined in `ConfigManager` to attempt completion of simpler tasks.

## 3. Tool Execution Robustness

### 3.1 Observation Feedback (Self-Correction)
Instead of crashing on tool failure (e.g., `FileNotFound`, `PermissionDenied`), the `ToolExecutor` returns a structured error message. This message is fed back to the LLM as an **Observation**, allowing the agent to "reason" about the error and try a different approach.

### 3.2 Resource Guardrails
- **Execution Timeouts**: Every tool has a hard timeout (default 30s).
- **Output Size Limits**: Tool results are capped at 64KB to prevent context window bloat. For larger files, the system uses **Line-based Pagination**.
- **Strict Sandbox Enforcement**: All file-system tools use `PathSanitizer` to ensure operations are confined to the project root, preventing path traversal attacks.
- **Shell Injection Prevention**: Utility methods escape all shell arguments before script formatting.
- **Process Isolation**: Script-based skills run in isolated processes via `bash -c`.

### 3.3 Atomic Side-Effects
Destructive operations like `write_file` or `replace_in_file` follow the **Write-then-Move** pattern:
1. Write to `.tmp` file.
2. Verify integrity.
3. Perform atomic rename/move.

## 4. Input/Output Integrity

### 4.1 Tool Call Validation
Before executing any tool, the arguments are validated against the tool's JSON Schema.
- **Invalid arguments**: Intercepted and returned to the model as a "Schema Validation Error".
- **JSON Sanitization**: Robust parsing that handles common LLM formatting errors (e.g., trailing commas, unescaped newlines).

### 4.2 Context Window Management
- **Token Budgeting**: The `PromptEngine` calculates the budget for System Prompt, History, and Response.
- **Proactive Compression**: Implementation of a 70% threshold monitor in `StandardAgentLoop`. When history consumes >70% of the window, the `ContextCompressor` is triggered to replace older turns with concise summaries, ensuring continuity without overflow.
- **Financial Guardrail**: A hard session-level token limit (e.g., 500k tokens) is enforced to prevent runaway costs.
- **Tool Failure Circuit Breaker**: If a tool fails consecutively (3 times), the loop is aborted to prevent token waste on repetitive errors.

## 5. Orchestration & Coordination Robustness

### 5.1 DAG Failure Handling
In `SubAgentGraphTools`, nodes can define failure policies:
- `FAIL_FAST`: Abort the entire graph if a node fails.
- `FAIL_SILENT`: Log the error but allow independent branches to continue.
- `RETRY`: Re-run the node up to N times.

### 5.2 Concurrency Quota
To prevent local resource exhaustion or API rate-limiting, a global `Semaphore` limits the number of concurrent active sub-agents.

## 6. Recovery & Continuity

### 6.1 State Checkpointing
After every `Observation` is added to the context, the `SessionManager` persists the `SessionContext` to disk. 
- **Crash Recovery**: If the process terminates, the next run can load the session state and resume from the last successful step.

### 6.2 Graceful Shutdown
Ganglia listens for SIGTERM/SIGINT to:
1. Stop the current Reasoning Loop.
2. Persist final state.
3. Clean up child processes and temporary files.
