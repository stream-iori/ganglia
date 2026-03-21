package work.ganglia.it;

import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import work.ganglia.it.harness.E2ETestHarness;
import work.ganglia.it.harness.TestScenario;
import work.ganglia.port.external.llm.ModelResponse;
import work.ganglia.port.external.tool.ToolCall;
import work.ganglia.port.internal.state.TokenUsage;

@ExtendWith(VertxExtension.class)
public class Scenario4UserInteractionE2EIT {

  private E2ETestHarness harness;

  @BeforeEach
  void setUp(Vertx vertx, VertxTestContext testContext) {
    harness = new E2ETestHarness(vertx);
    harness.setup().onComplete(testContext.succeedingThenComplete());
  }

  @Test
  void testInteractionAndResume(Vertx vertx, VertxTestContext testContext) {
    ToolCall askCall =
        new ToolCall(
            "c1",
            "ask_selection",
            Map.of(
                "header", "Choice",
                "question", "Which file to delete?",
                "type", "choice",
                "options", List.of("file1.txt (Size: 10KB)", "file2.txt (Size: 20KB)")));

    TestScenario scenario =
        new TestScenario(
            "scenario4",
            "User Interaction",
            "Delete the largest log file.",
            List.of(
                new ModelResponse(
                    "I found two files. Please choose one.",
                    List.of(askCall),
                    new TokenUsage(1, 1)),
                new ModelResponse(
                    "Deleting file2.txt...", Collections.emptyList(), new TokenUsage(1, 1))),
            List.of(new TestScenario.InteractiveStep("file2.txt", "Which file to delete?")),
            List.of(new TestScenario.Expectation("OUTPUT_CONTAINS", "Deleting file2.txt")));

    harness
        .runScenario(scenario)
        .onComplete(testContext.succeeding(result -> testContext.completeNow()));
  }
}
