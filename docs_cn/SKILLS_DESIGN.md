# Ganglia 技能系统设计 (Skill System)

> **状态**：初始设计
> **模块**：`ganglia-skills`
> **相关**：[架构](ARCHITECTURE.md), [核心内核](CORE_KERNEL_DESIGN.md)

## 1. 简介
Ganglia 中的**技能 (Skill)** 是一个模块化的包，它通过特定领域的专业知识来扩展 Agent 的能力。它封装了专门的系统提示词、最佳实践和自定义工具。这种模块化方法使核心 Agent 保持轻量，同时允许其按需扩展专业知识。

## 2. 技能结构
技能是一个自包含的目录或 JAR 文件，包含清单文件和关联资源。

### 2.1 文件结构
```text
<skill-root>/
├── SKILL.md                # 清单、元数据及指令 (带 YAML Frontmatter)
├── prompts/                # (可选) 领域特定的指令
│   ├── best_practices.md
│   └── architecture_rules.md
├── tools/                  # (可选) 编译好的 Java 工具类
│   └── specialized/
│       └── CustomTool.class
└── resources/              # (可选) 静态知识或模板
```

### 2.2 技能清单 (`SKILL.md`)
使用 Markdown 文件配合 YAML Frontmatter 定义技能如何与 Agent 集成。

```markdown
---
id: "java-vertx-expert"
name: "Java & Vert.x 专家"
description: "提供地道的 Java 17 模式和 Vert.x 5 诊断工具。"
version: "1.0.0"
tools: 
  - "me.stream.ganglia.skills.vertx.VertxInspectorTool"
triggers:
  filePatterns: ["pom.xml", ".*\.java"]
---

# Java & Vert.x 专家指令

当你处理 Vert.x 项目时，请遵循以下规则：
1. 始终使用非阻塞的 Future 链。
2. 优先使用 Record 定义 DTO。
...
```

## 3. 系统组件

### 3.1 `SkillRegistry`
所有已安装技能的中央目录。
- **发现**：扫描固定位置（如 `~/.ganglia/skills/` 和项目本地 `.ganglia/skills/`）以及类路径中的 `SKILL.md` 文件。
- **索引**：维护技能能力和触发器的可搜索索引。

### 3.2 `SkillManager`
管理活跃 `SessionContext` 中技能的生命周期。
- **激活**：当技能被激活时，其 ID 会添加到 `SessionContext.activeSkillIds` 中。
- **资源加载**：从技能包中加载提示词并实例化工具类。
- **持久化**：技能激活状态作为会话状态的一部分保存。

### 3.3 `SkillPromptInjector`
用于 `PromptEngine` 的专门 `ContextSource`，负责将技能内容合并到系统提示词中。
- **组合**：遍历活跃技能并将它们的提示词内容追加到指定的 `<skills_context>` 块中。
- **层级**：以**优先级 5** 注入到提示词中，确保领域特定的启发式方法在核心指令之后、具体任务计划之前指导 Agent。

### 3.4 `DynamicToolRegistry`
扩展 `ToolExecutor` 以支持由技能提供的工具。
- **按需注册**：当技能被激活时，其工具会被动态注册并可供 Agent 调用。

## 4. 交互模式

### 4.1 手动激活
用户可以显式要求使用某项技能：
> “使用 AWS 技能帮我部署这个项目。”

### 4.2 Agent 自主发现
Agent 可以访问 `list_available_skills` 工具。如果它遇到缺乏特定工具或知识的任务，它可以：
1. 搜索 `SkillRegistry`。
2. 向用户提议激活：“我看到你正在处理 Kubernetes 任务。需要我激活 'k8s-expert' 技能吗？”
3. 调用 `activate_skill(id)`。

### 4.3 基于触发器的建议
系统监控环境（文件结构、技术栈）。如果在 `triggers` 中发现匹配，Agent 可以主动建议激活该技能。

## 5. 安全与防护
- **工具沙箱**：由技能提供的工具同样受到执行防护（超时、内存限制）的约束。
- **人机交互**：技能清单中标记为 `@Sensitive` 的工具仍需显式的用户确认。

## 6. 技能发现与激活流程

本地技能的识别和触发依赖于自动化的**“扫描 -> 描述匹配 -> 函数调用”**机制。

### 6.1 发现阶段
在会话启动时，系统扫描特定的本地目录以查找 `SKILL.md` 文件：
- **项目级**：当前项目中的 `.ganglia/skills/`。
- **用户级**：用户主目录中的 `~/.ganglia/skills/`。

### 6.2 提示词注入（轻量级声明）
为了优化 Token 使用，系统采用“轻量级声明”策略：
- **元数据提取**：提取所有可用技能的 `name` 和 `description`。
- **工具声明**：将这些作为“可用工具”注入系统提示词，让模型知道有哪些能力可用，而无需加载完整指令。

### 6.3 模型决策
当用户提供指令时：
- **语义分析**：模型将用户意图与可用技能的描述进行对比。
- **关键词匹配**：元数据中定义的规则引导模型的决策过程。

### 6.4 触发与激活
一旦模型决定使用某项技能，它会发起**函数调用**：
1. **指令**：模型发出 `activate_skill(skillId="...")`。
2. **确认**：CLI 提示用户批准加载该技能（安全防护）。
3. **完整注入**：获批后，`SKILL.md` 的完整内容（正文）将被注入到活跃的上下文窗口中。
