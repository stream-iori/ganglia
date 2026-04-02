# Context Manager 改进计划

> 解决三个待改进项：Metadata 类型安全、EventPublisher 采用、职责重叠统一

**状态：✅ 已完成 (2026-04-02)**

---

## 改进项概览

| # | 改进项 | 工作量 | 风险 | 优先级 | 状态 |
|---|--------|--------|------|--------|------|
| 1 | SessionContext Metadata 类型安全 | 1-2 天 | 中 | 高 | ✅ 完成 |
| 2 | ContextEventPublisher 采用 | 0.5 天 | 低 | 中 | ✅ 完成 |
| 3 | TimeBasedMicrocompact 与 ContextSlimmer 统一 | 1 天 | 中 | 中 | ✅ 完成 |

---

## 改进项 1: SessionContext Metadata 类型安全

### 现状分析

**已有基础**：
- `CompressionState` record 已存在 (`work.ganglia.port.chat.CompressionState`)
- `RecentlyReadFile` record 已存在 (`work.ganglia.port.chat.RecentlyReadFile`)
- 提供完整的 with 方法：`withRunningSummary()`, `withFailure()`, `resetFailures()` 等

**问题**：
- `SessionContext` 仍使用 `Map<String, Object> metadata` 存储压缩状态
- 调用点分散在 6 个文件中，类型不安全

### 实施步骤

#### Step 1.1: 添加 compressionState 字段到 SessionContext

```java
// SessionContext.java
public record SessionContext(
    String sessionId,
    List<Turn> previousTurns,
    Turn currentTurn,
    Map<String, Object> metadata,        // 保留用于其他用途
    List<String> activeSkillIds,
    ModelOptions modelOptions,
    CompressionState compressionState    // 新增显式字段
) {
  // Compact constructor 添加默认值
  public SessionContext {
    if (compressionState == null) {
      compressionState = CompressionState.empty();
    }
    // ... 其他 null 检查
  }
}
```

#### Step 1.2: 添加便捷方法（向后兼容）

```java
// SessionContext.java - 新方法委托给 compressionState
public CompressionState compressionState() {
  return compressionState;
}

public SessionContext withCompressionState(CompressionState state) {
  return new SessionContext(sessionId, previousTurns, currentTurn, metadata,
                            activeSkillIds, modelOptions, state);
}

// 保留旧方法作为别名（标记 @Deprecated）
@Deprecated(since = "0.3.0", forRemoval = true)
public String getRunningSummary() {
  return compressionState.runningSummary();
}

@Deprecated(since = "0.3.0", forRemoval = true)
public SessionContext withRunningSummary(String s) {
  return withCompressionState(compressionState.withRunningSummary(s));
}

@Deprecated(since = "0.3.0", forRemoval = true)
public int getConsecutiveCompressionFailures() {
  return compressionState.consecutiveFailures();
}

@Deprecated(since = "0.3.0", forRemoval = true)
public SessionContext withCompressionFailure() {
  return withCompressionState(compressionState.withFailure());
}

@Deprecated(since = "0.3.0", forRemoval = true)
public SessionContext resetCompressionFailures() {
  return withCompressionState(compressionState.resetFailures());
}

@Deprecated(since = "0.3.0", forRemoval = true)
public boolean hasValidRunningSummary() {
  return compressionState.hasValidRunningSummary();
}
```

#### Step 1.3: 更新调用点

| 文件 | 当前调用 | 改为 |
|------|---------|------|
| `CompressionStep.java` | `context.getRunningSummary()` | `context.compressionState().runningSummary()` |
| `CompressionStep.java` | `context.withRunningSummary()` | `context.withCompressionState()` |
| `ReActAgentLoop.java` | `context.getRunningSummary()` | `context.compressionState().runningSummary()` |
| `DefaultContextOptimizerTest.java` | `context.getRunningSummary()` | `context.compressionState().runningSummary()` |

#### Step 1.4: 序列化兼容性处理

