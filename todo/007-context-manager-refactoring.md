# Context Management 架构分析与改造计划

> 对 ganglia-harness 的 context management 模块进行架构重构，解决 God Class、职责分散、扩展性差等问题。

---

## 一、整体架构图

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                            ReActAgentLoop                                    │
│  ┌─────────────┐  ┌──────────────────┐  ┌─────────────────┐                 │
│  │pressureMoni │  │contextSlimmer    │  │contextOptimizer │                 │
│  │    tor      │  │(separate inst)   │  │                 │                 │
│  └─────────────┘  └──────────────────┘  └────────┬────────┘                 │
└─────────────────────────────────────────────────┼───────────────────────────┘
                                                  │
┌─────────────────────────────────────────────────▼───────────────────────────┐
│                       DefaultContextOptimizer (710 lines)                    │
│  ┌─────────────┐ ┌──────────────┐ ┌───────────────┐ ┌──────────────────┐    │
│  │slimmer      │ │timeBased     │ │compressor     │ │fileRestoration   │    │
│  │(internal)   │ │Microcompact  │ │               │ │Service           │    │
│  └─────────────┘ └──────────────┘ └───────────────┘ └──────────────────┘    │
│  + chunked compression + PTL retry + circuit breaker + observation dispatch │
└──────────────────────────────────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────────────────────────────┐
│                       StandardPromptEngine                                   │
│  ┌──────────────────────────────────────────────────────────────────────┐   │
│  │ ContextSource[]: Persona, Tool, Memory, Environment, Skill, SubAgent │   │
│  └──────────────────────────────────────────────────────────────────────┘   │
│  ┌─────────────┐ ┌─────────────────┐ ┌──────────────────────────────────┐   │
│  │composer     │ │toolResultEnforc │ │toolOutputTruncator               │   │
│  └─────────────┘ └─────────────────────────────────┘ └──────────────────────┘
└──────────────────────────────────────────────────────────────────────────────┘
```

---

## 二、核心问题分析

### 1. God Class: DefaultContextOptimizer (严重)

```
文件: DefaultContextOptimizer.java (710 行)
职责数量: 8+
  - 压缩编排
  - 分块压缩
  - PTL 重试逻辑
  - Slimming 集成
  - Time-based microcompact
  - 文件恢复
  - 熔断器
  - Observation dispatch
```

**问题**: 违反 SRP，任何压缩策略变更都需要修改这个大类。

### 2. SessionContext Metadata 滥用 (中等)

```java
// SessionContext.java:182-185
private static final String KEY_RUNNING_SUMMARY = "runningSummary";
private static final String KEY_COMPRESSION_FAILURES = "compressionFailures";
private static final String KEY_LAST_ASSISTANT_TIMESTAMP = "lastAssistantTimestamp";
private static final String KEY_RECENTLY_READ_FILES = "recentlyReadFiles";
```

**问题**: 用 Map 存储类型不安全，隐式耦合，难以追踪。

### 3. 重复的 Slimmer 实例 (中等)

```java
// ReActAgentLoop.java:63
private final ContextSlimmer contextSlimmer;

// DefaultContextOptimizer.java:48
private final ContextSlimmer slimmer;  // 内部又创建了一个
```

**问题**: 两个独立实例，职责重叠，行为可能不一致。

### 4. 配置类分散 (轻微)

```
ContextBudget       - 预算分配
CompressionBudget   - 压缩配置 (未找到，可能用常量)
MicrocompactConfig  - 微压缩配置
SessionMemoryCompactConfig - 会话内存压缩配置
```

**问题**: 配置分散，难以统一管理和调整。

### 5. ContextSource cacheable 标记不一致 (轻微)

```java
// EnvironmentSource.java:26 - 正确标记 cacheable
ContextFragment.cacheable("OS", ...)

