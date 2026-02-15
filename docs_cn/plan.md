# Ganglia 开发计划 (Development Plan)

## 第一阶段：基础与持久化 (Tier 1)
**目标**：为当前会话循环提供鲁棒的状态管理。

- [x] **状态序列化**：
    - [x] 为 `SessionContext`（包括 `ToDoList` 和 `Turn` 历史）设计 JSON Schema。
    - [x] 实现 `FileStateEngine`：将 `SessionContext` 保存/加载到 `.ganglia/state/session_<id>.json`。
    - [x] 确保原子写以防止数据损坏。
- [x] **日志管理**：
    - [x] 实现 `LogManager`：将原始轮次交互（思考、工具、输出）追加到 `.ganglia/logs/<日期>.md`。
    - [x] 将日志记录集成到 `ReActAgentLoop` 中。

## 第二阶段：中期上下文与压缩 (Tier 2)
**目标**：通过与任务关联的智能总结来管理 Token 窗口限制。

- [x] **上下文修剪引擎**：
    - [x] 实现 `TokenCounter`（使用 JTokkit）以监控上下文使用情况。
    - [x] 实现滑动窗口逻辑以识别上下文何时超出限制。
- [x] **压缩逻辑**：
    - [x] 开发 `ContextCompressor`：提取 `Turn` 对象列表并请求 LLM 进行总结。
    - [x] 实现“任务-轮次”生命周期钩子：
        - [x] 检测 `todo_complete` 何时被调用。
        - [x] 触发与该任务相关的所有轮次的总结。
        - [x] 在 `SessionContext` 中将原始轮次替换为 `ToDoList` 中的总结注释。

## 第三阶段：长期知识 (Tier 3)
**目标**：跨会话持久化的项目级记忆。

- [x] **知识库管理器**：
    - [x] 实现 `KnowledgeBase`：读写 `MEMORY.md`。
    - [x] 为 `MEMORY.md` 定义标准章节（如“用户偏好”、“项目规范”、“架构决策”）。
- [x] **检索系统**：
    - [x] 将专门针对 `MEMORY.md` 的检索能力集成到系统提示词构建中。
    - [x] **Agent 搜索**：验证 Agent 在缺乏上下文时能否自主决定读取 `MEMORY.md`。

## 第四阶段：集成与工具
**目标**：向 Agent 开放记忆能力。

- [x] **`remember` 工具**：
    - [x] 实现 Agent 用于将事实显式写入 `MEMORY.md` 的工具。
- [x] **上下文注入**：
    - [x] 更新 `PromptEngine` 以动态注入：
        - [x] `ToDoList`（始终注入）。
        - [x] 压缩后的“成就”（来自 Tier 2）。
        - [x] 来自 `MEMORY.md` 的相关片段。
- [x] **验证**：
    - [x] 创建集成测试：“长上下文会话”，Agent 执行多个任务，触发压缩，并通过总结成功回忆早期上下文。

## 第五阶段：高级交互（中断）
**目标**：允许 Agent 向用户寻求帮助或进行选择。

- [x] **中断机制**：
    - [x] 更新 `ToolDefinition` 以包含 `isInterrupt`。
    - [x] 更新 `ReActAgentLoop` 以在调用中断工具时暂停。
- [x] **`ask_selection` 工具**：
    - [x] 实现 `InteractionTools` 及其 `ask_selection(question, options)`。
    - [x] 确保选项展示清晰（如索引列表）。
- [x] **恢复机制**：
    - [x] 实现使用用户选择恢复会话的方法（注入为工具结果）。

## 第六阶段：技能系统
**目标**：通过模块化包实现领域特定的专业知识。

- [x] **技能基础**：
    - [x] 定义 `SkillPackage` 和 `SkillManifest` 模型。
    - [x] 实现用于本地和类路径发现的 `SkillRegistry`。
- [x] **生命周期与集成**：
    - [x] 在 `SessionContext` 中跟踪 `activeSkillIds`。
    - [x] 开发 `SkillPromptInjector` 以将活跃技能的提示词合并到 `PromptEngine`。
    - [x] 更新 `ToolExecutor` 以支持从活跃技能动态注册工具。
- [x] **Agent 交互**：
    - [x] 实现 `list_available_skills` 工具。
    - [x] 实现 `activate_skill` 工具。
    - [x] 添加基于文件模式触发的自动技能建议逻辑。
