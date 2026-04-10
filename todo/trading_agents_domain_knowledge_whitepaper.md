# 交易智能体 (Trading Agents)：数字化资管机构实战与进化白皮书 (V1.0 Implementation)

> **导航**: [🔙 返回架构设计说明书](multi_agent_system_architecture_design.md)

## 第一章：确定航向——建立数字化投资"宪法"

在进入市场之前，系统通过 **Portfolio Manager** 设定当前的**投资风格 (Style)**、**输出语言 (Language)** 与 **准入漏斗 (Universe Filter)**。这决定了后续分析中不同 Agent 的"话语权权重"。

- **风格锚定**：价值发现、成长浪潮、事件驱动。
- **配置驱动**：`max_debate_rounds`, `max_risk_discuss_rounds` 等参数决定了逻辑博弈的深度。`output_language` 支持多语言输出，所有 Analyst 的报告末尾自动附加语言指令。
- **标的上下文 (Instrument Context)**：系统通过 `instrument_context` 为每个 Agent 注入标的类型信息（股票/加密货币等），确保分析逻辑与标的特性匹配。

---

## 第二章：感知机会——并行的多专家感官扫描

系统通过四个独立的分析师 Agent 构建全景感官，严格遵守**"感官剥夺"**原则。系统支持 **yfinance** 与 **Alpha Vantage** 双引擎，并内置**自动回退机制 (Vendor Fallback)**——当主数据源触发 Rate Limit 时自动切换备用数据源，确保分析流程不中断。

- **市场分析师 (Market Analyst)**：从 13 种技术指标中选择最多 8 个互补指标（SMA/EMA/MACD/RSI/Bollinger/ATR/VWMA/MFI），提取高信噪比技术信号。同时负责定义 **市场环境标签 (Market Regime Tags)**，如 [Trending/Ranging]、[High/Low Volatility]。
- **基本面分析师 (Fundamentals Analyst)**：穿透财务三表（Balance Sheet/Cashflow/Income Statement）及公司综合指标（市值/PE/EPS 等），识别财务真实性，并输出 **估值状态标签**（如 [Undervalued/Overvalued]）。
- **新闻分析师 (News Analyst)**：识别全球宏观变量与公司特定催化剂，同时监控**内幕交易 (Insider Transactions)**，并根据事件类型输出 **宏观/事件标签**（如 [Earnings/Federal Reserve]）。
- **社交分析师 (Social Analyst)**：监测社交媒体情绪与非理性动量。
- **受控交叉 (Controlled Context Sharing)**：在维持隔离的前提下，系统允许分析师在生成第二轮摘要时共享核心 `Regime Tags`，以消除逻辑孤岛，提升跨维度感知能力。
- **数据完整性保障**：
    - **前视偏差防护 (Look-Ahead Bias Prevention)**：所有历史数据（OHLCV、财务三表）在数据层按 `curr_date` 参数自动过滤，确保回测场景下不泄漏未来信息。`load_ohlcv()` 过滤行情数据，`filter_financials_by_date()` 过滤财务报告期。
    - **5年滚动缓存**：行情数据按 `{symbol}-YFin-data-{start}-{end}.csv` 格式本地缓存，5年滚动窗口，避免重复拉取。
    - **指数退避重试 (Exponential Backoff)**：yfinance 数据拉取遇到 Rate Limit 时，执行3次指数退避重试（2s/4s/8s），保障数据获取的稳定性。

---

## 第三章：逻辑质证——多层对抗与逻辑炼金

### 3.1 法庭式研究辩论 (Research Crucible)
- **对抗生成 (Isolated Adversarial Generation)**：多空研究员在隔离上下文中独立寻找证据。Bull 与 Bear 拥有**专属情境记忆 (BM25)**，用于强化立场深度。双方均接收全部四份分析报告（Market/Sentiment/News/Fundamentals）作为论据来源。
- **交叉质证 (Cross-Examination)**：Research Manager (Invest Judge) 负责提炼原始分歧，引入 **Agent 权重打分机制** 与 **人类纠偏打分 (Human Scoring)**。
- **概率校准 (Confidence Calibration)**：所有 Agent 输出的 `confidence` 必须经过 **历史实际命中率 (Regime Hit Rate)** 的贝叶斯校准，防止模型的主观盲目自信。最终产出《投资计划书》。