```java
// SessionContext.java - Jackson 注解确保 JSON 序列化兼容
@JsonCreator
public static SessionContext fromJson(
    @JsonProperty("sessionId") String sessionId,
    @JsonProperty("previousTurns") List<Turn> previousTurns,
    @JsonProperty("currentTurn") Turn currentTurn,
    @JsonProperty("metadata") Map<String, Object> metadata,
    @JsonProperty("activeSkillIds") List<String> activeSkillIds,
    @JsonProperty("modelOptions") ModelOptions modelOptions,
    @JsonProperty("compressionState") CompressionState compressionState
) {
  // 从 metadata 提取压缩状态（兼容旧数据）
  if (compressionState == null && metadata != null) {
    compressionState = extractCompressionState(metadata);
  }
  return new SessionContext(sessionId, previousTurns, currentTurn,
                            metadata, activeSkillIds, modelOptions, compressionState);
}

private static CompressionState extractCompressionState(Map<String, Object> metadata) {
  String runningSummary = (String) metadata.get("runningSummary");
  Integer failures = (Integer) metadata.get("compressionFailures");
  Long timestamp = (Long) metadata.get("lastAssistantTimestamp");
  // ... 构建 CompressionState
}
```

### 测试计划

```java
// SessionContextTest.java
@Test void compressionState_defaultValue_isEmpty() {
  SessionContext ctx = new SessionContext("test", List.of(), null, Map.of(), List.of(), null);
  assertThat(ctx.compressionState()).isEqualTo(CompressionState.empty());
}

@Test void deprecatedMethods_delegateToCompressionState() {
  SessionContext ctx = SessionContext.create("test");
  ctx = ctx.withRunningSummary("summary");
  assertThat(ctx.getRunningSummary()).isEqualTo("summary");
  assertThat(ctx.compressionState().runningSummary()).isEqualTo("summary");
}

@Test void serialization_backwardsCompatible() {
  // 旧格式 JSON (只有 metadata) 能正确反序列化
  String oldJson = """
    {"sessionId":"test","metadata":{"runningSummary":"old summary"}}
    """;
  SessionContext ctx = objectMapper.readValue(oldJson, SessionContext.class);
  assertThat(ctx.compressionState().runningSummary()).isEqualTo("old summary");
}
```

### 改动文件清单

| 文件 | 动作 |
|------|------|
| `SessionContext.java` | 添加 compressionState 字段、便捷方法、序列化兼容 |
| `CompressionStep.java` | 更新调用点 |
| `ReActAgentLoop.java` | 更新调用点 |
| `DefaultContextOptimizerTest.java` | 更新测试 |
| `SessionContextTest.java` | 新增测试 |

---

## 改进项 2: ContextEventPublisher 采用

### 现状分析

**已有基础**：
- `ContextEventPublisher` 接口已定义
- `DefaultContextEventPublisher` 实现已完成

**问题**：
以下组件仍直接调用 `dispatcher.dispatch()`：

| 组件 | 事件类型 |
|------|---------|
| `CompressionStep` | CONTEXT_COMPRESSED, SYSTEM_EVENT |
| `ContextPressureMonitor` | CONTEXT_PRESSURE_CHANGED |
| `StandardPromptEngine` | PROMPT_CACHE_STATS, CONTEXT_BUDGET_ALLOCATED, CONTEXT_ANALYSIS |
| `HardLimitGuardStep` | ERROR |

### 实施步骤

#### Step 2.1: 在 CompressionStep 中使用 ContextEventPublisher

```java
// CompressionStep.java
public class CompressionStep implements ContextOptimizationStep {
  private final ContextEventPublisher eventPublisher;  // 替代 ObservationDispatcher

  public CompressionStep(..., ContextEventPublisher eventPublisher) {
    this.eventPublisher = eventPublisher;
    // ...
  }

  @Override
  public Future<OptimizationResult> apply(SessionContext context, OptimizationContext optContext) {
    // 替换
    // dispatcher.dispatch(sessionId, ObservationType.CONTEXT_COMPRESSED, ...);
    // 为
    eventPublisher.publishCompressionStarted(context.sessionId(), optContext.currentTokens());

    return compressSession(...)
        .map(result -> {
          eventPublisher.publishCompressionFinished(
              context.sessionId(), optContext.currentTokens(), afterTokens, true);
          return OptimizationResult.changed(result, tokensSaved);
        });
  }
}
```

