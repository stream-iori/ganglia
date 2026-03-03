# Ganglia SWE-bench 评估模块

`ganglia-swe-bench` 是 Ganglia 框架的官方评估组件，专门用于在 [SWE-bench](https://www.swebench.com/) (Software Engineering Benchmark) 数据集上测试 Agent 的工程解决能力。它提供了一个隔离的 Docker 沙箱环境，让 Agent 能够尝试修复真实的 GitHub Issue，并自动验证修复结果。

## 1. 核心作用

*   **自动化测试环境**：通过 Testcontainers 集成 Docker，为每个任务动态创建隔离的 Python/Git 环境。
*   **端到端评估**：模拟从读取 Issue 描述到定位代码、修改 Bug、运行测试的完整工程链路。
*   **轨迹记录 (Trajectory Log)**：详细记录 Agent 的每一步推理和工具调用，生成的日志位于 `target/e2e-logs/` 下。
*   **精准验证**：使用数据集提供的 `FAIL_TO_PASS` 测试用例，在 Agent 完成修复后自动应用官方测试补丁进行最终验收。

## 2. 环境准备

在运行评估之前，请确保您的开发环境满足以下要求：

### 2.1 Docker 镜像
评估模块依赖预构建的 Docker 镜像作为沙箱基础。目前主要支持 `astropy` 相关的任务：
```bash
# 在项目根目录下执行
docker build -t astropy-base -f ganglia-swe-bench/docker/Dockerfile.astropy-base ganglia-swe-bench/docker
```

### 2.2 数据集下载
需要下载 SWE-bench Lite 的子集作为评估输入。
```bash
# 建议在虚拟环境中安装依赖
python3 -m venv venv
source venv/bin/activate
pip install datasets
python3 ganglia-swe-bench/download_dataset.py
```
这将在 `ganglia-swe-bench/target/` 下生成 `swe_bench_lite_subset.jsonl` 文件。

### 2.3 配置文件
确保 `.ganglia/config.json` 中配置了有效的 LLM API Key (OpenAI 兼容接口)。

## 3. 运行评估方式

使用 Maven 执行评估器主类：

```bash
mvn exec:java -Dexec.mainClass="me.stream.ganglia.swebench.SWEBenchEvaluator" -pl ganglia-swe-bench
```

### 运行参数说明：
*   默认运行 `ganglia-swe-bench/target/swe_bench_lite_subset.jsonl` 中的前 5 个任务。
*   评估器会依次为每个任务启动容器、克隆代码、运行 Agent。

## 4. 验证方式

评估器通过以下步骤判定任务是否成功：

1.  **Agent 运行阶段**：Agent 在沙箱中拥有 `run_shell_command`、`read_file`、`replace_in_file` 等权限。它必须自主决定如何修改代码。
2.  **验收阶段 (Validation)**：
    *   **应用测试补丁**：评估器会将数据集中的 `test_patch` (官方验证测试) 应用到 Agent 修改后的代码上。
    *   **针对性测试**：评估器根据任务元数据中的 `FAIL_TO_PASS` 字段，运行那些原本应该失败但现在应该通过的测试用例。
3.  **判定标准**：
    *   **RESOLVED**：所有 `FAIL_TO_PASS` 列表中的测试均运行成功 (PASSED)。
    *   **FAILED**：任何一个测试失败，或 Agent 在达到最大迭代次数前未给出最终答案。

## 5. 目录结构

*   `docker/`: 包含各种语言/项目的基础 Dockerfile。
*   `src/main/java/.../swebench/`: 
    *   `SWEBenchEvaluator`: 评估主循环。
    *   `SandboxManager`: Docker 容器全生命周期管理。
    *   `tools/`: 适配 Docker 环境的专属工具集 (DockerBashTools, etc.)。
*   `download_dataset.py`: 数据集准备脚本。
