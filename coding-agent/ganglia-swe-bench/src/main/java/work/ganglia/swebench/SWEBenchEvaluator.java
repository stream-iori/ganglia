package work.ganglia.swebench;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Vertx;

import work.ganglia.port.chat.SessionContext;
import work.ganglia.port.external.llm.ModelOptions;
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
      String containerRoot = "/workspace";
      String hostRoot = taskRoot.toAbsolutePath().toString();

      // For tools inside container: map host paths BACK to container paths if Agent uses them
      work.ganglia.util.PathMapper dockerMapper =
          new work.ganglia.util.MappingPathSanitizer(hostRoot, containerRoot);

      // For host-side tools: map container paths to host paths
      work.ganglia.util.PathMapper hostMapper =
          new work.ganglia.util.MappingPathSanitizer(containerRoot, hostRoot);

      List<work.ganglia.kernel.loop.AgentLoopObserver> observers = new java.util.ArrayList<>();
      observers.add(trajectoryLogger);
      observers.add(
          new work.ganglia.kernel.loop.AgentLoopObserver() {
            private final Logger progressLog = LoggerFactory.getLogger("work.ganglia.progress");

            @Override
            public void onObservation(
                String sessionId,
                work.ganglia.port.external.tool.ObservationType type,
                String content,
                java.util.Map<String, Object> data) {
              String typeName = type.name();
              if (typeName.equals("REASONING_STARTED")) {
                progressLog.info("[Agent Thinking...]");
              } else if (typeName.equals("TOOL_STARTED")) {
                progressLog.info("[Tool Call] {}: {}", data.get("name"), data.get("arguments"));
              } else if (typeName.equals("TOOL_FINISHED")) {
                String status = (String) data.get("status");
                progressLog.info("[Tool Result] Status: {}", status);
              } else if (typeName.equals("TURN_FINISHED")) {
                progressLog.info("--- Turn Completed ---");
              }
            }

            @Override
            public void onUsageRecorded(
                String sessionId, work.ganglia.port.internal.state.TokenUsage usage) {
              // No-op for console
            }
          });

      work.ganglia.BootstrapOptions baseOptions =
          work.ganglia.BootstrapOptions.builder()
              .projectRoot(hostRoot)
              .configPath(taskRoot.resolve(".ganglia/config.json").toAbsolutePath().toString())
              .extraObservers(observers)
              .commandExecutor(new DockerCommandExecutor(vertx, sandbox))
              .build();

      work.ganglia.Ganglia ganglia =
          work.ganglia.coding.CodingAgentBuilder.create(vertx)
              .withOptions(baseOptions)
              .withInternalPathMapper(dockerMapper)
              .withExternalPathMapper(hostMapper)
              .filterContextSources(
                  s ->
                      s.getClass()
                          .getName()
                          .equals(
                              "work.ganglia.infrastructure.internal.prompt.context.EnvironmentSource"))
              .addContextSource(
                  sessionContext -> {
                    List<work.ganglia.port.internal.prompt.ContextFragment> fragments =
                        new java.util.ArrayList<>();
                    fragments.add(
                        work.ganglia.port.internal.prompt.ContextFragment.prunable(
                            "OS", "Linux (Docker Sandbox)", 10));
                    fragments.add(
                        work.ganglia.port.internal.prompt.ContextFragment.prunable(
                            "Project Structure",
                            "The project is located at /workspace/repo. Use /workspace/repo as the root for all file operations.",
                            10));
                    return io.vertx.core.Future.succeededFuture(fragments);
                  })
              .bootstrap()
              .toCompletionStage()
              .toCompletableFuture()
              .get();

      log.info("Ganglia Agent bootstrapped via CodingAgentBuilder. Launching evaluation...");

      // Print config inside container for verification
      String containerConfig = sandbox.exec("cat", "/workspace/.ganglia/config.json");
      log.info("Config inside container (/workspace/.ganglia/config.json):\n{}", containerConfig);

      var modelOptions =
          new ModelOptions(
              ganglia.configManager().getTemperature(),
              ganglia.configManager().getMaxTokens(),
              ganglia.configManager().getModel(),
              ganglia.configManager().isStream());

      SessionContext sessionContext =
          new SessionContext(
              task.getInstanceId(),
              Collections.emptyList(),
              null,
              Collections.emptyMap(),
              Collections.emptyList(),
              modelOptions,
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

    // Target location for the reference mirror: ganglia-swe-bench/.ganglia/[repo_dir_name]
    Path mirrorRepoParent = baseDir.resolve(".ganglia");
    if (!Files.exists(mirrorRepoParent)) {
      log.info("Creating directory: {}", mirrorRepoParent);
      Files.createDirectories(mirrorRepoParent);
    }

    Path localSourceRepo = mirrorRepoParent.resolve(repoDirName);

    // If mirror does not exist, clone it from GitHub as a bare repository
    if (!Files.exists(localSourceRepo) || !Files.exists(localSourceRepo.resolve("config"))) {
      log.info(
          "Local mirror not found for {}. Cloning from GitHub to {}...", repoName, localSourceRepo);
      ProcessBuilder cloneMirrorPb =
          new ProcessBuilder(
              "git",
              "clone",
              "--bare",
              "https://github.com/" + repoName + ".git",
              localSourceRepo.toAbsolutePath().toString());
      cloneMirrorPb.inheritIO().start().waitFor(30, TimeUnit.MINUTES);
    }

    log.info("Using local source repository as reference for task clone: {}", localSourceRepo);
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
    vertx.close().toCompletionStage().toCompletableFuture().get();
  }
}
