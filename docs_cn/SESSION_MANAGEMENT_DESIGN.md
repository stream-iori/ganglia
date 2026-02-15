# Ganglia 会话管理设计 (Session Management)

> **状态**：初始设计
> **相关**：[架构](ARCHITECTURE.md), [核心内核](CORE_KERNEL_DESIGN.md), [记忆架构](MEMORY_ARCHITECTURE.md)

## 1. 简介
`SessionManager` 提供了用于管理 Agent 会话生命周期的高层 API。它抽象了状态持久化、轮次跟踪和上下文初始化的复杂性，允许客户端通过稳定的会话标识符与 Agent 进行交互。

## 2. 核心概念

### 2.1 `SessionContext` (回顾)
`SessionContext` 是会话在特定时间点的不可变状态。它包含：
- `sessionId`：唯一标识符。
- `previousTurns`：已完成轮次的历史记录。
- `currentTurn`：当前正在执行的轮次。
- `toDoList`：活跃的计划。
- `activeSkillIds`：当前启用的技能。

### 2.2 `Turn` (回顾)
`Turn` 代表单次用户-Agent 交互。它跟踪：
- `userMessage`：初始输入。
- `intermediateSteps`：中间思考和工具调用。
- `finalResponse`：最终回答。

## 3. Session Manager 接口

`SessionManager` 作为会话操作的主要协调器。

```java
public interface SessionManager {
    /**
     * 获取现有会话，如果不存在则创建一个新会话。
     */
    Future<SessionContext> getSession(String sessionId);

    /**
     * 持久化会话的当前状态。
     */
    Future<Void> persist(SessionContext context);

    /**
     * 使用默认选项创建一个新的空会话上下文。
     */
    SessionContext createSession(String sessionId);

    /**
     * 列出所有会话 ID。
     */
    Future<List<String>> listSessions();

    /**
     * 删除会话。
     */
    Future<Void> deleteSession(String sessionId);
}
```

## 4. 实现细节

### 4.1 `DefaultSessionManager`
- **依赖**：使用 `StateEngine` 进行物理持久化（JSON 文件）。
- **初始化**：确保新会话使用默认的 `ModelOptions` 和空的 `ToDoList` 进行初始化。
- **配置集成**：从 `ConfigManager` 获取默认的模型名称、温度等参数。

### 4.2 轮次生命周期管理
`SessionManager` 提供了在轮次之间转换的助手方法：
1. **开始轮次 (Start Turn)**：将 `currentTurn` 移至 `previousTurns`（如果存在）并创建一个新的 `currentTurn`。
2. **添加步骤 (Add Step)**：向 `currentTurn` 追加步骤（思考/工具调用/工具结果）。
3. **完成轮次 (Complete Turn)**：设置最终响应并清理当前状态。

## 5. 序列图：会话交互

```mermaid
sequenceDiagram
    participant Client
    participant SM as SessionManager
    participant State as StateEngine
    participant Loop as AgentLoop

    Client->>SM: getSession(sessionId)
    SM->>State: loadSession(sessionId)
    State-->>SM: SessionContext
    SM-->>Client: SessionContext

    Client->>Loop: run(input, context)
    Loop->>Loop: 执行 ReAct 步骤
    Loop->>SM: persist(updatedContext)
    SM->>State: saveSession(updatedContext)
    Loop-->>Client: 最终回答
```

## 6. 与 `Main` 的集成
`Main` 类（或其在 `example` 中的等效类）将使用 `SessionManager` 来管理用户交互的生命周期，而不是手动实例化 `SessionContext`。
