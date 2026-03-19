# Ganglia SWE-bench Evaluation Module

`ganglia-swe-bench` is the official evaluation component of the Ganglia framework, specifically designed to test Agent engineering capabilities on the [SWE-bench](https://www.swebench.com/) (Software Engineering Benchmark) dataset. It provides an isolated Docker sandbox environment where Agents can attempt to fix real-world GitHub Issues and automatically verify the results.

This module leverages the **CodingAgent** capability, providing the evaluator with standard software engineering workflows (Research -> Strategy -> Execution) and optimized file-editing tools.

## 1. Core Features

*   **Automated Sandbox**: Integrates Docker via Testcontainers to dynamically create isolated Python/Git environments for each task.
*   **CodingAgent Integration**: Uses the standard `CodingAgentBuilder` to provide the agent with professional engineering persona, mandates, and tools.
*   **Trajectory Logging**: Implements `AgentLoopObserver` to record every reasoning step, tool call, and token usage into `target/e2e-logs/`.
*   **Precise Verification**: Uses the `FAIL_TO_PASS` test cases provided by the dataset. After the Agent completes a fix, official test patches are applied for final acceptance.

## 2. Prerequisites

### 2.1 Docker Infrastructure
The evaluation module relies on pre-built Docker images as the sandbox base. You MUST build the base images before running the evaluator:

```bash
# Build the Astropy base image (Required for the lite subset)
docker build -t astropy-base -f ganglia-swe-bench/docker/Dockerfile.astropy-base ganglia-swe-bench/docker
```

### 2.2 Dataset Status
The evaluation input (`swe_bench_lite_subset.jsonl`) should be located in `ganglia-swe-bench/target/`. 

If it's missing, you can re-download it using:
```bash
python3 ganglia-swe-bench/download_dataset.py
```

### 2.3 LLM Configuration
Ensure that a valid LLM API Key (OpenAI or Anthropic) is configured in your global `.ganglia/config.json`. The evaluator uses the `primary` model defined there.

## 3. Running the Evaluation

Execute the evaluator using the following Maven command:

```bash
mvn exec:java -Dexec.mainClass="work.ganglia.swebench.SWEBenchEvaluator" -pl ganglia-swe-bench
```

### Execution Behavior:
*   The evaluator loads tasks from `target/swe_bench_lite_subset.jsonl`.
*   For each task, it starts a fresh Docker container, clones the repository, and injects the issue description.
*   The **Coding Agent** then attempts to solve the issue using `run_shell_command`, `read_file`, and `replace_in_file` (all executed *inside* the container).
*   Logs are saved to `target/e2e-logs/[instance_id]_trajectory.json`.

## 4. Verification Workflow

1.  **Agent Execution**: The Agent explores the codebase, implements a fix, and provides a final summary.
2.  **Acceptance Phase**:
    *   **Apply Test Patch**: The evaluator applies the official `test_patch` from the dataset to the Agent's modified code.
    *   **Verification**: The evaluator runs the specific test cases defined in `FAIL_TO_PASS` inside the container.
3.  **Result Scoring**:
    *   **RESOLVED**: All target tests passed.
    *   **FAILED**: Any target test failed or the Agent timed out.

## 5. Directory Structure

*   `docker/`: Base Dockerfiles for various projects.
*   `src/main/java/.../swebench/`: 
    *   `SWEBenchEvaluator`: Orchestrates the evaluation loop.
    *   `SandboxManager`: Manages Docker container lifecycles.
    *   `TrajectoryLogger`: Captures E2E traces as a standard Ganglia Observer.
    *   `tools/`: Specialized toolsets (`DockerBashTools`, etc.) that redirect commands to the sandbox.
