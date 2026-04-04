# Design & Plan: Team-based Manager Orchestration (ganglia-harness)

## 1. Vision: The "Team of Managers" Architecture

The goal of this enhancement is to evolve `ganglia-harness` into a robust multi-agent coordination system. We prioritize **System Simplicity** and **Traceability** over complex runtime interruption. The system mimics a real-world engineering team where Managers collaborate in iterative loops, while their internal workflows remain predictable and linear.

---

## 2. Core Operational Laws

To ensure stability in long-running, complex tasks, all Managers and the Global Engine must adhere to these three laws:

### I. The Low-Entropy Law (History Management)
- **Automatic Compaction:** The Global Engine monitors Blackboard depth. When the loop count or the number of superseded facts exceeds a threshold (e.g., 10), the engine automatically inserts a **Summarizer** task.
- **Lessons Learned:** This task distills the history of failures into a single `Lessons Learned` entry and archives old facts. This keeps the Context Window focused and prevents "History Debt" from diluting the model's attention.

### II. The Isolation Law (Parallel Safety)
- **Git Worktree Isolation:** Every Manager running in parallel MUST operate in an isolated **Git Worktree**. This prevents physical file collisions and shared-state corruption.
- **The Merge Gate:** Upon completion, the Global Engine attempts to merge worktree changes. 
- **Conflict Redo:** If a physical merge conflict or a logical failure (verified by **RealityAnchor**) occurs, changes are rejected, and a new cycle is triggered to resolve the discrepancy.

### III. The Self-Healing Law (Environment Robustness)
- **Audit-First Principle:** Managers and Sub-agents do not assume a "pristine" environment. 
- **Pre-flight Cleanup:** The first step of any Manager's execution is an **Environment Audit** (e.g., `git status`, `ps aux`). If artifacts from previous failed attempts are detected, the Manager must clean them up before proceeding. This ensures eventual consistency in the physical workspace.

---

## 3. Architectural Components

### A. Global Layer: Cyclic Manager Graph
- **Orchestration:** A **Cyclic Directed Graph** where each node is a **Manager**.
- **High Observability:** Every iteration is an explicit transition. If a strategy fails, the system triggers a new cycle, ensuring a clean audit trail.
- **RealityAnchor (The Grounding Engine):** A specialized, non-LLM node acting as the final arbiter. It "measures" reality via tools/scripts (Tests, Lints, Audits). 
    - **Immutable Law:** Validation scripts are read-only to Agents. No Agent can modify the "Law" to pass a test.
    - **Raw Grounding:** Failed validations bypass the Summarizer; raw logs are injected into the next cycle's context to prevent interpretation bias.

### B. Local Layer: Linear Manager Workflow
- **Mission-Driven Execution:** Every Sub-agent receives a **Mission Context**—a high-level goal statement to ensure local actions align with global intent.
- **Incremental Redo & Task Fingerprinting:** 
    - Each `ToDoItem` generates an `execution_hash = hash(missionContext + input_files_checksum + previous_task_ids)`.
    - The `Task Cycle Interceptor` uses this hash to skip tasks whose inputs and context remain unchanged, even if the parent Manager was restarted.

### C. State Consistency & Checkpoints
- **No Interruption:** Tasks always run to completion. Failure or Invalidation is treated as a valid completion state.
- **Heartbeat Protocol:** Long-running tasks periodically call `check_mission_validity()`.
- **Graceful Exit:** If a referenced fact is marked `is_superseded`, the task performs a **Graceful Exit**, saving intermediate results but marking the status as `INVALIDATED`.
- **Source of Truth:** The **Blackboard** is the source for strategic decisions; the **ToDoList** is the tactical record of execution.

### D. Progressive Fact Management
- **Tiered Fact Storage:**
    - **Active Context (L1):** High-level summaries for the LLM context window.
    - **Cold Storage (L2):** Detailed raw logs and diffs stored on disk, linked via `FactID`.
- **Detail Fetching:** Managers use `fetch_fact_details(fact_id)` to selectively retrieve L2 data only when tactical deep-dives are required.

---

## 4. Data Models

### Manager
- `id`, `missionContext`, `mode` (SELF|DELEGATE).
- `worktreePath`: Path for isolated execution.
- `internalDag`: Supports **PARALLEL** execution nodes.

### RealityAnchor
- `validationSuites`: Automated checks (Tests, Lints, Audits).
- `groundingStrategy`: Logic for merging or rejecting changes based on validation.

### Blackboard Fact
```json
{ 
  "id": "fact_001",
  "summary": "...", 
  "detail_ref": "path/to/cold_storage.json", 
  "source_manager": "...", 
  "is_superseded": boolean, 
  "is_archive_summary": boolean 
}
```

---

## 5. Implementation Roadmap

### Phase 1: Manager & Isolation
- [ ] Implement `Manager` and `SubAgent` models with `missionContext`.
- [ ] Implement **Git Worktree** lifecycle management.
- [ ] Create `LinearDagRunner` with `PARALLEL` support.

### Phase 2: Global Engine & Interceptor
- [ ] Implement `ManagerGraphEngine` (Cyclic transitions).
- [ ] Implement `Task Cycle Interceptor` for Incremental Redo (Fingerprinting).
- [ ] Implement the **Entropy Monitor** for automatic `Summarizer` injection.

### Phase 3: Blackboard & RealityAnchor
- [ ] Implement `Blackboard` with `is_superseded` versioning and L1/L2 storage.
- [ ] Implement `RealityAnchor` (Grounding & Merge Gate).
- [ ] Add `check_mission_validity()` and `fetch_fact_details()` tools.

### Phase 4: Observability
- [ ] Update `trace.html` to visualize cycles, superseded facts, and Worktree merges.
- [ ] Verify incremental redo after system restarts.
