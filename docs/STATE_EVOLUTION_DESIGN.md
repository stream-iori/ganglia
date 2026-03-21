# 状态演进设计 (State Evolution & Immutability)

> **Status:** In Development
> **Version:** 0.1.5
>
> **Package:** `work.ganglia.port.chat` (Models)
> **Related:** [Architecture](../ARCHITECTURE.md), [Core Kernel](CORE_KERNEL_DESIGN.md)

## 1. 核心设计哲学：不可变性 (Immutability)

Ganglia 的核心状态对象（如 `SessionContext`, `Turn`, `Message`）全部采用 Java 17 的 `record` 实现。我们严格遵循**函数式更新 (Functional Updates)** 模式，即：**状态一旦创建，永不修改；任何状态的变更都通过产生一个新的副本（Snapshot）来完成。**

### 1.1 态射与演进 (Morphism)

在数学和函数式编程中，这种模式被称为**自同态 (Endomorphism)**。
*   **模式**：`f(InitialState, Event) -> NewState`
*   **实现**：对象提供以 `with...` 开头的方法，返回一个新的记录实例。

```java
// 示例：SessionContext 的链式演进
SessionContext nextContext = initialContext
    .withNewMessage(userMsg)
    .withToDoList(updatedList);
```

## 2. 为什么选择不可变 Record？

### 2.1 彻底消灭竞态条件 (Concurrency Safety)

Ganglia 运行在 Vert.x 的异步非阻塞循环中。由于状态是不可变的，我们不需要任何 `synchronized` 关键字或复杂的锁机制。一个 `SessionContext` 副本可以在多个异步回调、EventBus 监听器中安全地传递，而不用担心它被中途篡改。

### 2.2 无损调试与回溯 (Time-travel Debugging)

每一个副本都是 Agent 运行轨迹上的一个**快照**。
*   **工程价值**：如果 Agent 在复杂的 ReAct 循环中“跑飞了”（逻辑出错），我们可以精确地提取出出错前那一刻的 `SessionContext` 序列化数据，进行本地重放和回归测试。
*   **状态可追溯**：日志系统记录的是状态的演进序列，而不仅仅是最终结果。

### 2.3 简易版“透镜”模式 (Manual Lenses)

对于深层嵌套的 Record（例如：`Context -> Turn -> Message -> ToolObservation`），我们通过在各层级手动实现 `with...` 方法，模拟了函数式语言中的 **Lens (透镜)** 模式。这使得修改深层属性时的代码依然保持高度的可读性和结构完整性。

## 3. 结构稳定性 (Structural Integrity)

### 3.1 聚合与解耦

通过将特定角色的属性聚合到内部 Record 中（如 `Message` 里的 `ToolObservation`），我们确保了：
1.  **语义清晰**：只有 `Role.TOOL` 的消息才包含工具观察数据。
2.  **类型安全**：避免了大量的 null 检查或平铺字段导致的逻辑歧义。

### 3.2 逻辑纯粹性 (Purity)

这种模式迫使开发者编写“纯函数”逻辑。`StandardAgentLoop` 不再是“修改状态”，而是在“编排状态的流动”。

## 4. 总结

在 Ganglia 中，`record` 不仅仅是数据容器，它是**状态机中的不可变节点**。通过这种仿函数式的设计，我们用“结构的稳定性”换取了“逻辑的强壮性”，确保了 Agent 在处理长时间、高并发的任务时，依然保持状态的清晰和可靠。
