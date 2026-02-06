# Ganglia Skill System Design

> **Status:** Initial Design
> **Module:** `ganglia-skills`
> **Related:** [Architecture](ARCHITECTURE.md), [Core Kernel](CORE_KERNEL_DESIGN.md)

## 1. Introduction
A **Skill** in Ganglia is a modular package that extends the agent's capabilities with domain-specific expertise. It encapsulates specialized system prompts, best practices, and custom tools. This modular approach keeps the core agent lightweight while allowing it to scale its expertise on demand.

## 2. Skill Anatomy
A skill is a self-contained directory or JAR file containing a manifest and associated resources.

### 2.1 File Structure
```text
<skill-root>/
├── skill.json              # Metadata and configuration
├── prompts/                # Domain-specific instructions (Markdown)
│   ├── best_practices.md
│   └── architecture_rules.md
├── tools/                  # (Optional) Compiled Java tool classes
│   └── specialized/
│       └── CustomTool.class
└── resources/              # (Optional) Static knowledge or templates
```

### 2.2 The Manifest (`skill.json`)
The manifest defines how the skill integrates with the agent.

```json
{
  "id": "java-vertx-expert",
  "version": "1.0.0",
  "name": "Java & Vert.x Specialist",
  "description": "Provides idiomatic Java 17 patterns and Vert.x 5 diagnostic tools.",
  "author": "Ganglia Core Team",
  "prompts": [
    {
      "id": "vertx-guidelines",
      "path": "prompts/best_practices.md",
      "priority": 10
    }
  ],
  "tools": [
    "me.stream.ganglia.skills.vertx.VertxInspectorTool"
  ],
  "activationTriggers": {
    "filePatterns": ["pom.xml", "build.gradle", ".*\.java"],
    "keywords": ["vertx", "eventbus", "reactive"]
  }
}
```

## 3. System Components

### 3.1 `SkillRegistry`
The central catalog of all installed skills.
- **Discovery:** Scans fixed locations (e.g., `~/.ganglia/skills/`) and the classpath for `skill.json` files.
- **Indexing:** Maintains a searchable index of skill capabilities and triggers.

### 3.2 `SkillManager`
Manages the lifecycle of skills within an active `SessionContext`.
- **Activation:** When a skill is activated, its ID is added to `SessionContext.activeSkillIds`.
- **Resource Loading:** Loads prompts and instantiates tool classes from the skill package.
- **Persistence:** Skill activation state is saved as part of the session state.

### 3.3 `SkillPromptInjector`
A component of the `PromptEngine` that merges skill content into the system prompt.
- **Composition:** Iterates through active skills and appends their prompt content into a designated `<skills_context>` block in the main system prompt.
- **De-duplication:** Ensures that if multiple skills provide overlapping instructions, they are handled gracefully (e.g., via priority).

### 3.4 `DynamicToolRegistry`
Extends the `ToolExecutor` to support tools provided by skills.
- **On-demand Registration:** When a skill is activated, its tools are dynamically registered and become available for the agent to call.

## 4. Interaction Patterns

### 4.1 Manual Activation
The user can explicitly request a skill:
> "Use the AWS skill to help me deploy this."

### 4.2 Agentic Discovery
The agent has access to a `list_available_skills` tool. If it encounters a task for which it lacks specific tools or knowledge, it can:
1. Search the `SkillRegistry`.
2. Propose activation to the user: "I see you are working with Kubernetes. Should I activate the 'k8s-expert' skill?"
3. Invoke `activate_skill(id)`.

### 4.3 Trigger-based Suggestions
The system monitors the environment (file structure, technology stack). If a match is found in `activationTriggers`, the agent can proactively suggest the skill.

## 5. Security & Safety
- **Tool Sandboxing:** Tools provided by skills are subject to the same execution guards (timeouts, memory limits) as built-in tools.
- **Human-in-the-Loop:** Tools marked as `@Sensitive` within a skill manifest still require explicit user confirmation.
- **Code Signing:** (Future) Support for signed skill packages to prevent execution of malicious third-party code.
