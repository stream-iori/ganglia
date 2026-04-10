# Trading Agent 能力实现清单

> 基于 `multi_agent_system_architecture_design.md` 和 `trading_agents_domain_knowledge_whitepaper.md` 的需求，
> 对照 `ganglia-harness` 现有能力，梳理 `ganglia-trading` 模块需要实现的具体能力。

---

## 模块结构

```
trading-agent/ganglia-trading/
└── src/main/java/work/ganglia/trading/
    ├── TradingAgentBuilder.java          ✅ 已创建 (参照 CodingAgentBuilder)
    ├── config/
    │   └── TradingConfig.java            ✅ 已创建 (领域配置)
    ├── prompt/                           ✅ 已创建 (3 个 ContextSource)
    ├── tool/                             🔲 待实现 (金融数据工具集)
    ├── debate/                           🔲 待实现 (对抗辩论引擎)
    ├── memory/                           🔲 待实现 (角色隔离记忆)
    └── signal/                           🔲 待实现 (信号提取)
```

---

## 一、可直接复用 Harness 的能力 (不需要在 trading 模块写代码)

| 能力 | Harness 组件 | 对应架构层 |
|------|-------------|-----------|
| 4 Analyst 并行执行 | `TaskGraph` + 4 个无依赖 `TaskNode` | 感知层 |
| Analyst 感官隔离 | `IsolationLevel.SESSION` + `ContextScoper` | 感知层 |
| 各 Analyst 角色注入 | `TaskNode.persona` + `SubAgentContextSource` | 感知层 |
| 依赖输出传递 (Analyst→Researcher) | `TaskNode.inputMapping` | 研究层 |
| Trader←Research Plan 依赖 | `TaskNode.dependencies` | 执行层 |
| 演化循环外框 | `CyclicManagerEngine` | 演化层 |
| 跨周期事实存储 | `Blackboard` (Fact ACTIVE→SUPERSEDED→ARCHIVED) | 演化层 |
| 收敛/停滞检测 | `DefaultTerminationController` | 演化层 |
| LLM 多厂商 + 重试/回退 | `RetryingModelGateway` / `FallbackModelGateway` | 基础设施 |
| 全链路观测 | `ObservationDispatcher` + Span Hierarchy | 运维 |
| 上下文压缩 | `ContextCompressor` (chunk/reflect/extractKeyFacts) | 运维 |

---

## 二、需要在 `ganglia-trading` 模块实现的能力

### 2.1 金融数据工具集 (`tool/`)

**对应架构层**: 数据基础设施 + 感知层

| 工具 | 用途 | 使用者 | 优先级 |
|------|------|--------|--------|
| `MarketDataTools` | OHLCV 行情 + 技术指标 (SMA/EMA/MACD/RSI/BB/ATR/VWMA/MFI) | Market Analyst | P0 |
| `FundamentalsTools` | 财务三表 + 综合指标 (PE/EPS/市值) | Fundamentals Analyst | P0 |
| `NewsTools` | 全球宏观新闻 + 公司新闻 + 内幕交易 | News Analyst | P0 |
| `SocialSentimentTools` | 社交媒体情绪与提及量 | Social Analyst | P1 |

**每个 ToolSet 内部需实现**:
- **VendorRouter**: yfinance / Alpha Vantage 双引擎路由，工具级别配置覆盖
- **VendorFallback**: Rate Limit 时自动回退到备用 vendor
- **LookAheadBiasGuard**: 所有历史数据按 `curr_date` 过滤，零容忍
- **RollingCache**: 行情数据 `{symbol}-{vendor}-{start}-{end}.csv` 本地缓存
- **ExponentialBackoff**: 数据拉取重试 (3次, 2s/4s/8s)

```java
// 核心接口设计
public interface DataVendor {
    Future<JsonObject> fetchOhlcv(String symbol, LocalDate start, LocalDate end);
    Future<JsonObject> fetchFundamentals(String symbol);
    Future<JsonArray> fetchNews(String query, int lookbackDays);
}

public class VendorRouter {
    Future<JsonObject> route(String toolName, DataVendor primary, DataVendor fallback);
}
```

