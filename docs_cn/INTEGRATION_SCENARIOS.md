# Ganglia 集成测试场景 (Integration Test Scenarios)

> **目标**：验证 Agent 在复杂任务中的多工具协同能力、记忆能力及低延迟响应。

## 1. 场景一：知识获取与长期记忆 (Web -> Memory)
**描述**：Agent 访问指定 URL 获取技术规范，并将其核心要点记入长期记忆。
*   **输入**： "从 http://localhost:8080/readme.md 获取内容，并记住其中列出的项目规范。"
*   **预期行为**：
    1. 调用 `web_fetch` 获取内容。
    2. 提取 "Conventions" (规范) 部分。
    3. 调用 `remember` (或 `ask_selection` 确认后) 将内容写入 `MEMORY.md`。
*   **验证点**： 检查 `MEMORY.md` 是否包含预期条目。

## 2. 场景二：系统诊断与问题定位 (Shell -> Thought)
**描述**：Agent 通过 Shell 命令分析系统状态或搜索代码库中的异常。
*   **输入**： "查找当前目录下所有包含 'TODO' 字样的文件并列出它们。"
*   **预期行为**：
    1. 调用 `grep_search` 或 `run_shell_command` 执行 `grep -r "TODO" .`。
    2. 解析输出并以自然语言向用户报告。
*   **验证点**： 报告中必须包含本项目中存在的 TODO 标记。

## 3. 场景三：多技能协同 (Skill -> Web -> Shell)
**描述**：激活特定技能，利用其工具完成跨域任务。
*   **输入**： "激活 'java-expert' 技能，从 Vert.x 官网获取最新版本，并检查我们的 pom.xml 是否是最新的。"
*   **预期行为**：
    1. 调用 `activate_skill`。
    2. 调用 `web_fetch` 获取版本信息。
    3. 调用 `read_file` 读取 `pom.xml`。
    4. 逻辑判断并给出建议。
*   **验证点**： Agent 能否正确识别 `pom.xml` 中的版本。

## 4. 场景四：用户交互与中断恢复 (Interrupt -> Resume)
**描述**：在执行敏感操作（如 Shell 命令）前请求用户选择。
*   **输入**： "搜索日志文件并删除最大的那一个。"
*   **预期行为**：
    1. `list_directory` 或 `glob` 定位文件。
    2. 调用 `ask_selection` 让用户确认要删除的文件。
    3. 用户选择后，执行 `run_shell_command` 进行清理。
*   **验证点**： 流程在 `ask_selection` 处正确暂停，并在回复后继续。

## 5. 场景五：代码库发现与探索 (Discovery -> Search -> Read)
**描述**：利用标准工程工具进行高效的代码库摸排。
*   **输入**： "找到 'docs' 目录下的所有 Markdown 文件，在其中搜索 'Phase' 这个词，然后阅读计划书 (plan)。"
*   **预期行为**：
    1. 调用 `glob` 匹配 `docs/*.md`。
    2. 调用 `grep_search` 在匹配到的文件中查找 "Phase"。
    3. 调用 `read_file` 读取 `docs/plan.md`。
*   **验证点**： Agent 能够从发现文件到精准定位内容，最后读取全文。
