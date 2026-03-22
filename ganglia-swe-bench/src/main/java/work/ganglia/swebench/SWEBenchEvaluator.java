package work.ganglia.swebench;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

import io.vertx.core.Vertx;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import work.ganglia.infrastructure.internal.prompt.context.MarkdownContextResolver;
import work.ganglia.port.external.tool.ToolSet;
import work.ganglia.swebench.tools.DockerCommandExecutor;

public class SWEBenchEvaluator {
  private static final Logger log = LoggerFactory.getLogger(SWEBenchEvaluator.class);
  private final Vertx vertx;

  public SWEBenchEvaluator(Vertx vertx) {
    this.vertx = vertx;
  }

  public void evaluate(Path datasetPath) throws Exception {
    List<SWEBenchTask> tasks = SWEBenchDatasetLoader.loadFromJsonl(datasetPath);
    log.info("Loaded {} tasks for evaluation", tasks.size());

    // 只跑第一个任务进行验证
    if (!tasks.isEmpty()) {
      evaluateTask(tasks.get(0));
    }
  }

  private void evaluateTask(SWEBenchTask task) {
    log.info("Starting evaluation for task: {}", task.getInstanceId());
    TrajectoryLogger trajectoryLogger = new TrajectoryLogger(task.getInstanceId());

    String currentDir = System.getProperty("user.dir");
    Path currentPath = Path.of(currentDir);

    // Resolve taskRoot - always in target/tasks relative to module or root
    Path taskRoot;
    if (currentPath.endsWith("ganglia-swe-bench")) {
      taskRoot = currentPath.resolve("target/tasks/" + task.getInstanceId());
    } else {
      taskRoot = currentPath.resolve("ganglia-swe-bench/target/tasks/" + task.getInstanceId());
    }
    Path repoPath = taskRoot.resolve("repo");

    try {
      // 1. Prepare Host Workspace
      Files.createDirectories(taskRoot.resolve(".ganglia"));

      // Resolve main config
      Path mainGangliaDir;
      if (currentPath.endsWith("ganglia-swe-bench")) {
        mainGangliaDir = currentPath.resolve(".ganglia");
      } else {
        mainGangliaDir = currentPath.resolve("ganglia-swe-bench/.ganglia");
      }

      Path rootConfig = mainGangliaDir.resolve("config.json");
      if (Files.exists(rootConfig)) {
        Files.copy(rootConfig, taskRoot.resolve(".ganglia/config.json"), REPLACE_EXISTING);
      }

      // 2. 在宿主机上准备仓库（如果不存在则克隆，优先使用本地镜像）
      if (!Files.exists(repoPath.resolve(".git"))) {
        log.info("Repository not found at {}, initializing...", repoPath);
        setupRepoOnHost(task, repoPath);
      } else {
        log.info("Repository already exists at {}, skipping clone.", repoPath);
      }

    } catch (Exception e) {
      log.error("Failed to setup host workspace", e);
      return;
    }

    try (SandboxManager sandbox = new SandboxManager()) {
      // 3. Start Sandbox mounting the task root to /workspace and shared .ganglia
      Path mainGangliaDir;
      if (currentPath.endsWith("ganglia-swe-bench")) {
        mainGangliaDir = currentPath.resolve(".ganglia");
      } else {
        mainGangliaDir = currentPath.resolve("ganglia-swe-bench/.ganglia");
      }

      sandbox.startSandbox(taskRoot.toAbsolutePath().toString());

      // Finalize environment inside container (pip install, etc.)
      sandbox.setupTaskEnvironment(task);

      // 4. 配置 Agent
      // projectRoot 设为宿主机路径，Agent 在此处写日志
      work.ganglia.BootstrapOptions baseOptions =
          work.ganglia.BootstrapOptions.defaultOptions()
              .withProjectRoot(taskRoot.toAbsolutePath().toString())
              .withConfigPath(taskRoot.resolve(".ganglia/config.json").toAbsolutePath().toString())
              .withObservers(List.of(trajectoryLogger))
              .withCommandExecutor(new DockerCommandExecutor(vertx, sandbox));

      // 强制工具操作容器内的 /workspace 路径
      String containerRoot = "/workspace";
      work.ganglia.util.PathSanitizer dockerSanitizer =
          new work.ganglia.util.PathSanitizer(containerRoot);
      work.ganglia.port.external.tool.CommandExecutor commandExecutor =
          baseOptions.commandExecutor();

      List<ToolSet> codingToolSets = new java.util.ArrayList<>(baseOptions.extraToolSets());
      codingToolSets.add(
          new work.ganglia.coding.tool.BashFileSystemTools(commandExecutor, dockerSanitizer));
      codingToolSets.add(new work.ganglia.coding.tool.BashTools(commandExecutor));
      codingToolSets.add(new work.ganglia.coding.tool.FileEditTools(vertx, dockerSanitizer));
      codingToolSets.add(new work.ganglia.coding.tool.WebFetchTools(vertx));

      List<work.ganglia.port.internal.prompt.ContextSource> codingSources =
          new java.util.ArrayList<>(baseOptions.extraContextSources());

      // 替换环境信息，指示 Agent 在容器内工作
      codingSources.removeIf(
          s ->
              s.getClass()
                  .getName()
                  .equals("work.ganglia.infrastructure.internal.prompt.context.EnvironmentSource"));
      codingSources.add(
          sessionContext -> {
            List<work.ganglia.port.internal.prompt.ContextFragment> fragments =
                new java.util.ArrayList<>();
            fragments.add(
                work.ganglia.port.internal.prompt.ContextFragment.prunable(
                    "OS", "Linux (Docker Sandbox)", 10));
            fragments.add(
                work.ganglia.port.internal.prompt.ContextFragment.prunable(
                    "Working Directory", containerRoot, 10));
            return io.vertx.core.Future.succeededFuture(fragments);
          });

      var resolver = new MarkdownContextResolver(vertx);
      codingSources.add(
          new work.ganglia.infrastructure.internal.prompt.context.FileContextSource(
              vertx, resolver, "CODING.md"));

      work.ganglia.BootstrapOptions finalOptions =
          baseOptions.withExtraToolSets(codingToolSets).withExtraContextSources(codingSources);

      work.ganglia.Ganglia ganglia =
          work.ganglia.Ganglia.bootstrap(vertx, finalOptions)
              .toCompletionStage()
              .toCompletableFuture()
              .get();

      log.info("Ganglia Agent bootstrapped. Launching evaluation...");

      // Print config inside container for verification
      String containerConfig = sandbox.exec("cat", "/workspace/.ganglia/config.json");
      log.info("Config inside container (/workspace/.ganglia/config.json):\n{}", containerConfig);

      work.ganglia.port.chat.SessionContext sessionContext =
          new work.ganglia.port.chat.SessionContext(
              task.getInstanceId(),
              Collections.emptyList(),
              null,
              Collections.emptyMap(),
              Collections.emptyList(),
              null);

      String result =
          ganglia
              .agentLoop()
              .run(task.getProblemStatement(), sessionContext)
              .toCompletionStage()
              .toCompletableFuture()
              .get();

      log.info("Agent finished task {}. Result: {}", task.getInstanceId(), result);

    } catch (Exception e) {
      log.error("Evaluation failed for task {}", task.getInstanceId(), e);
    }
  }