#### Step 2.2: 在 ContextPressureMonitor 中使用 ContextEventPublisher

```java
// ContextPressureMonitor.java
public class ContextPressureMonitor {
  private final ContextEventPublisher eventPublisher;

  public ContextPressureMonitor(ContextBudget budget, TokenCounter tokenCounter,
                                ContextEventPublisher eventPublisher) {
    this.eventPublisher = eventPublisher;
    // ...
  }

  public ContextPressure evaluateAndNotify(SessionContext context) {
    ContextPressure pressure = evaluate(context);
    if (pressure.level() != lastEmittedLevel) {
      lastEmittedLevel = pressure.level();
      eventPublisher.publishPressureChanged(
          context.sessionId(),
          pressure.level().name(),
          pressure.usedTokens(),
          pressure.budgetTokens());
    }
    return pressure;
  }
}
```

#### Step 2.3: 在 StandardPromptEngine 中使用 ContextEventPublisher

```java
// StandardPromptEngine.java
public class StandardPromptEngine implements PromptEngine {
  private final ContextEventPublisher eventPublisher;

  // 替换三处 dispatch 调用
  // 1. publishPromptCacheStats
  // 2. publishBudgetAllocated
  // 3. publishContextAnalysis
}
```

#### Step 2.4: 在 DefaultContextOptimizer 中组装

```java
// DefaultContextOptimizer.java
public DefaultContextOptimizer(...) {
  ContextEventPublisher eventPublisher = new DefaultContextEventPublisher(dispatcher);

  this.compressionStep = new CompressionStep(..., eventPublisher);
  // ...
}
```

### 测试计划

```java
// CompressionStepTest.java
@Test void usesEventPublisher_forCompressionEvents() {
  ContextEventPublisher mockPublisher = mock(ContextEventPublisher.class);
  CompressionStep step = new CompressionStep(..., mockPublisher);

  step.apply(context, optContext);

  verify(mockPublisher).publishCompressionStarted(eq("session"), anyInt());
  verify(mockPublisher).publishCompressionFinished(eq("session"), anyInt(), anyInt(), eq(true));
}
```

### 改动文件清单

| 文件 | 动作 |
|------|------|
| `CompressionStep.java` | 注入 ContextEventPublisher，替换 dispatch 调用 |
| `ContextPressureMonitor.java` | 注入 ContextEventPublisher，替换 dispatch 调用 |
| `StandardPromptEngine.java` | 注入 ContextEventPublisher，替换 dispatch 调用 |
| `HardLimitGuardStep.java` | 注入 ContextEventPublisher（可选，ERROR 事件不匹配当前接口） |
| `DefaultContextOptimizer.java` | 创建并注入 ContextEventPublisher |
| `GangliaKernel.java` | 更新组装逻辑 |
| `CompressionStepTest.java` | 新增测试 |

---

## 改进项 3: TimeBasedMicrocompact 与 ContextSlimmer 统一

### 现状分析

**TimeBasedMicrocompact**：
- 基于时间间隙（gap since last assistant）触发
- 配置：`gapThresholdMinutes`, `keepRecent`
- 只清除特定类型工具：read_file, bash, grep, glob, web_fetch, web_search
- 在 Pipeline 中作为 `TimeBasedMicrocompactStep` 执行

**ContextSlimmer.slimOldToolResults()**：
- 基于 cache TTL（maxAgeMs）触发
- 清除所有 tool results（不区分类型）
- 在 `ReActAgentLoop` 中直接调用

**重叠**：
- 两者目的相同：清除旧的 tool results 以节省 tokens
- 触发条件略有不同：时间间隙 vs cache TTL
- 工具过滤策略不同：特定类型 vs 全部