// MemoryContextSource.java - 未显式标记，依赖 MemoryModule 的实现
// 这可能导致 prompt cache 不稳定
```

### 6. Observation Dispatch 散落各处 (轻微)

```
DefaultContextOptimizer  → CONTEXT_COMPRESSED, SYSTEM_EVENT
StandardPromptEngine     → CONTEXT_BUDGET_ALLOCATED, CONTEXT_ANALYSIS, PROMPT_CACHE_STATS
ContextPressureMonitor   → CONTEXT_PRESSURE_CHANGED
ReActAgentLoop           → 多种事件
```

**问题**: 没有统一的事件发布抽象。

### 7. 已有的抽象未被使用

```java
// 这些接口存在但未被使用:
ContextOptimizationStep  - 优化步骤接口
CompressionRequest       - 压缩请求
CompressionResult        - 压缩结果
CompressionStrategy      - 压缩策略
```

---

## 三、可读性评估

|            类            |  行数  |  可读性  |      备注       |
|-------------------------|------|-------|---------------|
| DefaultContextOptimizer | 710  | ⚠️ 中等 | 方法过长，嵌套深      |
| StandardPromptEngine    | 329  | ✅ 良好  | 职责清晰          |
| ContextComposer         | 213  | ✅ 良好  | 单一职责          |
| ContextSlimmer          | 320  | ✅ 良好  | 方法独立          |
| SessionContext          | 306  | ⚠️ 中等 | metadata 逻辑分散 |
| ReActAgentLoop          | ~900 | ⚠️ 中等 | 依赖过多          |

---

## 四、扩展性评估

|         场景         |            当前状态             | 改动难度 |
|--------------------|-----------------------------|------|
| 添加新的压缩策略           | 需修改 DefaultContextOptimizer | 高    |
| 添加新的 slimming 操作   | 需修改 ContextSlimmer          | 中    |
| 添加新的 ContextSource | 实现 ContextSource 接口         | 低 ✅  |
| 修改预算分配策略           | 修改 ContextBudget            | 低 ✅  |
| 添加新的压力监控指标         | 需修改 ContextPressureMonitor  | 中    |

---

## 五、改造计划

### Phase 1: 拆分 DefaultContextOptimizer (优先级: 高)

**目标**: 将 God Class 拆分为单一职责的小类

```
DefaultContextOptimizer (重构后 ~150 行)
├── CompressionOrchestrator (编排器)
│
├── CompressionPipeline (管道模式)
│   ├── TimeBasedMicrocompactStep
│   ├── SlimmingStep
│   ├── CompressionStep
│   └── FileRestorationStep
│
├── ChunkedCompressor (分块压缩)
│   └── PTLRetryHandler
│
└── CircuitBreaker (熔断器)
```

**改动文件**:
| 文件 | 动作 |
|------|------|
| `DefaultContextOptimizer.java` | 精简为编排器 |
| `CompressionPipeline.java` (新建) | 管道模式实现 |
| `TimeBasedMicrocompactStep.java` | 从 optimizer 提取 |
| `SlimmingStep.java` | 从 optimizer 提取 |
| `CompressionStep.java` | 核心压缩逻辑 |
| `ChunkedCompressor.java` | 从 optimizer 提取 |
| `PTLRetryHandler.java` | 从 optimizer 提取 |

### Phase 2: 统一 Slimmer 实例 (优先级: 中)

**目标**: 消除重复实例，统一 slimming 入口

```java
// 改造前: ReActAgentLoop 和 DefaultContextOptimizer 各持有一个
// 改造后: 由 DefaultContextOptimizer 统一管理

// ReActAgentLoop 移除 contextSlimmer 字段
// 通过 contextOptimizer.slimOldToolResults() 暴露能力
```

### Phase 3: SessionContext Metadata 类型化 (优先级: 中)

**目标**: 用显式字段替代 Map 存储

```java
// 改造前
private static final String KEY_RUNNING_SUMMARY = "runningSummary";

// 改造后: 提取 CompressionState record
public record CompressionState(
    String runningSummary,
    int consecutiveFailures,
    Instant lastAssistantTimestamp,
    List<RecentlyReadFile> recentlyReadFiles
) {}

// SessionContext 新增显式字段
public record SessionContext(
    ...,
    CompressionState compressionState  // 替代 metadata 中的压缩相关字段
) {}
```

### Phase 4: 配置统一 (优先级: 低)

**目标**: 合并分散的配置类

```java
// 统一配置类
public record ContextManagementConfig(
    ContextBudget budget,
    CompressionConfig compression,    // 合并 CompressionBudget
    MicrocompactConfig microcompact,
    SessionMemoryCompactConfig sessionMemory
) {
    public static ContextManagementConfig fromModel(int contextLimit, int maxTokens) {
        // 统一创建逻辑
    }
}
```

### Phase 5: Event Publisher 抽象 (优先级: 低)

**目标**: 统一事件发布接口

```java
public interface ContextEventPublisher {
    void publishCompressionStarted(String sessionId, int beforeTokens);
    void publishCompressionFinished(String sessionId, int beforeTokens, int afterTokens);
    void publishPressureChanged(String sessionId, ContextPressure pressure);
    void publishBudgetAllocated(String sessionId, ContextBudget budget);
}

