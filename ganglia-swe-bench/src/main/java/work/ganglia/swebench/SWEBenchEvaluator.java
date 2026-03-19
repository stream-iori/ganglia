package work.ganglia.swebench;

import io.vertx.core.Vertx;
import work.ganglia.swebench.tools.DockerBashTools;
import work.ganglia.swebench.tools.DockerFileSystemTools;
import work.ganglia.swebench.tools.DockerFileEditTools;
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

            // 1. Setup Bootstrap Options with Sandbox Tools and Observer
            work.ganglia.BootstrapOptions options = work.ganglia.BootstrapOptions.defaultOptions()
                .withObservers(List.of(trajectoryLogger))
                .withExtraToolSets(List.of(
                    new DockerBashTools(vertx, sandbox),
                    new DockerFileSystemTools(vertx, sandbox),
                    new DockerFileEditTools(vertx, sandbox)
                ));

            // 2. Bootstrap Ganglia with Coding Capabilities
            // This injects Persona, Mandates, Workflow, and standard Coding tools.
            // Note: Docker tools will override local ones because they are in 'extraToolSets'.
            work.ganglia.Ganglia ganglia = work.ganglia.coding.CodingAgentBuilder.bootstrap(vertx, options)
                .toCompletionStage().toCompletableFuture().get();

            log.info("Ganglia Coding Agent bootstrapped for SWE-bench task.");

            // 3. Run Agent
            String problemStatement = task.getProblemStatement();
            log.info("Launching Agent for task {}...", task.getInstanceId());

            String result = ganglia.sessionManager().getSession(task.getInstanceId())
                .compose(context -> {
                    // Inject problem metadata via functional update
                    Map<String, Object> newMetadata = new HashMap<>(context.metadata());
                    newMetadata.put("problem_statement", problemStatement);
                    newMetadata.put("instance_id", task.getInstanceId());
                    
                    work.ganglia.port.chat.SessionContext evolvedContext = context.withMetadata(newMetadata);
                    return ganglia.agentLoop().run(problemStatement, evolvedContext);
                })
                .toCompletionStage().toCompletableFuture().get();

            log.info("Agent finished with result: {}", result);
            trajectoryLogger.logAction("agent_final", result);

            // 4. Evaluate Result
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
                //without pytenst cache
                String testResult = sandbox.execInDir("/workspace/repo", "pytest", "-p", "no:cacheprovider", test);
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

        Path datasetPath = Path.of("ganglia-swe-bench/swe_bench_lite_subset.jsonl");
        if (datasetPath.toFile().exists()) {
            evaluator.evaluate(datasetPath);
        } else {
            log.warn("Dataset not found at {}. Please run download_dataset.py first.", datasetPath);
        }

        vertx.close();
    }
}
