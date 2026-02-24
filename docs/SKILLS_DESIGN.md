# Ganglia Skill System Redesign (Script-based & File-driven)

> **Status:** Initial Design
> **Module:** `ganglia-skills`
> **Related:** [Architecture](ARCHITECTURE.md), [Core Kernel](CORE_KERNEL_DESIGN.md)

## 1. Objective
To align Ganglia's skill system with the open standard established by Gemini CLI. The system moves away from Java-based tool definitions for skills, embracing a file-driven approach where skills are defined by a `SKILL.md` file and tools are executed as external scripts (Python, Node.js, Bash, etc.).

## 2. Skill Structure
A skill is a directory containing a `SKILL.md` file.

### 2.1 Directory Layout
```text
my-skill/
├── SKILL.md          # Mandatory: Metadata, Instructions, and Tool Definitions
├── scripts/          # Optional: Executable scripts (python, sh, js)
└── assets/           # Optional: Static resources
```

### 2.2 The `SKILL.md` Format
The file uses YAML frontmatter for configuration and Markdown for instructions.

```markdown
---
id: py-linter
name: Python Linter
description: Provides deep linting for Python files using Ruff.
activationTriggers:
  filePatterns: ["*.py"]
tools:
  - name: python_lint
    description: Run ruff on a specific file.
    command: "ruff check ${file}"
    schema: |
      {
        "type": "object",
        "properties": {
          "file": { "type": "string", "description": "Path to the python file" }
        },
        "required": ["file"]
      }
---
# Python Linting Instructions
You are a Python quality expert. When using the `python_lint` tool:
1. Always suggest fixes for the errors found.
2. Refer to PEP 8 standards.
```

## 3. Core Components

### 3.1 `SkillManifest` (Enhanced)
Updated to support the `tools` array in frontmatter. Each tool entry contains:
- `name`: Tool name for the LLM.
- `command`: Bash-like command template with variable substitution (e.g., `${file}`, `${skillDir}`).
- `schema`: JSON Schema for parameter validation.

### 3.2 `ScriptToolSet` (New)
A dynamic `ToolSet` implementation that:
1. Map skill-defined tools to executable processes.
2. Performs variable substitution in the `command` string using arguments provided by the LLM.
3. Executes the process using a non-blocking bridge (wrapping `ProcessBuilder` or reusing `BashTools` logic).
4. Captures `stdout` and `stderr` as the tool observation.

### 3.3 `FileSystemSkillLoader`
Scans for `SKILL.md` files in:
1. **Workspace Scope**: `./.ganglia/skills/`
2. **User Scope**: `~/.ganglia/skills/`

### 3.4 `SkillRuntime` (Lazy Activation)
- **Discovery**: At startup, only the metadata (id, name, description, triggers) is loaded.
- **Activation**: When `activate_skill(id)` is called:
    1. The Markdown body (instructions) is loaded and injected into the System Prompt.
    2. The defined script tools are instantiated and registered in the `DefaultToolExecutor`.

## 4. Execution Workflow

```mermaid
sequenceDiagram
    participant LLM
    participant Loop as ReActAgentLoop
    participant Exec as ToolExecutor
    participant ScriptSet as ScriptToolSet
    participant Proc as External Process

    LLM->>Loop: Thought: I need to lint this file.
    LLM->>Loop: ToolCall: python_lint(file="main.py")
    Loop->>Exec: execute(python_lint)
    Exec->>ScriptSet: execute(python_lint, {file: "main.py"})
    ScriptSet->>ScriptSet: Substitute "ruff check ${file}" -> "ruff check main.py"
    ScriptSet->>Proc: Spawn "ruff check main.py"
    Proc-->>ScriptSet: Output: "L001: Missing docstring"
    ScriptSet-->>Exec: ToolInvokeResult(success, "L001: Missing docstring")
    Exec-->>Loop: Observation
    Loop-->>LLM: Feed observation...
```

## 5. Security & Safety
- **Sandbox**: All script tools run with the same constraints as `run_shell_command` (timeouts, restricted working directory).
- **Confirmation**: Activating a skill requires user consent (Interrupt).
- **Variable Injection**: Arguments are sanitized before being placed into the command template to prevent shell injection.