- [x] **验证**：
    - [x] 创建“Java 专家”演示技能，验证 Agent 能激活并使用其专业提示词/工具。

## 第七阶段：低延迟流式处理
**目标**：通过提供推理过程中的实时反馈来提升用户体验。

- [x] **内核重构**：
    - [x] 更新 `ReActAgentLoop` 以使用 `ModelGateway.chatStream`。
    - [x] 实现基于会话的 EventBus 寻址方案 (`ganglia.stream.<sessionId>`)。
- [x] **UI 集成**：
    - [x] 在 UI 层实现监听器，将 EventBus Token 管道传输到 `stdout`。
- [x] **工具调用处理**：
    - [x] 确保在流完成后，工具调用仍能被正确累积并顺序执行。
- [x] **验证**：
    - [x] 验证 Agent 的“思考”过程对用户实时可见。

## 第八阶段：网络与系统工具
**目标**：通过 Web 访问和通用 Shell 执行扩展 Agent 的行动能力。

- [x] **Web 能力**：
    - [x] 使用 Vert.x `WebClient` 实现 `WebFetchTools`。
    - [x] 工具：`web_fetch(url)` - 获取 URL 内容。
- [x] **通用 Shell 能力**：
    - [x] 实现 `BashTools` 用于通用命令执行。
    - [x] 工具：`run_shell_command(command)` - 执行带超时和安全防护的任意 Bash 命令。
- [x] **集成**：
    - [x] 更新 `ToolsFactory` 和 `DefaultToolExecutor` 以包含新工具。
- [x] **验证**：
    - [x] 为两个工具集编写单元测试。
    - [x] 创建集成测试，Agent 获取页面并根据结果运行 Shell 命令。

## 第十阶段：核心指南系统
**目标**：通过 `GANGLIA.md` 文件实现项目级的行为引导。

- [x] **指南加载逻辑**：
    - [x] 实现从项目根目录读取 `GANGLIA.md` 的逻辑。
    - [x] 实现回退到默认硬编码指南的机制。
- [x] **提示词集成**：
    - [x] 重构 `StandardPromptEngine`，用加载的内容替换硬编码的“指南”部分。
- [x] **初始化**：
    - [x] 添加逻辑，在项目初始化期间如果 `GANGLIA.md` 不存在则创建一个默认文件。
- [x] **验证**：
    - [x] 验证修改 `GANGLIA.md` 能在下一轮次立即更新 Agent 行为。

## 第十一阶段：会话与轮次管理
**目标**：通过 `SessionManager` 提供管理会话和轮次的结构化方式。

- [x] **核心会话 API**：
    - [x] 创建 `SessionManager` 接口。
    - [x] 使用 `StateEngine` 实现 `DefaultSessionManager`。
- [x] **轮次管理增强**：
    - [x] 重构 `SessionContext` 和 `Turn` 以更好地支持生命周期转换。
    - [x] 更新 `ReActAgentLoop` 以使用 `SessionManager` 进行状态持久化。
- [x] **重构与示例**：
    - [x] 将 CLI 会话逻辑移至 `me.stream.ganglia.example.InteractiveDemo`。
    - [x] 更新 `Main` 专注于核心框架初始化。

## 第十二阶段：集成场景与 E2E 验证
**目标**：通过复杂的多工具场景验证完整的 Agent 逻辑。

- [x] **场景文档**：
    - [x] 创建 `docs/INTEGRATION_SCENARIOS.md`。
- [x] **E2E 测试套件**：
    - [x] 实现 `FullWorkflowIT` 覆盖 Web -> Shell -> Memory。
    - [x] 在真实循环中实现中断 -> 恢复的测试。
- [x] **测试基础设施重构**：
    - [x] 将集成测试移至 `src/integration-test/java`。
    - [x] 配置 `maven-failsafe-plugin` 独立执行 IT 测试。
- [x] **工具优化 (TDD)**：
    - [x] 增强 `WebFetchTools` 处理状态码。
    - [x] 增强 `BashTools` 支持复杂管道命令。
- [x] **最终检查**：
    - [x] 使用真实模型调用验证所有场景通过。

## 第十五阶段：技能系统重构 (SKILL.md & 延迟激活)
**目标**：转型为轻量级、基于文件的技能发现与激活机制。

