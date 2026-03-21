# Ganglia Skill Architecture

> **Status:** In Development
> **Version:** 0.1.5
>
> **Package:** `work.ganglia.port.internal.skill` / `work.ganglia.infrastructure.internal.skill`
> **Related:** [Architecture](../ARCHITECTURE.md), [Core Kernel](CORE_KERNEL_DESIGN.md)

## 1. Objective

Enable extensible agent expertise through lightweight script-based tools and JAR-based Java extensions loaded dynamically.

## 2. Core Concepts

### 2.1 Skill Ports (work.ganglia.port.internal.skill)

- **`SkillService`**: Registry for available and active skills.
- **`SkillManifest`**: Immutable record defining a skill's identity, guidelines, and tools.
- **`SkillToolDefinition`**: Describes individual tools within a skill.

### 2.2 Skill Infrastructure (work.ganglia.infrastructure.internal.skill)

- **`DefaultSkillService`**: Orchestrates skill discovery and session-based activation.
- **`FileSystemSkillLoader`**: Scans `.ganglia/skills` for directory-based skills.
- **`JarSkillLoader`**: Handles dynamic loading of Java `.jar` skills with ClassLoader isolation.

## 3. Execution & Task Architecture

Skills are integrated into the Kernel through the `SkillTask` (in `work.ganglia.kernel.task`).

1. **Discovery**: `SkillService` finds all `SKILL.md` files.
2. **Activation**: The agent calls `activate_skill` (provided via `SkillTools`).
3. **Execution**: When a skill tool is called, `SkillTask` delegates to either:
   - **`ScriptSkillToolSet`**: For CLI commands (Node, Python, etc.).
   - **`JavaSkillToolSet`**: For reflection-based Java method calls.

## 4. Context Injection

The `SkillContextSource` ensures that:
- **Expert Guidelines**: The "System Prompt" fragments from `SKILL.md` are added to the Kernel's prompt.
- **Tool Schemas**: Schemas for tools belonging to active skills are merged into the model request.

## 6. Model Context Protocol (MCP) Integration

While Skills provide internal, domain-specific extensions, Ganglia also supports the **Model Context Protocol (MCP)** for cross-platform tool integration.

### 6.1 MCP vs. Skills

- **Skills**: Native to Ganglia, tied to `.ganglia/skills`, can be JARs or simple scripts with a `SKILL.md` manifest.
- **MCP**: Industry-standard protocol. Ganglia acts as an MCP Client, connecting to external MCP Servers (e.g., SQLite, GitHub, Chrome DevTools) via **Stdio** or **SSE** transports.

### 6.2 Configuration & Lifecycle

- **Config**: MCP servers are configured in `.ganglia/.mcp.json`.
- **Loading**: `McpConfigManager` bootstraps servers during the `GangliaKernel` initialization.
- **Tool Mapping**: Tools provided by MCP servers are automatically wrapped into a `McpToolSet` and made available to the agent.
- **Shutdown**: All MCP server child processes are gracefully closed via the JVM shutdown hook managed by `McpRegistry`.

## 7. Directory Constants

Paths are managed via `work.ganglia.util.Constants`:
- `DIR_SKILLS`: Default location (`.ganglia/skills`).
- `FILE_SKILL_MD`: Standard manifest filename (`SKILL.md`).
- `FILE_MCP_JSON`: MCP configuration (`.ganglia/.mcp.json`).