// 各组件通过依赖注入获取 publisher
```

---

## 六、改造优先级建议

|  Phase  |  工作量   | 风险 | 收益 |    状态    |
|---------|--------|----|----|----------|
| Phase 1 | 3-5 天  | 中  | 高  | ✅ 已完成    |
| Phase 2 | 1 天    | 低  | 中  | ✅ 已完成    |
| Phase 3 | 1-2 天  | 中  | 中  | ⏸️ 已评估延后 |
| Phase 4 | 0.5-1天 | 低  | 低  | ✅ 已完成    |
| Phase 5 | 1 天    | 低  | 中  | ✅ 已完成    |

---

## 七、实施记录

### Phase 1 进度 ✅ 已完成

- [x] 创建 ContextOptimizationPipeline（已存在，复用）
- [x] 创建 HardLimitGuardStep
- [x] 提取 TimeBasedMicrocompactStep
- [x] 提取 SlimmingStep
- [x] 提取 CompressionStep
- [x] 提取 ChunkedCompressor
- [x] 提取 PTLRetryHandler
- [x] 重构 DefaultContextOptimizer 为编排器
- [x] 添加 CompactBoundaryMetadata.forced() 方法
- [x] 修复 OptimizationContext.thresholdTokens() 计算
- [x] 所有测试通过

### Phase 2 进度 ✅ 已完成

- [x] 移除 ReActAgentLoop 中的 contextSlimmer 字段
- [x] 移除 Builder 中的 contextSlimmer setter
- [x] 通过 DefaultContextOptimizer.slimOldToolResults() 暴露能力
- [x] 编译通过

### Phase 3 进度 ✅ 已完成

**详细计划**: [008-context-manager-improvements.md](008-context-manager-improvements.md)

**实施记录 (2026-04-02)**：
- [x] 添加 compressionState 字段到 SessionContext
- [x] 删除 metadata 中的压缩相关 key 常量
- [x] 更新所有 SessionContext 创建点（15+ 文件）
- [x] 更新 CompressionStep 使用 ContextEventPublisher
- [x] 更新 ContextPressureMonitor 使用 ContextEventPublisher
- [x] 创建 ToolResultCompactor 统一两种策略
- [x] 删除 TimeBasedMicrocompact.java
- [x] 删除 ContextSlimmer.slimOldToolResults() 方法
- [x] 所有测试通过

### Phase 4 进度 ✅ 已完成

**现状分析**：
- `ContextBudget` - 预算分配 (port.internal.prompt 包)
- `MicrocompactConfig` - 微压缩配置 (port.internal.prompt 包)
- `SessionMemoryCompactConfig` - 会话内存压缩配置 (port.internal.prompt 包)
- 配置类已在同一包下，合并成本低

**实施记录**：

- [x] 创建 `ContextManagementConfig` record，统一管理所有配置
- [x] 添加 `fromModel()` 工厂方法，处理边界情况（contextLimit < maxGenerationTokens）
- [x] 更新 `DefaultContextOptimizer` 构造函数，支持 `ContextManagementConfig`
- [x] 更新 `CompressionStep` 构造函数，接受 `SessionMemoryCompactConfig` 参数
- [x] 更新 `StandardPromptEngine` 构造函数，支持 `ContextManagementConfig`
- [x] 更新 `GangliaKernel` 使用 `ContextManagementConfig`
- [x] 所有测试通过 (398 tests)

**关键改动**：
- `ContextManagementConfig.fromModel()` 对小 contextLimit 进行特殊处理，避免预算计算产生不合理的值
- 保留向后兼容的构造函数，支持渐进式迁移

### Phase 5 进度 🚧 进行中

**现状分析**：
- `ObservationDispatcher` 接口已存在
- `DefaultObservationDispatcher` 实现已完善
- 事件发布散落在 4+ 个组件中
- 各组件直接调用 `dispatcher.dispatch()`

**实施记录**：

- [x] 创建 `ContextEventPublisher` 接口，定义语义化事件发布方法
- [x] 创建 `DefaultContextEventPublisher` 实现，适配到 `ObservationDispatcher`
- [ ] 更新 CompressionStep 使用新接口（可选，当前实现已足够）
- [ ] 更新 ContextPressureMonitor 使用新接口（可选）
- [ ] 更新 StandardPromptEngine 使用新接口（可选）

**接口设计**：

```java
public interface ContextEventPublisher {
  void publishCompressionStarted(String sessionId, int beforeTokens);
  void publishCompressionFinished(String sessionId, int beforeTokens, int afterTokens, boolean success);
  void publishPressureChanged(String sessionId, String level, int currentTokens, int limit);
  void publishBudgetAllocated(String sessionId, ContextBudget budget);
  void publishContextAnalysis(String sessionId, Map<String, Object> analysis);
  void publishPromptCacheStats(String sessionId, Map<String, Object> stats);
}
```

**收益**：
- 类型安全的事件发布
- 更好的 IDE 自动补全
- 事件格式集中管理
- 便于单元测试 mock

**后续工作**：更新现有组件使用新接口是可选的，当前 ObservationDispatcher 已足够满足需求。新组件可以直接使用 ContextEventPublisher。
