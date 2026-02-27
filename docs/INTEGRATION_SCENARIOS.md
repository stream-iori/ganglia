# Ganglia Integration Test Scenarios

> **Goal**: Validate Agent's multi-tool collaboration, memory capabilities, and low-latency response in complex tasks.
> **Implementation**: These scenarios are verified using the **E2ETestHarness**, which simulates LLM responses to ensure deterministic and cost-effective testing.

## 1. Scenario 1: Knowledge Acquisition & Long-Term Memory (Web -> Memory)
**Description**: Agent accesses a specific URL to fetch technical specifications and records key points into long-term memory.
*   **Input**: "Fetch the content from http://localhost:8080/readme.md and remember the project conventions listed there."
*   **Expected Behavior**:
    1. Call `web_fetch` to get content.
    2. Extract the "Conventions" section.
    3. Call `remember` to write content to `MEMORY.md`.
*   **Verification**: `MEMORY_CONTAINS` expectation in `Scenario1KnowledgeAcquisitionE2EIT`.

## 2. Scenario 2: System Diagnosis & Issue Location (Shell -> Thought)
**Description**: Agent analyzes system status or searches for anomalies in the codebase via shell commands.
*   **Input**: "Find all files in the current directory that contain the word 'TODO' and list them."
*   **Expected Behavior**:
    1. Call `grep_search` or `run_shell_command` executing `grep -r "TODO" .`.
    2. Parse output and report to user in natural language.
*   **Verification**: `OUTPUT_CONTAINS` expectation in `Scenario2SystemDiagnosisE2EIT`.

## 3. Scenario 3: Multi-Skill Collaboration (Skill -> Web -> Shell)
**Description**: Activate a specific skill and use its tools to complete cross-domain tasks.
*   **Input**: "Activate the 'java-expert' skill, fetch the latest version of Vert.x from their site, and check if our pom.xml is up to date."
*   **Expected Behavior**:
    1. Call `activate_skill`.
    2. Call `web_fetch` to get version info.
    3. Call `read_file` to read `pom.xml`.
    4. Logical judgment and providing recommendations.
*   **Verification**: `OUTPUT_CONTAINS` expectation in `Scenario3MultiSkillCollaborationE2EIT`.

## 4. Scenario 4: User Interaction & Interrupt Recovery (Interrupt -> Resume)
**Description**: Request user choice before executing sensitive operations (like shell commands).
*   **Input**: "Search for log files and delete the largest one."
*   **Expected Behavior**:
    1. `list_directory` or `glob` to locate files.
    2. Call `ask_selection` to let user confirm which file to delete.
    3. After user selection, execute `run_shell_command` for cleanup.
*   **Verification**: `OUTPUT_CONTAINS` expectation in `Scenario4UserInteractionE2EIT` after resume.

## 5. Scenario 5: Codebase Discovery & Exploration (Discovery -> Search -> Read)
**Description**: Efficiently investigate the codebase using standard engineering tools.
*   **Input**: "Find all Markdown files in the 'docs' directory, search for the word 'Phase' in them, and read the plan."
*   **Expected Behavior**:
    1. Call `glob` matching `docs/*.md`.
    2. Call `grep_search` to find "Phase" in matched files.
    3. Call `read_file` to read `docs/plan.md`.
*   **Verification**: Agent can精准 locate content from discovery to precision and finally read the full text.