### 设计方案

**统一为 `ToolResultCompactor`**，支持两种策略：

```java
public class ToolResultCompactor {

  /**
   * 清除策略
   */
  public enum Strategy {
    /** 基于时间间隙：距离上次 assistant 消息超过阈值 */
    TIME_GAP,
    /** 基于 cache TTL：消息年龄超过阈值 */
    CACHE_TTL
  }

  /**
   * 根据策略清除旧的 tool results
   *
   * @param context 会话上下文
   * @param strategy 清除策略
   * @param threshold 阈值（TIME_GAP: 分钟, CACHE_TTL: 毫秒）
   * @param keepRecent 保留最近 N 个 tool results
   * @param toolFilter 工具过滤器（可选）
   */
  public SessionContext compact(
      SessionContext context,
      Strategy strategy,
      long threshold,
      int keepRecent,
      Set<String> toolFilter) {
    // 统一实现
  }
}
```

### 实施步骤

#### Step 3.1: 创建 ToolResultCompactor

```java
// ToolResultCompactor.java (新建)
package work.ganglia.infrastructure.internal.state;

public class ToolResultCompactor {
  private static final Logger logger = LoggerFactory.getLogger(ToolResultCompactor.class);

  /** 默认可压缩的工具类型 */
  public static final Set<String> DEFAULT_COMPACTABLE_TOOLS =
      Set.of("read_file", "read", "bash", "grep", "glob", "web_fetch", "web_search");

  /**
   * 基于时间间隙清除（替代 TimeBasedMicrocompact）
   */
  public SessionContext compactByTimeGap(
      SessionContext context,
      long gapThresholdMinutes,
      int keepRecent,
      Set<String> toolFilter) {
    // 从 TimeBasedMicrocompact.compactIfNeeded 迁移逻辑
  }

  /**
   * 基于 cache TTL 清除（替代 ContextSlimmer.slimOldToolResults）
   */
  public SessionContext compactByCacheTtl(
      SessionContext context,
      long maxAgeMs,
      int keepRecent,
      Set<String> toolFilter) {
    // 从 ContextSlimmer.slimOldToolResults 迁移逻辑
  }

  // 内部方法复用
  private SessionContext compactInternal(SessionContext context,
      Predicate<Message> shouldCompact, int keepRecent, Set<String> toolFilter) {
    // 统一的清除逻辑
  }
}
```

#### Step 3.2: 更新 TimeBasedMicrocompactStep

```java
// TimeBasedMicrocompactStep.java
public class TimeBasedMicrocompactStep implements ContextOptimizationStep {
  private final ToolResultCompactor compactor;  // 替代 TimeBasedMicrocompact
  private final MicrocompactConfig config;

  @Override
  public Future<OptimizationResult> apply(SessionContext context, OptimizationContext optContext) {
    SessionContext result = compactor.compactByTimeGap(
        context,
        config.gapThresholdMinutes(),
        config.keepRecent(),
        ToolResultCompactor.DEFAULT_COMPACTABLE_TOOLS);
    // ...
  }
}
```

#### Step 3.3: 更新 DefaultContextOptimizer

```java
// DefaultContextOptimizer.java
public class DefaultContextOptimizer implements ContextOptimizer {
  private final ToolResultCompactor toolResultCompactor;  // 新增

  /**
   * 清除过期的 tool results（基于 cache TTL）
   * 供 ReActAgentLoop 调用
   */
  public SessionContext compactExpiredToolResults(SessionContext context, long cacheTtlMs) {
    return toolResultCompactor.compactByCacheTtl(
        context, cacheTtlMs, 0, null);  // keepRecent=0, 过期的全部清除
  }

  // 废弃旧方法
  @Deprecated(since = "0.3.0", forRemoval = true)
  public SessionContext slimOldToolResults(SessionContext context, long maxAgeMs) {
    return compactExpiredToolResults(context, maxAgeMs);
  }
}
```

#### Step 3.4: 更新 ReActAgentLoop

