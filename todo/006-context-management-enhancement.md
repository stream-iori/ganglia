# Design & Plan: Context Management Enhancement (ganglia-harness)

> 对标 Claude Code 的上下文管理体系，系统性提升 Ganglia 的 context 效率、成本控制和稳定性。

---

## 1. 现状分析

### Ganglia 已有能力

- `ContextBudget`: 统一预算分配（system prompt 5%, history 80%, tool output 4% 等）
- `DefaultContextOptimizer`: 三级阈值（normal 70% → forced 3x → hard limit 4x）
- `ObservationCompressionHook`: 四策略 tool output 处理（SKIP / WRITE_TO_TMP / TRUNCATE_WITH_HINT / COMPRESS_AND_STORE）
- `ContextComposer`: 优先级裁剪 + 硬截断兜底
- `SessionContext.getPrunedHistory()`: 当前轮独立预算 + 历史轮原子级跳过
- `SessionTmpStore`: 大输出落盘 + 路径提示
- `TokenAwareTruncator`: 二分查找精确截断
- Running Summary: 增量摘要缓存，避免重复压缩

### 对标 Claude Code 后的关键差距

| #  |                                 差距                                  |           影响            | 难度 |
|----|---------------------------------------------------------------------|-------------------------|----|
| G1 | 无 Prompt Cache 感知 — system prompt 每轮重算，无 cacheable/volatile 区分      | API 成本高（cache miss 10x） | 中  |
| G2 | 压缩无分块自保护 — 待压缩内容超模型 limit 直接失败                                      | 长会话崩溃                   | 低  |
| G3 | 无 Tool Result 聚合预算 — 只有 per-message cap，无 per-turn aggregate budget | 多工具并发时 context 膨胀       | 中  |
| G4 | 无免费压缩层 — 缺少清除旧 thinking/tool 参数等零成本操作                               | 过早触发昂贵的 LLM 压缩          | 低  |
| G5 | 无预警阈值体系 — 只有一个 compressionThreshold，无 warning/error/blocking 分级     | 用户无感知，突然压缩              | 低  |
| G6 | ContextComposer 效率问题 — 每次 pruneOne 重算全部 tokens                      | 慢（O(n²) token 计算）       | 低  |
| G7 | 硬截断用字符估算 — `maxTokens * 4` 不精确                                      | 可能超出或浪费预算               | 低  |

---

## 2. 改进计划（6 个 Phase）

### Phase 1: 压缩分块自保护（G2）

> 防止压缩请求本身超出模型 context limit 导致会话崩溃。

**目标**: `DefaultContextOptimizer.compressSession()` 支持分块压缩和 PTL 重试。

#### 1.1 设计

```
compressSession(context, turnsToKeep)
  ├── toCompress = previousTurns[0..compressCount]
  ├── 估算 toCompress 的 token 数
  ├── if tokens > CompressionBudget.chunkingThreshold × utilityContextLimit:
  │     ├── 按 chunkSize 分组 turns → chunks
  │     ├── 逐 chunk 调用 compressor.compress()
  │     └── 合并各 chunk 摘要 → final summary
  ├── else:
  │     └── 单次 compressor.compress(toCompress)
  └── PTL 重试:
        ├── 如果 compress() 失败 (PromptTooLong):
        │     ├── 从 toCompress 头部丢弃 20% 的 turns
        │     ├── 重试（最多 3 次）
        │     └── 丢弃部分添加 "[earlier turns truncated]" 标记
        └── 3 次后仍失败: 降级为 TokenAwareTruncator 截断拼接
```

#### 1.2 改动文件

|               文件                |                        改动                        |
|---------------------------------|--------------------------------------------------|
| `DefaultContextOptimizer.java`  | `compressSession` 增加分块和 PTL 重试逻辑                 |
| `ContextCompressor.java` (port) | 新增 `compress(List<Turn>, int maxInputTokens)` 重载 |
| `CompressionBudget.java`        | 已有 `chunkingThreshold`/`chunkSize`，无需改动          |

#### 1.3 测试

**单元测试** (`DefaultContextOptimizerTest.java` 扩展):
- `chunkedCompression_splitsWhenExceedingThreshold`: 15 个大 turn，验证分块调用次数
- `chunkedCompression_mergesSummaries`: 验证多 chunk 摘要正确合并
- `ptlRetry_dropsOldestTurnsAndRetries`: 模拟 compress 首次抛 PTL，验证重试时 turn 减少
- `ptlRetry_maxRetriesExhausted_fallsBackToTruncation`: 3 次均失败，验证降级截断
- `ptlRetry_secondAttemptSucceeds`: 第一次失败第二次成功

