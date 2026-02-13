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
A specialized `ContextSource` for the `ContextEngine` that merges skill content into the system prompt.
- **Composition:** Iterates through active skills and appends their prompt content into a designated `<skills_context>` block.
- **Hierarchy:** Injected into the prompt at **Priority 5**, ensuring domain-specific heuristics steer the agent after core mandates but before the specific task plan.
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

## 6. Skill Discovery & Activation Workflow

The identification and triggering of local Skills rely on an automated **"Scan -> Description Match -> Function Calling"** mechanism.

### 6.1 Discovery Phase
At session startup or via the `/skills reload` command, the system scans specific local directories for `SKILL.md` files:
- **Project Level:** `.ganglia/skills/` within the current project.
- **User Level:** `~/.ganglia/skills/` in the user's home directory.
- **Identification:** Each skill must reside in its own folder and contain a `SKILL.md` file defining its metadata (Frontmatter) and instructions.

### 6.2 Prompt Injection (Lightweight Declaration)
To optimize token usage, the system employs a "Lightweight Declaration" strategy:
- **Metadata Extraction:** The CLI extracts the `name` and `description` of all available skills.
- **Tool Declaration:** These are injected as "Available Tools" into the system prompt or passed via the API's `tools` definition, allowing the model to know *what* is available without loading the full instructions yet.

### 6.3 Model Decision
When a user provides an instruction:
- **Semantic Analysis:** The model compares user intent against the descriptions of available skills.
- **Keyword Matching:** Rules defined in the skill's metadata (e.g., "trigger when user mentions 'audit' or 'test'") guide the model's decision-making process.

### 6.4 Trigger & Activation
Once the model decides to use a skill, it initiates a **Function Call**:
1. **Instruction:** The model issues `activate_skill(skill_id="...")`.
2. **Consent:** The CLI prompts the user for permission to load the skill (Security Guard).
3. **Full Injection:** Upon approval, the complete content of `SKILL.md` (the "Body") and associated resource paths are injected into the active context window.

### 6.5 Execution Phase
Once activated, the model operates with "Domain Expertise":
- **Chained Operations:** The model may use its new instructions to call other tools (e.g., `run_shell_command`) to execute scripts within the skill directory.
- **Result Feedback:** Output from these scripts is fed back to the model for final synthesis and response.