```java
// ReActAgentLoop.java
// 替换
// contextForOptimization = defaultOptimizer.slimOldToolResults(currentContext, cacheExpiryMs);
// 为
contextForOptimization = defaultOptimizer.compactExpiredToolResults(currentContext, cacheExpiryMs);
```

#### Step 3.5: 删除冗余代码

- 删除 `TimeBasedMicrocompact.java`（逻辑已迁移到 ToolResultCompactor）
- 删除 `ContextSlimmer.slimOldToolResults()` 方法

### 测试计划

```java
// ToolResultCompactorTest.java
@Test void compactByTimeGap_clearsOldResults() {
  // 构造 context，包含 5 个 tool results，时间间隔超过阈值
  SessionContext context = createContextWithToolResults(5, Duration.ofMinutes(70));

  SessionContext result = compactor.compactByTimeGap(context, 60, 2, DEFAULT_COMPACTABLE_TOOLS);

  // 验证只保留最近 2 个
  assertThat(countToolResults(result)).isEqualTo(2);
}

@Test void compactByCacheTtl_clearsExpiredResults() {
  // 构造 context，包含 5 个 tool results，部分超过 TTL
  SessionContext context = createContextWithToolResults(5, Duration.ofMinutes(10));

  SessionContext result = compactor.compactByCacheTtl(context, 5 * 60 * 1000, 0, null);

  // 验证过期的被清除
  assertThat(countToolResults(result)).isLessThan(5);
}

@Test void toolFilter_onlyAffectsSpecifiedTools() {
  // 验证只清除指定类型的工具
}
```

### 改动文件清单

| 文件 | 动作 |
|------|------|
| `ToolResultCompactor.java` | 新建，统一实现 |
| `TimeBasedMicrocompactStep.java` | 使用 ToolResultCompactor |
| `DefaultContextOptimizer.java` | 添加 compactExpiredToolResults，废弃 slimOldToolResults |
| `ReActAgentLoop.java` | 更新调用 |
| `ContextSlimmer.java` | 删除 slimOldToolResults 方法 |
| `TimeBasedMicrocompact.java` | 删除（逻辑已迁移） |
| `ToolResultCompactorTest.java` | 新增测试 |

---

## 实施顺序

```
改进项 1 (Metadata 类型安全)
    │
    ├── 独立实施，无依赖
    │
    ▼
改进项 2 (EventPublisher 采用)
    │
    ├── 独立实施，无依赖
    │
    ▼
改进项 3 (职责统一)
    │
    ├── 建议在改进项 1 完成后实施
    │   （SessionContext API 变化可能影响 ToolResultCompactor）
    │
    ▼
完成
```

**推荐顺序**：改进项 1 → 改进项 2 → 改进项 3

---

## 风险与缓解

| 风险 | 缓解措施 |
|------|---------|
| 序列化兼容性破坏 | 使用 Jackson @JsonCreator 处理旧格式，添加兼容性测试 |
| 废弃方法调用点遗漏 | 使用 @Deprecated(forRemoval=true) + 编译警告检查 |
| 职责统一引入回归 | 保留原有测试用例，确保行为一致后再删除旧代码 |
| 工具过滤策略变化 | 保持 DEFAULT_COMPACTABLE_TOOLS 与原有 TimeBasedMicrocompact 一致 |

---

## 验收标准

### 改进项 1
- [ ] SessionContext 包含 compressionState 字段
- [ ] 所有调用点使用新 API
- [ ] 旧 JSON 格式能正确反序列化
- [ ] 所有测试通过

### 改进项 2
- [ ] CompressionStep、ContextPressureMonitor、StandardPromptEngine 使用 ContextEventPublisher
- [ ] 无直接 dispatcher.dispatch() 调用（context 事件相关）
- [ ] 所有测试通过

### 改进项 3
- [ ] ToolResultCompactor 统一实现两种策略
- [ ] TimeBasedMicrocompact.java 已删除
- [ ] ContextSlimmer.slimOldToolResults 已删除
- [ ] 所有测试通过