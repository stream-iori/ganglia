# Ganglia SWE-bench Evaluation Module

`ganglia-swe-bench` is the official evaluation component of the Ganglia framework, specifically designed to test Agent engineering capabilities on the [SWE-bench](https://www.swebench.com/) (Software Engineering Benchmark) dataset. It provides an isolated Docker sandbox environment where Agents can attempt to fix real-world GitHub Issues and automatically verify the results.

## 1. Core Features

*   **Automated Test Environment**: Integrates Docker via Testcontainers to dynamically create isolated Python/Git environments for each task.
*   **End-to-End Evaluation**: Simulates the complete engineering workflow, from reading Issue descriptions to locating code, fixing bugs, and running tests.
*   **Trajectory Logging**: Records every reasoning step and tool call made by the Agent. Logs are generated in `target/e2e-logs/`.
*   **Precise Verification**: Uses the `FAIL_TO_PASS` test cases provided by the dataset. After the Agent completes a fix, official test patches are applied for final acceptance.

## 2. Prerequisites

Before running the evaluation, ensure your development environment meets the following requirements:

### 2.1 Docker Image
The evaluation module relies on pre-built Docker images as the sandbox base. Currently, it primarily supports `astropy` related tasks:
```bash
# Execute from the project root
docker build -t astropy-base -f ganglia-swe-bench/docker/Dockerfile.astropy-base ganglia-swe-bench/docker
```

### 2.2 Dataset Download
A subset of SWE-bench Lite needs to be downloaded as evaluation input.
```bash
# Recommendation: Install dependencies in a virtual environment
python3 -m venv venv
source venv/bin/activate
uv pip install datasets
python3 ganglia-swe-bench/download_dataset.py
```
This will generate the `swe_bench_lite_subset.jsonl` file under `ganglia-swe-bench/target/`.

### 2.3 Configuration
Ensure that a valid LLM API Key (OpenAI-compatible interface) is configured in `.ganglia/config.json`.

## 3. Running the Evaluation

Execute the evaluator main class using Maven:

```bash
mvn exec:java -Dexec.mainClass="work.ganglia.swebench.SWEBenchEvaluator" -pl ganglia-swe-bench
```

### Execution Parameters:
*   By default, it runs the first 5 tasks from `ganglia-swe-bench/target/swe_bench_lite_subset.jsonl`.
*   The evaluator will sequentially start containers, clone code, and run the Agent for each task.

## 4. Verification Workflow

The evaluator determines task success through the following steps:

1.  **Agent Execution Phase**: The Agent is granted permissions such as `run_shell_command`, `read_file`, and `replace_in_file` within the sandbox. It must autonomously decide how to modify the code.
2.  **Acceptance Phase (Validation)**:
    *   **Apply Test Patch**: The evaluator applies the `test_patch` (official verification tests) from the dataset to the Agent's modified code.
    *   **Targeted Testing**: Based on the `FAIL_TO_PASS` field in the task metadata, the evaluator runs specific test cases that were previously failing but are now expected to pass.
3.  **Determination Criteria**:
    *   **RESOLVED**: All tests in the `FAIL_TO_PASS` list run successfully (PASSED).
    *   **FAILED**: Any test fails, or the Agent fails to provide a final answer before reaching the maximum iteration limit.

## 5. Directory Structure

*   `docker/`: Contains base Dockerfiles for various languages/projects.
*   `src/main/java/.../swebench/`: 
    *   `SWEBenchEvaluator`: Main evaluation loop.
    *   `SandboxManager`: Lifecycle management for Docker containers.
    *   `tools/`: Specialized toolsets adapted for the Docker environment (DockerBashTools, etc.).
*   `download_dataset.py`: Script for dataset preparation.
