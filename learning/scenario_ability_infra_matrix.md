# Agent 场景与能力全景矩阵 (多维解构版)

## 1. 拆分后的 Agent 全景交叉矩阵

在此版本中，我将“强依赖工具与基建”拆分为两列：**Agent Ability (Agent 能主动调用的手脚)** 和 **Infra (系统被动提供的底座保障)**。这让架构设计中的“主动权”归属更加明确。

| 视角分类 | 场景模块 | 核心大脑动作 | **Agent Ability**<br>(工具/技能/MCP/子体) | **Infra**<br>(安全/监控/面板/链路) | 关键衡量指标 (KPI) |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **功能性** | **Init (探底):** 仓库认知与初始化。 | 全局扫盘，推断项目规约。 | `list_directory`, `glob`, `read_file`。 | **Dashboard:** 展示扫描进度。 | 认知准确率。 |
| 👆 | **Pre (接单):** 需求理解与计划拆解。 | DAG 构建，依赖预判。 | **SubAgent:** 规划师；`KnowledgeBase` 检索。 | **Trace:** 记录任务拆解逻辑链路。 | 任务拆解正确性。 |
| 👆 | **Mid (执行):** 搜索定位与代码编写。 | 语义导航，原子级修改。 | `grep_search`, `apply_patch`, `write_file`。 | **Safe:** `PathSanitizer` 沙箱拦截。 | Diff 合法率。 |
| 👆 | **Post (交付):** 编译验证与结构化提交。| 根因提炼，闭环排错。 | `BashTools` (run_test), **Git SubAgent**。 | **Monitor:** ExitCode 与错误流捕获。 | 编译通过率。 |
| 👆 | **Aux (沉淀):** 资产积累与异常交接。 | 记忆持久化，生成失败报告。 | `KnowledgeTools` (remember), `ask_user`。 | **Store:** 写入长期 MemoryStore。 | 知识复用率，接管成本。 |
| --- | --- | --- | --- | --- | --- |
| **非功能性** | **Ops (可靠性):** 挂起与断点续传。 | 序列化/反序列化。 | `SessionManager` 内置 Dump 动作。 | **Monitor:** 会话存活探针/心跳检测。 | 状态无损恢复率。 |
| 👇 | **Sec (安全性):** 越权与审计拦截。 | 识别危险指令。 | **Builtin:** 意图防火墙/安全过滤器。 | **Safe:** chroot沙箱/隔离工作区。 | 越权拦截率 (100%)。 |
| 👇 | **Perf (经济性):** 熔断与资源控制。 | 止损阻断，模型路由。 | **Skill:** 动态模型路由插件。 | **Monitor:** Token 实时计费/水位线警报。| 任务平均 Token 成本。 |
| 👇 | **Obs (透明度):** 决策溯源。 | CoT 输出，差异渲染。 | `InteractionTools` (Diff 确认)。 | **Dashboard:** 实时展示 Agent 思考轨迹。 | 用户信任度。 |

---

