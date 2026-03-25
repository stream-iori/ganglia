package work.ganglia.it;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

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
    Map<String, Object> question =
        Map.of(
            "header", "Choice",
            "question", "Which file to delete?",
            "type", "choice",
            "options",
                List.of(
                    Map.of("label", "file1.txt", "value", "file1.txt", "description", "10KB"),
                    Map.of("label", "file2.txt", "value", "file2.txt", "description", "20KB")));

    ToolCall askCall = new ToolCall("c1", "ask_selection", Map.of("questions", List.of(question)));

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
            List.of(
                new TestScenario.InteractiveStep("file2.txt", "- Which file to delete? (choice)")),
            List.of(new TestScenario.Expectation("OUTPUT_CONTAINS", "Deleting file2.txt")));

    harness
        .runScenario(scenario)
        .onComplete(testContext.succeeding(result -> testContext.completeNow()));
  }
}