**集成测试** (`ContextCompressionIT.java` 新建):
- 构造真实 SessionContext（50+ turns, 每 turn 500 tokens），配置小 contextLimit（8000），验证端到端压缩不崩溃
- 验证压缩后 token 数 < compressionTarget

---

### Phase 2: System Prompt 缓存与 Cache 感知（G1）

**目标**: 区分 cacheable/volatile fragments，缓存不变部分，减少 prompt cache miss。

#### 2.1 设计

```
ContextFragment 新增字段:
  cacheable: boolean  // true = 跨轮不变（persona, workflow, tools, mandates, guidelines）
                      // false = 每轮可变（memory, environment, skills, plan）

ContextComposer.compose() 新逻辑:
  1. 将 fragments 分为 stablePrefix（cacheable=true）和 volatileSuffix（cacheable=false）
  2. stablePrefix 按 priority 排序后拼接，结果缓存
  3. volatileSuffix 每轮重新拼接
  4. 最终 = cachedStablePrefix + "\n\n" + volatileSuffix

  关键: stable 部分的拼接顺序和内容跨轮保持一致 → prompt cache 前缀命中

StandardPromptEngine:
  - 增加 cachedStablePrompt: String 字段
  - 增加 stablePromptDirtyFlag: 仅在 tool/skill 注册变化时设 true
  - buildSystemPrompt() 中: 如果 dirty=false 且 cache 非 null，直接复用 stable 部分
```

#### 2.2 改动文件

|             文件              |                                      改动                                      |
|-----------------------------|------------------------------------------------------------------------------|
| `ContextFragment.java`      | 新增 `cacheable` 字段、工厂方法 `mandatoryCacheable()`、`prunableCacheable()`          |
| `ContextComposer.java`      | `compose()` 拆分 stable/volatile 拼接，增加 `composeStable()` + `composeVolatile()` |
| `StandardPromptEngine.java` | 缓存 stable prompt 结果，dirty flag 机制                                            |
| `PersonaContextSource.java` | fragment 标记 `cacheable=true`                                                 |
| `ToolContextSource.java`    | fragment 标记 `cacheable=true`                                                 |
| `MemoryContextSource.java`  | fragment 标记 `cacheable=false`                                                |
| `EnvironmentSource.java`    | fragment 标记 `cacheable=false`                                                |
| `SkillContextSource.java`   | fragment 标记 `cacheable=false`                                                |

#### 2.3 测试

**单元测试** (`ContextComposerTest.java` 扩展):
- `compose_stableFragmentsOrderedBeforeVolatile`: 验证 cacheable fragments 始终在前
- `compose_stablePrefixConsistentAcrossInvocations`: 相同 stable fragments 两次 compose 结果 byte-identical
- `compose_volatileChangeDoesNotAffectStablePrefix`: 修改 volatile fragment 后 stable 前缀不变
- `compose_pruningOnlyAffectsVolatileFragments`: 超预算时先裁剪 volatile

**单元测试** (`StandardPromptEngineTest.java` 新建):
- `buildSystemPrompt_cacheHitWhenStableUnchanged`: 第二次调用不重新收集 stable sources
- `buildSystemPrompt_cacheMissWhenToolRegistered`: 注册新 tool 后 dirty flag 触发重算
- `buildSystemPrompt_volatileFragmentsAlwaysRefreshed`: memory fragment 每轮更新

**集成测试** (`PromptCacheIT.java` 新建):
- 配置完整 PromptEngine + 多 ContextSource，连续 3 轮 buildSystemPrompt
- 验证 stable 前缀跨轮一致（String.equals 或 hash 比较）
- 验证 volatile 部分随 memory 变化更新

---

### Phase 3: Tool Result 聚合预算（G3）

**目标**: 对单轮内所有 tool results 做聚合预算控制，防止多工具并发时 context 膨胀。

#### 3.1 设计

