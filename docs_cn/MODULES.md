# Ganglia 模块分解 (Module Decomposition)

> **状态**：草案 / 高层设计
> **基于**：需求文档 v0.1.0

本文档根据功能性和非功能性需求将 Ganglia 系统分解为逻辑模块。

## 1. 核心内核 (模块：`ganglia-core`)

**职责**：协调主 ReAct 循环、模型抽象和状态管理。

- **组件**：
  - `AgentLoop`：实现 `输入 -> 思考 -> 工具 -> 观察` 循环。
  - `ModelGateway`：LLM 提供商（OpenAI 等）的统一接口（`ModelProvider`），支持通过 EventBus 进行流式输出，以实现低延迟的 UI 更新。
  - `SessionManager`：管理当前会话状态，包括历史记录维护和用于崩溃恢复的序列化。
  - `PromptEngine`：编排分层提示词的构建。
    - `ContextResolver`：从静态文件、环境和记忆中获取片段。
    - `ContextComposer`：根据优先级合并片段并应用 Token 修剪策略。

## 2. 记忆系统 (模块：`ganglia-memory`)

**职责**：管理临时和长期上下文（“大脑”）。

*   **组件**：
    *   `LogManager`：处理每日 Markdown 日志的写入 (`.ganglia/logs/`)。
    *   `KnowledgeBase`：管理精选的 `MEMORY.md` 文件（读取/更新）。
    *   `TokenCounter`：Token 计数和保持上下文窗口在限制内的策略（滑动窗口/总结）。
    *   `ContextCompressor`：将已完成的任务/轮次总结为简明历史记录的逻辑。
    *   `retrieval-engine`：内部“主动检索”逻辑（搜索记忆文件）。

## 3. 工具与执行 (模块：`ganglia-tools`)

**职责**：发现、定义和安全执行工具（“双手”）。

*   **组件**：
    *   `ToolRegistry`：扫描并注册带有 `@AgentTool` 注解的类。
    *   `SchemaGenerator`：将 Java 方法签名转换为 LLM 所需的 JSON Schema。
    *   `ToolExecutor`：调用工具并处理结构化错误映射 (`ToolErrorResult`)。
    *   `ExecutionGuard`：强制执行超时和内存输出限制 (8KB)。
    *   **标准库**：
        *   `bash-tools`：原生命令执行（`list_directory`, `read_file`, `grep_search`, `glob`）。
        *   `vertx-fs-tools`：非阻塞 Java 文件系统操作（`write_file`）。
        *   `net-tools`：HTTP 客户端。
        *   `todo-tools`：计划与任务管理。

## 4. 技能系统 (模块：`ganglia-skills`)
**职责**：封装和管理行业特定的专业知识（知识 + 工具）。

*   **组件**：
    *   `SkillPackage`：定义技能的结构（清单、提示词、JAR）。
    *   `SkillManager`：处理生命周期（安装、激活、停用）和依赖解析。
    *   `SkillPromptInjector`：将技能特定的提示词和启发式方法合并到活跃的系统提示词中。
    *   `SkillRegistry`：可用技能的仓库或目录（本地和远程）。

## 5. 交互与规划 (模块：`ganglia-interaction`)

**职责**：人机交互工作流和高层规划。

- **组件**：
  - `Planner`：用于将请求分解为 `List<Step>` 的专门子 Agent 逻辑。
  - `ApprovalFlow`：管理“计划 -> 审核 -> 批准”状态机。
  - `InterruptManager`：拦截 `@Sensitive` 工具调用，并暂停执行以等待用户确认。
  - `UserInterface`：用于接收输入并向用户流式传输输出/事件的抽象。

## 6. 基础设施与支持 (模块：`ganglia-infra`)

**职责**：横向关注点和企业级就绪性。

- **组件**：
  - `Telemetry`：OpenTelemetry 集成，用于追踪和指标。
  - `ConfigManager`：从环境/文件中加载设置和 API 密钥。
  - `ExtensionLoader`：加载第三方工具 JAR 的机制。
  - `Reliability`：API 调用的熔断器和重试逻辑。
