# Ganglia Core Guidelines Design

> **Status:** Initial Design
> **Related:** [Architecture](ARCHITECTURE.md), [Memory Architecture](MEMORY_ARCHITECTURE.md)

## 1. Introduction
Following the "Memory as Code" and "Steerability via Prompting" principles, Ganglia utilizes a project-level configuration file to define its core operational mandates, security rules, and behavioral guidelines. This file, `GANGLIA.md`, serves as the agent's "Prime Directive," ensuring consistent behavior across different sessions within the same project.

## 2. The `GANGLIA.md` File
The `GANGLIA.md` file is a Markdown document located in the project root. It is intended to be human-readable, version-controlled, and easily editable by the user to tune the agent's behavior.

### 2.1 File Structure (Example)
```markdown
# Ganglia Core Guidelines

## 1. Operational Mandates
- Always prioritize non-blocking Vert.x patterns.
- Use `todo_add` to break down any task requiring more than 3 steps.
- Never modify files outside the project root.

## 2. Security & Safety
- Never log or commit API keys or secrets.
- Ask for confirmation before executing `rm -rf` or similar destructive commands.

## 3. Project Conventions
- Use Java 17 records for data-only classes.
- Follow the architectural patterns defined in `docs/ARCHITECTURE.md`.
```

## 3. System Integration

### 3.1 Loading Mechanism
The `StandardPromptEngine` is responsible for loading the `GANGLIA.md` file during the system prompt construction phase.

- **Path:** Root directory (working directory).
- **Fallback:** If `GANGLIA.md` is missing, the engine falls back to a hardcoded set of "Default Guidelines."
- **Caching:** The content should be loaded once per session or refreshed on demand to avoid unnecessary I/O while allowing runtime updates.

### 3.2 Prompt Injection
The content of `GANGLIA.md` is injected into a dedicated section of the system prompt:

```text
## Core Guidelines
[Content of GANGLIA.md]
```

## 4. Relationship to Other Systems

| System | Role | Persistence |
| :--- | :--- | :--- |
| **Guidelines (`GANGLIA.md`)** | Immutable (mostly) behavioral rules and project standards. | Project-level (Git) |
| **Memory (`MEMORY.md`)** | Evolving facts, preferences, and architectural decisions. | Project-level (Git) |
| **Skills** | Domain-specific expertise and tools (e.g., "AWS Expert"). | Module-level |

## 5. Benefits
- **Transparency:** Users can see exactly what rules the agent is following.
- **Steerability:** Users can easily modify agent behavior without changing Java code.
- **Portability:** Guidelines travel with the project repository.