---

### 2.2 对抗辩论引擎 (`debate/`)

**对应架构层**: 研究炼金层 + 风险审计层

这是 **最大的 Gap** — Harness 的 TaskGraph 是 DAG (无环)，无法原生支持多轮对话。

| 组件 | 职责 | 优先级 |
|------|------|--------|
| `DebateEngine` | 编排 N 轮 Bull↔Bear 或 Agg↔Neu↔Con 辩论循环 | P0 |
| `DebateRound` | 单轮辩论数据结构 (立场/论点/反驳/证据引用) | P0 |
| `DebateConfig` | 轮数上限、提前终止条件、评判标准 | P0 |
| `DebateJudge` | 综合双方论点、产出裁决 (Research Manager / PM 角色) | P0 |

**设计思路**: 不改 Harness，在 trading 层实现辩论循环

```
方案: DebateEngine 包装 ReActAgentLoop

DebateEngine.run(topic, participants, maxRounds):
  for round in 1..maxRounds:
    for participant in participants:
      // 注入: 对方上轮论点 + 历史记忆 + 全部分析报告
      context = buildIsolatedContext(participant, round, opponentArgs)
      result = agentLoop.run(context)  // 单次 ReAct 调用
      record(participant, round, result)
    if judge.shouldTerminate(allArgs): break
  return judge.synthesize(allArgs)
```

**两处复用**:
1. **研究辩论**: Bull vs Bear → Research Manager 裁决 → 产出 Investment Plan
2. **风控辩论**: Aggressive vs Neutral vs Conservative → PM 终审

---

### 2.3 角色隔离记忆 (`memory/`)

**对应架构层**: 演化与记忆闭环

| 组件 | 职责 | 优先级 |
|------|------|--------|
| `RoleIsolatedMemoryStore` | 按角色 (Bull/Bear/Trader/Judge/PM) 分区的 BM25 记忆库 | P0 |
| `SituationAdvicePair` | 存储 "情境-建议" 对 (Situation + Advice + Metadata) | P0 |
| `TimeWeightedRetrieval` | BM25 分数 × 时间衰减因子 e^(-λΔt) 归一化到 [0,1] | P1 |
| `EpochTagValidator` | 宏观代际标签 ([QE-Era], [High-Interest-Rate]) + 跨代际校验 | P2 |
| `AsymmetricInjector` | 注入时 Warning 权重 > Success 权重 (token 分配不对称) | P1 |

**与 Harness 的关系**:
- Harness 的 `MemorySystem` SPI 定义了 `MemoryStore` 接口
- `ganglia-local-file-memory` 提供了 `FileSystemMemoryStore` (全局共享)
- Trading 模块需要 **装饰或扩展** 此接口，增加 `roleId` 维度

```java
// 核心接口
public interface RoleMemory {
    Future<Void> store(String roleId, SituationAdvicePair pair);
    Future<List<SituationAdvicePair>> recall(String roleId, String query, int topK);
}

public record SituationAdvicePair(
    String situation,
    String advice,
    double confidence,
    LocalDate timestamp,
    List<String> epochTags,  // [QE-Era], [High-Rate]
    boolean isWarning        // true = failure lesson
) {}
```

---

### 2.4 结构化反思器 (`memory/` 或独立)

**对应架构层**: 演化层 Reflector

| 组件 | 职责 | 优先级 |
|------|------|--------|
| `StructuredReflector` | 四步法: Reasoning → Improvement → Summary → Query | P1 |
| `ReflectionResult` | 结构化反思输出 (含角色归因、confidence 校准) | P1 |

**与 Harness 的关系**:
- Harness 有 `ContextCompressor.reflect()` 和 `extractKeyFacts()` — 通用文本反思
- Trading 需要 **领域特化** 的四步法，每步有特定 prompt 模板
- 反思结果写入 `RoleIsolatedMemoryStore` (不是通用 MemoryStore)