```
新类 ToolResultBudgetEnforcer:
  - 在 StandardPromptEngine.prepareRequest() 中，capToolMessages() 之后执行
  - 输入: List<Message>（已 per-message cap 的历史）, aggregateBudget (int tokens)
  - 逻辑:
    1. 收集所有 TOOL role 的 message
    2. 计算总 token 数
    3. 如果总数 > aggregateBudget:
       a. 按 age 排序（最老的优先替换）
       b. 对最老的 TOOL message 做进一步截断（取前 N tokens 的 preview）
       c. 重复直到总数 ≤ aggregateBudget
  - 替换决策缓存: Map<String, String> (toolCallId → replacement)
    - 下一轮 prepareRequest 时，已缓存的替换直接复用（保证 prompt cache 稳定）

ContextBudget 新增:
  - toolOutputAggregate: 单轮所有 tool output 总预算 (20% of available, clamped 4000-80000)
```

#### 3.2 改动文件

|                  文件                  |               改动                |
|--------------------------------------|---------------------------------|
| `ContextBudget.java`                 | 新增 `toolOutputAggregate` 字段     |
| `ToolResultBudgetEnforcer.java` (新建) | 聚合预算执行 + 替换缓存                   |
| `StandardPromptEngine.java`          | `prepareRequest()` 中调用 enforcer |

#### 3.3 测试

**单元测试** (`ToolResultBudgetEnforcerTest.java` 新建):
- `enforce_withinBudget_noChange`: 总量未超限，消息不变
- `enforce_exceedsBudget_truncatesOldest`: 5 个 tool result 超限，最老的被截断
- `enforce_cachedReplacement_reusedNextTurn`: 第二轮调用复用缓存的替换
- `enforce_preservesRecentToolResults`: 最近 N 个 tool result 永不截断
- `enforce_mixedRoles_onlyAffectsToolMessages`: USER/ASSISTANT 消息不受影响

**集成测试** (`ToolResultBudgetIT.java` 新建):
- 构造 SessionContext 包含 10 个大 tool result（每个 3000 tokens），aggregateBudget=10000
- 验证 enforcer 后总 tool tokens ≤ 10000
- 验证最近 3 个 tool result 内容完整

---

### Phase 4: 免费压缩层 — 中间步骤瘦身（G4）

**目标**: 在触发 LLM 压缩之前，先执行零成本的上下文瘦身操作。

#### 4.1 设计

```
新类 ContextSlimmer (在 DefaultContextOptimizer 中调用):
  - slimIfNeeded(SessionContext) → SessionContext
  - 三步瘦身:
    1. stripOldThinkingBlocks():
       - 对 previousTurns（除最近 1 轮外）中的 ASSISTANT messages
       - 移除 <think>...</think> 或 thinking content blocks
       - 保留 action/tool_use 部分
    2. compactOldToolCallArgs():
       - 对 previousTurns（除最近 2 轮外）中的 ASSISTANT messages
       - 将 toolCalls 的 arguments 替换为摘要 "{toolName}({argKeys})"
       - 保留 tool result（TOOL message）不动
    3. deduplicateSystemMessages():
       - 如果多个 summary turns 存在，合并为一个

执行时机:
  DefaultContextOptimizer.optimizeIfNeeded():
    1. 计算 totalTokens
    2. if totalTokens > limit * threshold:
       a. 先调用 ContextSlimmer.slimIfNeeded()
       b. 重新计算 totalTokens
       c. 如果仍超限 → 继续 LLM 压缩
       d. 如果已降到阈值以下 → 跳过 LLM 压缩（省了一次 API 调用）
```

#### 4.2 改动文件

|               文件               |                          改动                           |
|--------------------------------|-------------------------------------------------------|
| `ContextSlimmer.java` (新建)     | 三步瘦身逻辑                                                |
| `DefaultContextOptimizer.java` | `optimizeIfNeeded()` 中先调用 slimmer                     |
| `Message.java`                 | 可能需要 `withContent(String)` 或 `withToolCalls(List)` 方法 |
| `Turn.java`                    | 可能需要 `withIntermediateSteps(List<Message>)` 方法        |

#### 4.3 测试

