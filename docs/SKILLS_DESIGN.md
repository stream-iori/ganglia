# Ganglia Skill System Redesign (Hybrid: Script & Java JAR)

> **Status:** Updated Design
> **Module:** `ganglia-skills`
> **Related:** [Architecture](ARCHITECTURE.md), [Core Kernel](CORE_KERNEL_DESIGN.md)

## 1. Objective
To provide a highly extensible skill system that supports both lightweight script-based tools (Gemini CLI style) and heavy-duty Java-based extensions loaded dynamically via ClassLoaders. This hybrid approach ensures ease of creation for end-users while allowing enterprise-grade integrations.

## 2. Skill Types & Structures

### 2.1 Script-based Skill (Folder)
Standard Gemini CLI structure.
```text
my-script-skill/
├── SKILL.md          # Tool definitions (command template)
└── scripts/          # python, node, bash scripts
```

### 2.2 JAR-based Skill (Extensible)
A JAR file containing logic and metadata.
```text
my-java-skill.jar
├── SKILL.md          # Tool definitions (class mapping)
└── me/stream/...     # Compiled classes
```

## 3. Tool Definition Matrix

The `tools` array in `SKILL.md` frontmatter uses a structured schema to support different execution modes:

### `SkillToolDefinition` Structure
- `name`: (Required) Tool name for the LLM.
- `description`: (Required) Tool description.
- `type`: (Required) `SCRIPT` or `JAVA`.
- `script`: Embedded record for `SCRIPT` type.
    - `command`: (Required) Execution command template.
- `java`: Embedded record for `JAVA` type.
    - `className`: (Required) Java class implementing `ToolSet`.
- `schema`: (Required) JSON Schema for arguments.

### Example `SKILL.md` for Java:
```markdown
---
id: db-expert
tools:
  - name: query_database
    type: JAVA
    java:
      className: "me.stream.ganglia.skills.db.DbTool"
    schema: |
      { "type": "object", ... }
---
Instructions for DB expert...
```

## 4. Component Architecture

### 4.1 Hybrid `SkillLoader`
The system uses multiple loaders to discover skills. The loaders to be used are determined at startup based on configuration.
- **`FileSystemSkillLoader`**: Scans directories for `SKILL.md`.
- **`JarSkillLoader`**: Scans for `.jar` files in skill directories, loads the internal `SKILL.md`, and prepares a `URLClassLoader` for that specific skill.

### 4.2 Dynamic ClassLoading
To prevent dependency hell:
- Each Java-based skill gets its own **Isolated ClassLoader**.
- The `DefaultSkillRuntime` manages these ClassLoaders.

### 4.3 Tool Execution
- **`ScriptSkillToolSet`**: Orchestrates external process execution with variable substitution.
- **`JavaSkillToolSet`**: Orchestrates reflection-based execution of Java tools from JARs.

## 5. Startup Configuration
Ganglia can be configured to use specific skill loading strategies via `ganglia-config.json`:

```json
{
  "skills": {
    "loaders": ["filesystem", "jar"],
    "paths": [".ganglia/skills", "~/skills"]
  }
}
```

## 6. Security & Sandboxing
- **Script Tools**: Restricted via process-level timeouts and working directory limits.
- **Java Tools**: Restricted via ClassLoader isolation and (future) JVM-level security policies.