- [x] **统一技能格式**：
    - [x] 从 `skill.json` 迁移到带有 YAML Frontmatter 的 `SKILL.md`。
    - [x] 更新 `SkillManifest` 以解析 Markdown 文件。
- [x] **延迟激活逻辑**：
    - [x] 重构 `SkillRegistry`，在初始提示词构建期间仅公开名称/描述。
    - [x] 实现 `activate_skill` 工具，将完整的 `SKILL.md` 正文加载到上下文窗口。
- [x] **用户授权层**：
    - [x] 在调用 `activate_skill` 时增加中断/确认步骤。
- [x] **发现范围扩展**：
    - [x] 支持扫描 `~/.ganglia/skills/` 和项目本地 `.ganglia/skills/`。
- [x] **验证**：
    - [x] 移植 `git-smart-commit` 技能并验证完整流程。

## 第十三阶段：系统化上下文引擎 (GEMINI.md 机制)
**目标**：使用文件驱动的上下文将提示词构建与代码解耦。

- [x] **核心架构**：
    - [x] 定义 `ContextSource` 和 `ContextFragment` 模型。
    - [x] 实现 `MarkdownContextResolver` 按标题解析文件。
- [x] **动态注入**：
    - [x] 实现 `EnvironmentSource` 注入 OS 和目录结构信息。
    - [x] 支持 Markdown 文件中的变量替换。
- [x] **编排**：
    - [x] 创建带优先级修剪功能的 `ContextComposer`。
    - [x] 重构 `StandardPromptEngine` 使用新引擎。
- [x] **验证**：
    - [x] 通过运行时修改 `GANGLIA.md` 测试 Agent 的自适应能力。

## 第十四阶段：配置与热加载
**目标**：使用带动态加载功能的 JSON 配置文件将模型和系统参数与代码解耦。

- [x] **配置 Schema 与加载**：
    - [x] 为 `config.json` 设计 JSON Schema。
    - [x] 使用 Vert.x 实现 `ConfigManager` 进行加载和解析。
- [x] **集成**：
    - [x] 更新 `DefaultSessionManager` 和 `OpenAIModelGateway` 使用 `ConfigManager` 的值。
- [x] **热加载机制**：
    - [x] 实现文件观察器监控配置文件。
    - [x] 在修改文件时实时更新内部配置状态。
- [x] **验证**：
    - [x] 增加配置解析和默认回退的单元测试。
    - [x] 验证修改模型名称能立即影响下一轮次。

## 第十六阶段：标准工程工具
**目标**：增强 Agent 使用高性能、结构化工具发现和修改代码库的能力。

- [x] **文件系统写能力**：
    - [x] 实现 `write_file` 工具：支持创建或覆盖文件内容。
    - [x] 集成 Vert.x 非阻塞 I/O。
- [x] **高级搜索与发现**：
    - [x] 实现 `grep_search` 工具：提供跨项目的递归、基于正则的文本搜索。
    - [x] 实现 `glob` 工具：支持模式匹配查找文件（如 `**/*.java`）。
- [x] **集成与优化**：
    - [x] 更新工具工厂，引入元数据校验以实现大文件读取保护。
- [x] **验证**：
    - [x] 编写搜索和写入逻辑的单元测试。
    - [x] 创建 E2E 场景：“代码探索”，Agent 使用 `glob` 定位文件并使用 `grep_search` 查找实现。

## 第十七阶段：增强型人机交互
**目标**：完善交互能力，支持结构化选择和自由反馈，确保在错误场景下的鲁棒恢复。

- [x] **统一交互工具**：
    - [x] 实现 `ask_selection` 工具：支持文本输入和选项选择。
    - [x] 确保 `InteractiveDemo` 中 stdin 处理的非阻塞化（使用 EventBus）。
- [x] **循环集成**：
    - [x] 优化 `ReActAgentLoop` 的恢复逻辑，自然地处理多工具序列中的中断。
    - [x] 修复轮次完成时的消息重复问题。
- [x] **错误恢复增强**：
    - [x] 更新 `ErrorHandlingReActDemo` 在遇到歧义时使用交互工具。
- [x] **验证**：
    - [x] 为 `ask_selection` 编写单元测试。
    - [x] 创建“交互式故障排除”的 E2E 演示。