```
Reflector.reflect(roleId, decision, outcome, marketContext):
  1. Reasoning: "为什么做出这个决策? 依据了哪些信号?"
  2. Improvement: "如果重来,哪里可以改进? 哪些信号被忽略了?"
  3. Summary: "一句话总结本次教训"
  4. Query: "生成检索 query,用于未来遇到类似情境时召回"
  → store(roleId, SituationAdvicePair{situation=query, advice=summary, ...})
```

---

### 2.5 信号处理器 (`signal/`)

**对应架构层**: 执行层 Signal Processor

| 组件 | 职责 | 优先级 |
|------|------|--------|
| `SignalProcessor` | 从 PM 非结构化输出中提取标准化 5 级信号 | P0 |
| `TradingSignal` | BUY / OVERWEIGHT / HOLD / UNDERWEIGHT / SELL + confidence | P0 |

**实现**: 简单 — 用 `quick_think_llm` 做一次结构化提取

```java
public record TradingSignal(
    SignalLevel level,    // BUY, OVERWEIGHT, HOLD, UNDERWEIGHT, SELL
    double confidence,    // 0.0 - 1.0
    String rationale      // one-line reason
) {
    public enum SignalLevel { BUY, OVERWEIGHT, HOLD, UNDERWEIGHT, SELL }
}
```

---

## 三、需要扩展 Harness 的能力 (可能需要改 harness 代码)

| 扩展点 | 原因 | 改动范围 |
|--------|------|---------|
| `Blackboard.Fact` 增加 `stance` 属性 | 辩论中需要区分 Bull/Bear 立场的 Fact | 小改: 加 field |
| `TradingConfig` 集成到 `GangliaConfig` | 领域配置需要与全局配置联动 | 中改: 配置扩展点 |
| `MemoryStore` 增加 `namespace/roleId` 过滤 | 角色隔离记忆的基础 | 中改: 接口扩展 |

---

## 四、实现优先级路线图

```
Phase 1 (MVP - 端到端可跑):
  ├── MarketDataTools (至少 yfinance)          ← 没数据什么都做不了
  ├── FundamentalsTools
  ├── NewsTools
  ├── DebateEngine (Bull/Bear 2 人辩论)        ← 核心差异化能力
  ├── SignalProcessor                          ← 输出归一化
  └── TaskGraph 编排 (全流程串联)

Phase 2 (记忆与演化):
  ├── RoleIsolatedMemoryStore
  ├── SituationAdvicePair 存储
  ├── StructuredReflector 四步法
  ├── TimeWeightedRetrieval
  └── AsymmetricInjector

Phase 3 (鲁棒性):
  ├── VendorRouter + Fallback
  ├── LookAheadBiasGuard
  ├── RollingCache
  ├── 3-agent Risk Debate (Agg/Neu/Con)
  ├── EpochTagValidator
  └── Logic Drift Monitoring
```

---

## 五、与 coding-agent 的对称性对比

| 维度 | ganglia-coding | ganglia-trading |
|------|---------------|-----------------|
| 核心 Builder | `CodingAgentBuilder` | `TradingAgentBuilder` |
| 领域配置 | 无 (harness 默认够用) | `TradingConfig` (风格/轮数/语言/vendor) |
| 领域工具 | `FileEditTools` (3 tools) | `MarketData/Fundamentals/News/Social` (4 ToolSets) |
| 领域 Prompt | Persona + Workflow + Mandates | Persona + Workflow + Mandates (trading 版) |
| 编排模式 | 单 Agent ReAct (偶尔 SubAgent) | **多 Agent 流水线** + 辩论循环 |
| 记忆模式 | 全局共享 | **角色隔离** + TWR + Asymmetric |
| 后处理 | 无 | `SignalProcessor` (结构化提取) |
| 反思模式 | 通用 reflect/extractKeyFacts | **四步法** per-role 反思 |
| 指令文件 | CODING.md | TRADING.md (待定) |

**核心差异**: coding-agent 是 **单 Agent + 工具** 模式；trading-agent 是 **多 Agent 协作 + 对抗博弈** 模式。
后者需要的不仅是领域工具，更是一套 **编排范式** (辩论引擎) 的创新。