### 3.2 镜像博弈与鲁棒性测试
系统模拟对手方视角，测试计划在被"假想敌"利用时的鲁棒性。

---

## 第四章：风险审计——多维偏好下的压力测试

投资计划必须通过三个立场迥异的审计员（Aggressive/Neutral/Conservative）的循环辩论测试。三位审计员均接收全部四份分析报告及交易员决策作为输入，确保风控判断基于完整信息。Portfolio Manager 综合风控辩论，输出五级评分（Buy/Overweight/Hold/Underweight/Sell），由 **Signal Processor** 通过专用 LLM 从非结构化文本中提取标准化信号。

---

## 第五章：智慧成长——归因、记忆与对称演化

### 5.1 归因复盘与"四步法"审计 (Causal Audit)
- **分析四步法**：Reflector 在交易结束后，按"推理 (Reasoning) → 改进 (Improvement) → 总结 (Summary) → 查询 (Query)"四步法对每个角色（Bull/Bear/Trader/Judge/PM）独立执行反思，生成结构化的经验教训。
- **外部基准锚定 (External Anchor)**：审计强制引入 **SPY/QQQ 等基准对比**。
- **失败优先审计 (Failure-First Attribution)**：系统强制记录"逻辑完美但结果失败"的案例。通过分析 **Type II 错误（错失机会）** 与 **逻辑噪声**，对抗**幸存者偏差**，确保记忆库不只是盈利逻辑的堆砌，而是风险边界的集合。

### 5.2 分层记忆传承 (Layered Memory)
- **角色隔离的 BM25 记忆库**：系统为 Bull/Bear/Trader/Invest Judge/Portfolio Manager 五个角色建立相互隔离的 `FinancialSituationMemory`，每个角色只从自己的历史经验中学习，防止跨角色的逻辑污染。记忆以"情境 (Situation) - 建议 (Advice)"对的形式存储，通过 BM25 Okapi 算法做纯词汇匹配检索，无需 embedding API，纯本地计算，跨 LLM 提供商可用。
- **非对称记忆注入 (Asymmetric Injection)**：注入记忆时，历史警示 (Warning Logic) 权重显著高于成功经验。
- **时间衰减检索 (Time-Weighted Retrieval, TWR)**：引入 **记忆半衰期 (Memory Half-life)** 概念。系统利用指数衰减函数 $e^{-\lambda \cdot \Delta t}$ 对检索到的 BM25 分数进行重校准，确保"新鲜"逻辑拥有更高的决策权重，防止系统陷入"冷冻幻觉"。
- **宏观代际标签 (Macro Epoch Tags)**：记忆存储时强制附带 **宏观底色标签**（如 [High-Interest-Rate]、[QE-Era]）。在检索相似情境时，系统会执行 **跨代际环境校验 (Epoch Validation)**。若当前环境与记忆发生时的宏观逻辑存在底层冲突，该记忆将被标记为 `[DEPRECATED]`（已作废）或执行逻辑衰减。
- **逻辑漂移监测 (Logic Drift Monitoring)**：`Reflector` 实时监控置信度与实际盈亏的偏差走势。若偏差持续扩大，将自动触发 **"全局记忆清洗 (Memory Pruning)"**，剔除陈旧且无效的逻辑锚点。

### 5.3 闭环偏见修正与对称进化
人类对 Agent 的每一次纠偏，以及 Agent 对人类决策的非一致性预警，均量化为**"双向偏见得分"**。这些得分作为负向反馈，确保人机协作系统的持续进化。

---

## 结语：在不确定的海洋中，拥抱系统化的确定性复利
数字化资管机构的核心特征是：先谈输，再谈赢。敬畏市场，相信系统，拥抱复利。