**单元测试** (`ContextSlimmerTest.java` 新建):
- `stripThinking_removesThinkBlocksFromOldTurns`: 验证旧轮 `<think>` 内容被移除
- `stripThinking_preservesCurrentTurn`: 当前轮 thinking 不动
- `stripThinking_preservesNonThinkingContent`: ASSISTANT 的 text content 保留
- `compactToolArgs_replacesWithSummary`: 旧轮 tool call args 被替换为摘要
- `compactToolArgs_preservesRecentTurns`: 最近 2 轮的 tool call args 完整保留
- `deduplicateSummaries_mergesMultipleSummaryTurns`: 2 个 summary turn 合并为 1 个
- `slimIfNeeded_reducesTokenCount`: 验证 slim 后 token 数显著下降
- `slimIfNeeded_noOpWhenNoThinkingOrOldArgs`: 无 thinking 无旧参数时原样返回

**单元测试** (`DefaultContextOptimizerTest.java` 扩展):
- `optimizeIfNeeded_slimmerAvoidsLLMCompression`: slim 后低于阈值，验证 compressor 未调用
- `optimizeIfNeeded_slimmerInsufficientThenCompresses`: slim 后仍超限，验证 compressor 被调用

---

### Phase 5: 预警阈值体系（G5）

**目标**: 建立分级阈值，让上层应用感知 context 状态。

#### 5.1 设计

```
新类 ContextPressureMonitor:
  - 四级阈值（基于 ContextBudget.history()）:
    - NORMAL: < 60%
    - WARNING: 60% - 80%（通知上层，UI 可展示黄色指示）
    - CRITICAL: 80% - 95%（触发 slim + 压缩）
    - BLOCKING: > 95%（必须压缩后才能继续）

  - evaluate(SessionContext) → ContextPressure record:
    - level: NORMAL/WARNING/CRITICAL/BLOCKING
    - usedTokens: int
    - budgetTokens: int
    - percentUsed: double

  - 通过 ObservationDispatcher 发布 CONTEXT_PRESSURE_CHANGED 事件

集成点:
  - ReActAgentLoop: 每轮 reason() 前调用 evaluate()
  - 如果 BLOCKING: 强制 optimizeIfNeeded() 并等待完成后再 reason
  - 如果 WARNING/CRITICAL: dispatch event 让 UI 展示状态
```

#### 5.2 改动文件

|                 文件                 |                          改动                          |
|------------------------------------|------------------------------------------------------|
| `ContextPressureMonitor.java` (新建) | 评估逻辑 + 阈值常量                                          |
| `ContextPressure.java` (新建)        | record: level, usedTokens, budgetTokens, percentUsed |
| `ObservationType.java`             | 新增 `CONTEXT_PRESSURE_CHANGED`                        |
| `ReActAgentLoop.java`              | 每轮调用 monitor.evaluate()，dispatch 事件                  |
| `GangliaKernel.java`               | 组装 monitor                                           |

#### 5.3 测试

**单元测试** (`ContextPressureMonitorTest.java` 新建):
- `evaluate_normalWhenUnder60Percent`: 低使用率返回 NORMAL
- `evaluate_warningAt65Percent`: 返回 WARNING
- `evaluate_criticalAt85Percent`: 返回 CRITICAL
- `evaluate_blockingAt96Percent`: 返回 BLOCKING
- `evaluate_emptyContext_returnsNormal`: 空 session 返回 NORMAL

**集成测试** (`ContextPressureIT.java` 新建):
- 逐步添加 message 到 SessionContext，验证 pressure level 递进变化
- 验证 BLOCKING 级别触发压缩后回落到 NORMAL/WARNING

---

### Phase 6: ContextComposer 性能优化（G6 + G7）

**目标**: 消除 O(n²) token 重算，修复硬截断字符估算。

#### 6.1 设计

```
ContextComposer 优化:
  1. 增量 token 计算:
     - 每个 ContextFragment 预计算自身 token 数（含 "## {name}\n" 前缀和 "\n\n" 分隔符）
     - 全局 overhead = 所有 fragment 间分隔符
     - pruneOne 时: totalTokens -= victim.cachedTokens（O(1) 而非 O(n)）

  2. 精确硬截断:
     - 替换 `result.substring(0, maxTokens * 4)` 为 TokenAwareTruncator 二分查找
     - 复用已有的 TokenAwareTruncator 实现

ContextFragment 扩展:
  - transient cachedTokens: int（由 Composer 设置，不序列化）
  - 或在 Composer 内部维护 Map<ContextFragment, Integer>
```

#### 6.2 改动文件

|           文件           |                  改动                  |
|------------------------|--------------------------------------|
| `ContextComposer.java` | 增量 token 计算 + TokenAwareTruncator 兜底 |

#### 6.3 测试

