package work.ganglia.it.harness;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import work.Main;
import work.ganglia.core.Ganglia;
import work.ganglia.core.model.SessionContext;
import work.ganglia.stubs.StubModelGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A harness to run E2E test scenarios against the agent.
 */
public class E2ETestHarness {
    private static final Logger logger = LoggerFactory.getLogger(E2ETestHarness.class);
    private final Vertx vertx;
    private Ganglia ganglia;
    private StubModelGateway stubModel;

    public E2ETestHarness(Vertx vertx) {
        this.vertx = vertx;
    }

    public Future<Void> setup() {
        return setup(new JsonObject().put("agent", new JsonObject().put("projectRoot", ".")));
    }

    public Future<Void> setup(JsonObject configOverride) {
        stubModel = new StubModelGateway();
        return Main.bootstrap(vertx, ".ganglia/config.json", configOverride, stubModel)
            .onSuccess(g -> this.ganglia = g)
            .mapEmpty();
    }

    public Ganglia getGanglia() {
        return ganglia;
    }

    public StubModelGateway getStubModel() {
        return stubModel;
    }

    public Future<String> runScenario(TestScenario scenario) {
        logger.info("Running E2E Scenario: {}", scenario.name());

        // Load stub model with scenario responses
        scenario.mockResponses().forEach(stubModel::addResponse);

        String sessionId = "e2e-" + scenario.id() + "-" + UUID.randomUUID().toString().substring(0, 4);
        SessionContext context = ganglia.sessionManager().createSession(sessionId);

        return ganglia.agentLoop().run(scenario.userInput(), context)
            .compose(result -> handleInteractiveSteps(scenario, context, result, 0))
            .onSuccess(finalResult -> verifyExpectations(scenario, finalResult));
    }

    private Future<String> handleInteractiveSteps(TestScenario scenario, SessionContext context, String currentOutput, int stepIndex) {
        if (scenario.interactiveSteps() == null || stepIndex >= scenario.interactiveSteps().size()) {
            return Future.succeededFuture(currentOutput);
        }

        TestScenario.InteractiveStep step = scenario.interactiveSteps().get(stepIndex);
        if (step.expectedInterruptPrompt() != null) {
            assertTrue(currentOutput.contains(step.expectedInterruptPrompt()),
                "Expected interrupt prompt: " + step.expectedInterruptPrompt());
        }

        // Must fetch latest context before resume
        return ganglia.sessionManager().getSession(context.sessionId())
            .compose(latest -> ganglia.agentLoop().resume(step.userInput(), latest))
            .compose(nextOutput -> handleInteractiveSteps(scenario, context, nextOutput, stepIndex + 1));
    }

    private void verifyExpectations(TestScenario scenario, String result) {
        for (TestScenario.Expectation exp : scenario.expectations()) {
            switch (exp.type()) {
                case "OUTPUT_CONTAINS" ->
                    assertTrue(result.contains(exp.value()),
                        "Output should contain: " + exp.value());
                case "FILE_EXISTS" ->
                    assertTrue(Files.exists(Path.of(exp.value())),
                        "File should exist: " + exp.value());
                case "MEMORY_CONTAINS" -> {
                    // Logic to check MEMORY.md
                    try {
                        String memory = Files.readString(Path.of("MEMORY.md"));
                        assertTrue(memory.contains(exp.value()),
                            "Memory should contain: " + exp.value());
                    } catch (Exception e) {
                        throw new RuntimeException("Failed to read memory file", e);
                    }
                }
            }
        }
    }
}
