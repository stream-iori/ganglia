# Design & Plan: Sub-agent & Graph Capability Enhancement (ganglia-harness)

## 1. Vision: From Task-Completion to Engineering-Autonomy

The goal of this enhancement is to evolve `ganglia-harness` from a container that executes a single ReAct loop into a **Supervised Multi-Agent Orchestrator**.

In complex software engineering tasks (like SWE-bench), a single agent often gets lost in long-context or fails due to lack of specialized roles. By introducing a structured, parallel, and supervised Task Graph, we enable specialized personas (Researcher, Engineer, Verifier) to collaborate effectively.

---

## 2. Architectural Design & Rationale

### A. The "Simple DAG" Philosophy

**Decision:** Keep the Graph structure as a Directed Acyclic Graph (DAG) but make the **Supervisor** smart.
- **Rationale:** Complex branching and looping within the graph itself lead to "spaghetti graphs" that are hard to debug and persist. Instead, logic like "if failed, try again" or "if rejected, go back to implement" is handled by the `GraphSupervisor` controlling node states, rather than complex internal graph edges.

### B. Specialized Personas (Roles)

Standardizing roles allows for optimized prompts and tool-sets:
- **Architect:** Planning and graph decomposition.
- **Researcher:** Deep exploration, root cause analysis (RCA), and fact-finding.
- **Engineer:** Implementation, patch generation, and refactoring.
- **Verifier:** Test writing and validation.
- **Reviewer:** Code audit and quality gates.

### C. Shared Blackboard (Structured Memory)

**Decision:** Introduce a `Blackboard` (Map<String, Object>) within the `TaskGraph`.
- **Rationale:** Passing raw strings between nodes is inefficient and causes token bloat. The Blackboard stores **structured facts** (e.g., `fixed_file_paths: [...]`, `test_error_type: "NPE"`).
- **Injection:** A node only receives data from its **direct dependencies**, ensuring a clean and focused context.

### D. Persistence & State Recovery

**Decision:** JSON-based persistence in `.ganglia/sessions/{sessionId}/graph.json`.
- **Rationale:** Long-running tasks (hours of evaluation) must survive JVM crashes or API rate limits. The Supervisor can "re-hydrate" the graph state and resume exactly from the last `PENDING` node.

### E. Failure Policies (Orchestration Control)

**Decision:** Implement two distinct strategies for handling node failures:
1. **HALT_ON_ERROR (Safety First):** Stops all new node dispatches if any node fails. Best for risky or sequential operations.
2. **CONTINUE_ON_ERROR (Efficiency First - Default):** Continues executing independent parallel branches that do not depend on the failed node.

### F. Human-in-the-loop (HITL)

**Decision:** Explicit `HUMAN` nodes and implicit failure hooks.
- **Rationale:** For critical steps (e.g., merging code or running destructive tests), we need a way to pause and wait for user approval.

---

## 3. Detailed Data Models

### TaskNode

- `id`: Unique identifier.
- `type`: `AGENT` (ReAct), `HUMAN` (Wait for input), `SUMMARY` (Final report).
- `persona`: Role-specific System Prompt.
- `instruction`: Task description.
- `dependencies`: List of parent Node IDs.
- `status`: `PENDING`, `RUNNING`, `COMPLETED`, `FAILED`, `WAITING_FOR_HUMAN`, `ABORTED`.
- `result`: Raw output text.
- `outputData`: Extracted structured facts (synced to Blackboard).

### TaskGraph

- `sessionId`: Associated session.
- `failurePolicy`: `HALT_ON_ERROR` or `CONTINUE_ON_ERROR`.
- `nodes`: List of `TaskNode`.
- `blackboard`: Global shared state for structured data.

---

## 4. Implementation Roadmap (Task List)

### Phase 1: Core Models & Persistence

- [ ] Define `NodeType`, `NodeStatus`, and `FailurePolicy` enums.
- [ ] Implement `TaskNode` and `TaskGraph` POJOs.
- [ ] Implement `JacksonGraphStore` for state persistence to `graph.json`.
- [ ] Implement graph validation (cycle detection, dependency checking).

### Phase 2: Parallel Orchestration (GraphSupervisor)

- [ ] Refactor `DefaultGraphExecutor` into a stateful `GraphSupervisor`.
- [ ] Implement the `drive()` loop using Vert.x `Future` for parallel node dispatch.
- [ ] Implement `getReadyNodes()` logic with dependency resolution.
- [ ] Implement `FailurePolicy` enforcement.

### Phase 3: Context & Data Flow

- [ ] Implement `DependencyContextResolver` to filter and inject Blackboard data into prompts based on node dependencies.
- [ ] Add "Fact Extraction" step after `AGENT` nodes to populate `outputData`.

### Phase 4: Human-in-the-loop & Tooling

- [ ] Add `ask_selection` tool integration for pre-run policy confirmation.
- [ ] Implement `resumeNode` API for `HUMAN` nodes.

### Phase 5: Verification

- [ ] Create a "TDD Collaboration" test case.
- [ ] Verify parallel execution performance.
- [ ] Verify state recovery after simulated crash.

