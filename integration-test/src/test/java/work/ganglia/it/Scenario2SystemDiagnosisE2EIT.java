package work.ganglia.it;

import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import work.ganglia.port.external.llm.ModelResponse;
import work.ganglia.port.internal.state.TokenUsage;
import work.ganglia.it.harness.E2ETestHarness;
import work.ganglia.it.harness.TestScenario;
import work.ganglia.port.external.tool.ToolCall;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@ExtendWith(VertxExtension.class)
public class Scenario2SystemDiagnosisE2EIT {

    private E2ETestHarness harness;

    @BeforeEach
    void setUp(Vertx vertx, VertxTestContext testContext) {
        harness = new E2ETestHarness(vertx);
        harness.setup().onComplete(testContext.succeedingThenComplete());
    }

    @Test
    void testSystemDiagnosis(Vertx vertx, VertxTestContext testContext, @TempDir Path tempDir) {
        // Create files with TODO
        vertx.fileSystem().writeFileBlocking(tempDir.resolve("task1.java").toString(), Buffer.buffer("// TODO: Fix this"));
        vertx.fileSystem().writeFileBlocking(tempDir.resolve("task2.java").toString(), Buffer.buffer("// TODO: And this"));

        ToolCall grepCall = new ToolCall("c1", "grep_search", Map.of(
            "path", tempDir.toString(),
            "pattern", "TODO"
        ));

        TestScenario scenario = new TestScenario(
            "scenario2",
            "System Diagnosis",
            "Find all TODOs in " + tempDir,
            List.of(
                new ModelResponse("Searching for TODOs...", List.of(grepCall), new TokenUsage(1, 1)),
                new ModelResponse("I found TODOs in task1.java and task2.java.", Collections.emptyList(), new TokenUsage(1, 1))
            ),
            Collections.emptyList(),
            List.of(
                new TestScenario.Expectation("OUTPUT_CONTAINS", "task1.java"),
                new TestScenario.Expectation("OUTPUT_CONTAINS", "task2.java")
            )
        );

        harness.runScenario(scenario)
            .onComplete(testContext.succeeding(result -> testContext.completeNow()));
    }
}
