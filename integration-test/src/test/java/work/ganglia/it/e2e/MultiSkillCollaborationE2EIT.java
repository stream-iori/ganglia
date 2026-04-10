package work.ganglia.it.e2e;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

import work.ganglia.it.support.harness.E2ETestHarness;
import work.ganglia.it.support.harness.TestScenario;
import work.ganglia.port.external.llm.ModelResponse;
import work.ganglia.port.external.tool.ToolCall;
import work.ganglia.port.internal.state.TokenUsage;

@ExtendWith(VertxExtension.class)
public class MultiSkillCollaborationE2EIT {

  private E2ETestHarness harness;

  @BeforeEach
  void setUp(Vertx vertx, VertxTestContext testContext) {
    harness = new E2ETestHarness(vertx);
    harness.setup().onComplete(testContext.succeedingThenComplete());
  }

  @Test
  void testMultiSkillCollaboration(Vertx vertx, VertxTestContext testContext) {
    ToolCall activateCall =
        new ToolCall("c1", "activate_skill", Map.of("skillId", "it-test-skill", "confirmed", true));
    ToolCall echoCall = new ToolCall("c2", "it_test_tool", Map.of("message", "Hello from Skill"));

    TestScenario scenario =
        new TestScenario(
            "scenario3",
            "Multi-Skill Collaboration",
            "Activate it-test-skill and say hello.",
            List.of(
                new ModelResponse(
                    "Activating skill...", List.of(activateCall), new TokenUsage(1, 1)),
                new ModelResponse("Using tool...", List.of(echoCall), new TokenUsage(1, 1)),
                new ModelResponse(
                    "Skill collaboration successful: IT_TOOL_OUTPUT: Hello from Skill",
                    Collections.emptyList(),
                    new TokenUsage(1, 1))),
            Collections.emptyList(),
            List.of(
                new TestScenario.Expectation(
                    "OUTPUT_CONTAINS", "IT_TOOL_OUTPUT: Hello from Skill")));

    harness
        .runScenario(scenario)
        .onComplete(testContext.succeeding(result -> testContext.completeNow()));
  }
}
