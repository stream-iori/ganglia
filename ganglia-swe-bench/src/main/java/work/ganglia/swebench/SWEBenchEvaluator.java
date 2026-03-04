package work.ganglia.swebench;

import io.vertx.core.Vertx;
import work.ganglia.core.config.model.ModelConfig;
import work.ganglia.core.llm.OpenAIModelGateway;
import work.ganglia.core.loop.StandardAgentLoop;
import work.ganglia.core.model.SessionContext;
import work.ganglia.core.model.ModelOptions;
import work.ganglia.core.session.DefaultSessionManager;
import work.ganglia.core.schedule.DefaultSchedulableFactory;
import work.ganglia.core.schedule.SchedulableFactory;
import work.ganglia.memory.ContextCompressor;
import work.ganglia.swebench.config.MinimalConfigManager;
import work.ganglia.swebench.prompt.MinimalPromptEngine;
import work.ganglia.swebench.state.InMemoryStateEngine;
import work.ganglia.swebench.state.InMemoryLogManager;
import work.ganglia.swebench.tools.DockerBashTools;
import work.ganglia.swebench.tools.DockerFileSystemTools;
import work.ganglia.swebench.tools.DockerFileEditTools;
import work.ganglia.swebench.tools.SWEBenchToolExecutor;
import work.ganglia.tools.model.ToDoList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SWEBenchEvaluator {
    private static final Logger log = LoggerFactory.getLogger(SWEBenchEvaluator.class);
    private final Vertx vertx;

    public SWEBenchEvaluator(Vertx vertx) {
        this.vertx = vertx;
    }

    public void evaluate(Path datasetPath) throws Exception {
        List<SWEBenchTask> tasks = SWEBenchDatasetLoader.loadFromJsonl(datasetPath);
        log.info("Loaded {} tasks for evaluation", tasks.size());

        for (SWEBenchTask task : tasks) {
            evaluateTask(task);
        }
    }

    private void evaluateTask(SWEBenchTask task) {
        log.info("Starting evaluation for task: {}", task.getInstanceId());
        TrajectoryLogger trajectoryLogger = new TrajectoryLogger(task.getInstanceId());

        try (SandboxManager sandbox = new SandboxManager()) {
            sandbox.startSandbox();
            sandbox.setupTaskEnvironment(task);

            // 1. Setup Infrastructure
            MinimalConfigManager config = new MinimalConfigManager(vertx);
            ModelConfig primaryModel = config.getGangliaConfig().getModel("primary");

            OpenAIModelGateway model = new OpenAIModelGateway(vertx, primaryModel.apiKey(), primaryModel.baseUrl());

            SWEBenchToolExecutor toolExecutor = new SWEBenchToolExecutor(trajectoryLogger);
            toolExecutor.addToolSet(new DockerBashTools(vertx, sandbox));
            toolExecutor.addToolSet(new DockerFileSystemTools(vertx, sandbox));
            toolExecutor.addToolSet(new DockerFileEditTools(vertx, sandbox));

            MinimalPromptEngine promptEngine = new MinimalPromptEngine(toolExecutor);
            ContextCompressor compressor = new ContextCompressor(model, config);
            DefaultSessionManager sessionManager = new DefaultSessionManager(new InMemoryStateEngine(), new InMemoryLogManager(), config);

            // Create a minimal SchedulableFactory without sub-agents or skills
            SchedulableFactory scheduleableFactory = new DefaultSchedulableFactory(
                vertx, model, sessionManager, promptEngine, config, compressor,
                toolExecutor, null, null, null
            );

            StandardAgentLoop loop = new StandardAgentLoop(vertx, model, scheduleableFactory, sessionManager, promptEngine, config, compressor);

            // 2. Run Agent
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("problem_statement", task.getProblemStatement());
            metadata.put("instance_id", task.getInstanceId());

            ModelOptions options = new ModelOptions(config.getTemperature(), config.getMaxTokens(), config.getModel());
            SessionContext initialContext = new SessionContext(
                task.getInstanceId(),
                Collections.emptyList(),
                null,
                metadata,
                Collections.emptyList(),
                options,
                ToDoList.empty()
            );

            log.info("Launching Agent for task {}...", task.getInstanceId());
            String result = loop.run(task.getProblemStatement(), initialContext).toCompletionStage().toCompletableFuture().get();

            log.info("Agent finished with result: {}", result);
            trajectoryLogger.logAction("agent_final", result);

            // 3. Evaluate Result
            log.info("Applying test patch and evaluating...");
            sandbox.getContainer().copyFileToContainer(
                    org.testcontainers.images.builder.Transferable.of(task.getTestPatch().getBytes()),
                    "/workspace/test_patch.diff"
            );

            String patchOutput = sandbox.execInDir("/workspace/repo", "git", "apply", "/workspace/test_patch.diff");
            log.debug("Test Patch output: {}", patchOutput);

            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            List<String> failToPassTests = task.getFailToPass() != null ? mapper.readValue(task.getFailToPass(), new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {}) : Collections.emptyList();
            List<String> passToPassTests = task.getPassToPass() != null ? mapper.readValue(task.getPassToPass(), new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {}) : Collections.emptyList();

            if (failToPassTests.isEmpty()) {
                log.warn("No Fail-to-Pass tests found for task {}", task.getInstanceId());
                log.info("Task {} result: FAILED (No tests to verify)", task.getInstanceId());
                return;
            }

            boolean allPassed = true;
            for (String test : failToPassTests) {
                String testResult = sandbox.execInDir("/workspace/repo", "pytest", test);
                if (testResult.contains("FAILED") || testResult.contains("failed") || testResult.contains("ERROR")) {
                    log.info("Fail-to-Pass test {} FAILED", test);
                    allPassed = false;
                } else {
                    log.info("Fail-to-Pass test {} PASSED", test);
                }
            }

            if (allPassed) {
                log.info("Task {} result: RESOLVED", task.getInstanceId());
            } else {
                log.info("Task {} result: FAILED", task.getInstanceId());
            }

        } catch (Exception e) {
            log.error("Evaluation failed for task {}", task.getInstanceId(), e);
        }
    }

    public static void main(String[] args) throws Exception {
        Vertx vertx = Vertx.vertx();
        SWEBenchEvaluator evaluator = new SWEBenchEvaluator(vertx);

        Path datasetPath = Path.of("ganglia-swe-bench/target/swe_bench_lite_subset.jsonl");
        if (datasetPath.toFile().exists()) {
            evaluator.evaluate(datasetPath);
        } else {
            log.warn("Dataset not found at {}. Please run download_dataset.py first.", datasetPath);
        }

        vertx.close();
    }
}