## 2. 分析功能性的其他思考框架
 二、 除了“业务周期视角”，分析功能性的其他思考框架


  “业务周期视角”（例如：获客 -> 激活 -> 留存 -> 变现）非常适合分析产品/商业层面的功能。但对于一个复杂的软件系统（尤其是基于 LLM 的 Agent
  架构），仅靠业务周期是不够的。

  以下是几个业界常用的高阶视角和思考框架，可以帮助你全方位、无死角地梳理和定义功能性：


  1. 认知与控制流视角 (Sense-Think-Act 框架)
  👉 最适合分析：Agent 核心逻辑与智能体行为
  这个框架来源于控制论和机器人学，是将 Agent 拟人化来拆解其需要具备的功能。
   * Sense (感知/输入功能): 系统如何获取信息？
       * 功能举例： 多模态输入解析、长文本截断与预处理、触发器（Webhook/定时任务监听）、意图识别。
   * Think (思考/决策功能): 系统如何处理信息、制定计划？
       * 功能举例： 记忆检索（Short/Long-term Memory）、任务拆解（Planning）、反思与自我纠错（Reflection）、路由路由（Router，决定调用哪个
         Sub-agent 或 Tool）。
   * Act (行动/输出功能): 系统如何对外部世界产生影响？
       * 功能举例： 工具调用（API Execution）、代码沙盒执行、流式输出（Streaming）、发送邮件/消息。


  2. 用户旅程与角色视角 (User Journey & Persona Framework)
  👉 最适合分析：多端系统的交互与后台支撑功能
  一个系统通常不止服务于终端用户。通过穷举所有涉众（Stakeholders）及其使用旅程，可以挖出很多隐性功能。
   * End-User (终端用户): 关注交互与结果。
       * 功能举例： 会话历史管理、一键中断生成、多轮对话、反馈（点赞/踩）功能。
   * Developer / Prompt Engineer (开发者/提示词工程师): 关注调试与编排。
       * 功能举例： Prompt 沙盒测试、Tool 注册与鉴权配置、Trace 链路可视化回放（看 Agent 为什么调用错工具）。
   * Admin / Operator (管理员/运营者): 关注管控与成本。
       * 功能举例： 用户并发限制（Rate Limiting）、Token 额度分配、全局黑白名单配置、Dashboard 统计。


  3. 数据生命周期视角 (Data Lifecycle Perspective)
  👉 最适合分析：RAG 系统、知识库、底层流转逻辑
  从数据“出生”到“消亡”的整个流动过程去盘点系统必须具备的功能。
   * Ingestion (数据接入): 多格式文件解析（PDF, Word）、爬虫抓取、数据库同步。
   * Processing (数据处理): 数据清洗、Chunking（分块）、Embedding（向量化）。
   * Storage (数据存储): 向量数据库管理、元数据（Metadata）索引、缓存（Cache）功能。
   * Consumption (数据消费): 混合检索（Hybrid Search）、重排序（Reranking）。
   * Retention (数据维护): 数据过期自动清理、知识库热更新。


  4. KANO 模型与演进视角 (MVP & Product Evolution)
  👉 最适合分析：版本规划、功能优先级排序
  当你面对一堆需求时，可以通过功能的“属性”来分类，决定先做谁、后做谁。
   * 基础必备功能 (Must-be): 没有就不叫 Agent 的功能。
       * 举例： 大模型基础对接、简单的上下文记忆、基础的并发处理。
   * 期望型功能 (Performance): 投入越多，用户越满意的功能。
       * 举例： 响应速度优化、RAG 的召回准确率提升、支持更多的外部 Tool。
   * 魅力型功能 (Delighter): 用户没预期，但做了会“哇塞”的功能。
       * 举例： Agent 展现出主动提问澄清模糊需求的能力（Proactive Agent）、自动从对话中提取长期偏好记忆并自我进化。


  5. 架构分层视角 (DDD / 分层架构)
  👉 最适合分析：系统工程设计与模块解耦
  将系统像盖楼一样自上而下拆解，这也是技术架构师最常用的拆解方法：
   * 展示层 (Presentation): Web UI, CLI, 微信/钉钉小程序接入。
   * 编排层 (Orchestration): Workflow 引擎 (如 Dify/Coze 的连线逻辑)，Agent 编排逻辑 (LangChain/AutoGen)。
   * 能力层 (AgentAbility): 你提到的 Tool, Skill, MCP, Sub-agent。
   * 基建与资源层 (Infra/Resources): 你提到的 Safe, Dashboard, Monitor, Trace，以及 LLM 路由池、VectorDB。脑力有多强）、**环境支配力**（手脚有多大破坏性）和 **控制权**（听谁的指挥）这三个框架与时间周期的矩阵叠加，就能彻底穷尽一个 AI Agent 在工程领域所有可能的功能形态，并为每一个形态匹配相应的工具与基础设施。