  private void setupRepoOnHost(SWEBenchTask task, Path repoPath) throws Exception {
    String currentDir = System.getProperty("user.dir");
    Path currentPath = Path.of(currentDir);
    Path baseDir =
        currentPath.endsWith("ganglia-swe-bench")
            ? currentPath
            : currentPath.resolve("ganglia-swe-bench");

    String repoName = task.getRepo(); // e.g., astropy/astropy
    String repoDirName = repoName.replace("/", "__");

    // Primary location: ganglia-swe-bench/.ganglia/[repo_dir_name]
    Path localSourceRepo = baseDir.resolve(".ganglia").resolve(repoDirName);

    if (!Files.exists(localSourceRepo)) {
      // Fallback 1: target/[repo_name]
      localSourceRepo = baseDir.resolve("target").resolve(repoName);
    }

    if (!Files.exists(localSourceRepo)) {
      // Fallback 2: target/[repo_dir_name]
      localSourceRepo = baseDir.resolve("target").resolve(repoDirName);
    }

    if (!Files.exists(localSourceRepo) || !Files.exists(localSourceRepo.resolve(".git"))) {
      throw new RuntimeException(
          "Local repository not found for task "
              + task.getInstanceId()
              + ". Please manually clone "
              + repoName
              + " to "
              + baseDir.resolve(".ganglia").resolve(repoDirName)
              + " on the host.");
    }

    log.info("Using local source repository as reference for clone: {}", localSourceRepo);
    // 使用 --reference 实现引用，避免物理拷贝大量对象库
    ProcessBuilder pb =
        new ProcessBuilder(
            "git",
            "clone",
            "--reference",
            localSourceRepo.toAbsolutePath().toString(),
            localSourceRepo.toAbsolutePath().toString(),
            repoPath.toAbsolutePath().toString());
    pb.inheritIO().start().waitFor(5, TimeUnit.MINUTES);

    // 切换到目标 commit
    log.info("Checking out base commit: {}", task.getBaseCommit());
    new ProcessBuilder(
            "git",
            "-C",
            repoPath.toAbsolutePath().toString(),
            "checkout",
            "-f",
            task.getBaseCommit())
        .inheritIO()
        .start()
        .waitFor(5, TimeUnit.MINUTES);
  }

  public static void main(String[] args) throws Exception {
    Vertx vertx = Vertx.vertx();
    SWEBenchEvaluator evaluator = new SWEBenchEvaluator(vertx);
    Path datasetPath = Path.of("ganglia-swe-bench/swe_bench_lite_subset.jsonl");
    if (datasetPath.toFile().exists()) {
      evaluator.evaluate(datasetPath);
    } else {
      log.warn("Dataset not found at {}", datasetPath);
    }
    vertx.close();
  }
}
