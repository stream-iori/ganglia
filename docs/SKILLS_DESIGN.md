# Ganglia Skill Architecture (Implemented)

> **Status:** Implemented (v1.0.0)
> **Module:** `ganglia-skills`
> **Related:** [Architecture](ARCHITECTURE.md), [Core Kernel](CORE_KERNEL_DESIGN.md)

## 1. Objective
Enable extensible agent expertise through both lightweight script-based tools and JAR-based Java extensions loaded dynamically.

## 2. Skill Types & Loading

### 2.1 Script-based Skills
Standard Gemini CLI/Claude Code style skills.
- **`FileSystemSkillLoader`**: Scans directories for `SKILL.md` and associated scripts.
- **`ScriptSkillToolSet`**: Orchestrates external process execution (Node, Python, Bash) with variable substitution.

### 2.2 JAR-based Skills
Java extensions for high-performance or complex tool logic.
- **`JarSkillLoader`**: Scans for `.jar` files in skill directories.
- **`JavaSkillToolSet`**: Executes tool methods using reflection.
- **ClassLoader Isolation**: `DefaultSkillRuntime` creates a unique `URLClassLoader` for each JAR skill, preventing dependency conflicts with the core runtime.

## 3. Tool Definition Matrix

The `tools` array in `SKILL.md` frontmatter defines the execution mode:

- **`type: SCRIPT`**: Requires a `command` template.
- **`type: JAVA`**: Requires a `className` implementing `ToolSet`.

Example `SKILL.md` for Java:
```markdown
---
id: db-expert
tools:
  - name: query_database
    type: JAVA
    java:
      className: "me.stream.ganglia.skills.db.DbToolSet"
    schema: |
      { "type": "object", ... }
---
Instructions for DB expert...
```

## 4. Lifecycle Management

- **`SkillService`**: Handles the activation and deactivation of skills per session.
- **Context Injection**: `SkillContextSource` (part of `PromptEngine`) detects active skills and injects their persona guidelines and tool schemas into the system prompt.

## 5. Security & Isolation

- **Process-level isolation** for scripts.
- **ClassLoader-level isolation** for Java tools.
- **Schema Validation** via `ToolCallValidator` before tool execution.