**单元测试** (`ContextComposerTest.java` 扩展):
- `compose_incrementalTokenCountMatchesFullRecount`: 增量计算与全量一致
- `compose_pruneOnePerformance_noFullRecount`: mock tokenCounter 验证调用次数 = fragments 数（非 fragments²）
- `compose_hardTruncateUsesTokenAwareTruncator`: mandatory 超限时精确截断
- `compose_hardTruncatePreservesValidContent`: 截断后内容不会在 token 中间断开

---

## 3. 依赖关系与实施顺序

```
Phase 1 (压缩自保护)  ──┐
                        ├──→ Phase 4 (免费压缩层，依赖 Phase 1 的 optimizer 改动)
Phase 6 (Composer 优化) ──┘     │
                                ↓
Phase 2 (Prompt Cache) ───→ Phase 3 (聚合预算，依赖 Phase 2 的 ContextBudget 扩展)
                                │
                                ↓
                          Phase 5 (预警体系，依赖 Phase 1+4 的 optimizer 最终形态)
```

**推荐实施顺序**: Phase 1 → Phase 6 → Phase 4 → Phase 2 → Phase 3 → Phase 5

---

## 4. 测试汇总

### 单元测试（共 ~35 个新 test case）

|                 测试类                 | 新增 case 数 | Phase  |
|-------------------------------------|-----------|--------|
| `DefaultContextOptimizerTest`       | +7        | P1, P4 |
| `ContextComposerTest`               | +7        | P2, P6 |
| `StandardPromptEngineTest` (新建)     | +3        | P2     |
| `ToolResultBudgetEnforcerTest` (新建) | +5        | P3     |
| `ContextSlimmerTest` (新建)           | +8        | P4     |
| `ContextPressureMonitorTest` (新建)   | +5        | P5     |

### 集成测试（共 5 个新 IT 类）

|           测试类            |                         验证点                          | Phase |
|--------------------------|------------------------------------------------------|-------|
| `ContextCompressionIT`   | 大会话端到端压缩不崩溃                                          | P1    |
| `PromptCacheIT`          | stable 前缀跨轮一致性                                       | P2    |
| `ToolResultBudgetIT`     | 聚合预算执行 + 最近结果保护                                      | P3    |
| `ContextPressureIT`      | 压力等级递进 + 压缩回落                                        | P5    |
| `ContextManagementE2EIT` | 全链路: slim → compress → cache stable → budget enforce | All   |

### 测试基础设施

需要以下 test fixture:
- `TestSessionContextBuilder`: 快速构造包含 N 个 turn、每 turn M 条 message 的 SessionContext
- `StubTokenCounter`: 固定 "1 char = 1 token" 的测试用 counter（已有类似实现可复用）
- `StubContextCompressor`: 返回固定摘要的 mock compressor
- `CapturingObservationDispatcher`: 捕获 dispatch 事件用于断言

---

## 5. 风险与缓解

|                     风险                      |                           缓解                            |
|---------------------------------------------|---------------------------------------------------------|
| Prompt cache 稳定性很难验证（需要 API 侧 cache hit 统计） | 用 String.equals 验证前缀一致性；上线后监控 `cache_read_input_tokens` |
| ContextSlimmer 误删有用内容                       | 保守策略：只处理明确的 `<think>` 标签和旧轮 tool args；最近 2 轮永不 slim     |
| 分块压缩摘要质量低于单次压缩                              | 最终合并步骤中再做一次概要提炼；可 A/B 对比                                |
| 聚合预算替换缓存内存增长                                | 替换缓存按 session 生命周期管理，session 结束时清理                      |
| Phase 2 改动涉及多个 ContextSource                | 向后兼容：`cacheable` 默认 false，现有 source 不受影响                |

---

## 6. 不做的事情（已评估但搁置）

|                           项目                            |                       原因                        |
|---------------------------------------------------------|-------------------------------------------------|
| API 原生 context editing (clear_tool_uses/clear_thinking) | 依赖 Anthropic API 特定功能，Ganglia 需支持多 LLM provider |
| Tool result 替换的 frozen/fresh 三分区                        | 过于复杂，先用简单的 age-based 策略，效果不好再迭代                 |
| 用户级 /compact 命令                                         | 属于 UI 层功能，不在 harness 范围内                        |
| Session memory compaction (跨 session 记忆压缩)              | 已有 MemorySystem 处理，不重复                          |

