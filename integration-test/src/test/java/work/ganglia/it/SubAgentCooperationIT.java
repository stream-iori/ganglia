package work.ganglia.it;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import work.ganglia.BootstrapOptions;
import work.ganglia.Ganglia;
import work.ganglia.coding.CodingAgentBuilder;
import work.ganglia.port.chat.SessionContext;
import work.ganglia.port.external.llm.ChatRequest;
import work.ganglia.port.external.llm.ModelGateway;
import work.ganglia.port.external.llm.ModelResponse;
import work.ganglia.port.external.tool.ToolCall;
import work.ganglia.port.internal.state.TokenUsage;

@ExtendWith(VertxExtension.class)
public class SubAgentCooperationIT {

  private Ganglia ganglia;
  private ModelGateway mockModel;

  @BeforeEach
  void setUp(Vertx vertx, VertxTestContext testContext, @TempDir Path tempDir)
      throws java.io.IOException {
    mockModel = mock(ModelGateway.class);
    when(mockModel.chat(any(ChatRequest.class)))
        .thenReturn(Future.failedFuture("Reflection disabled"));

    String projectRoot = tempDir.toRealPath().toString();

    BootstrapOptions options =
        BootstrapOptions.defaultOptions()
            .withProjectRoot(projectRoot)
            .withModelGateway(mockModel)
            .withOverrideConfig(
                new JsonObject().put("webui", new JsonObject().put("enabled", false)));

    CodingAgentBuilder.bootstrap(vertx, options)
        .onComplete(
            testContext.succeeding(
                (Ganglia g) -> {
                  this.ganglia = g;
                  testContext.completeNow();
                }));
  }

  @Test
  void testInvestigatorDelegationAndCalculation(Vertx vertx, VertxTestContext testContext) {
    // Master Agent receives instruction: "Find three numbers in files and sum them up."
    // Master decides to delegate finding numbers to an INVESTIGATOR sub-agent.

    ToolCall delegateCall =
        new ToolCall(
            "c1",
            "call_sub_agent",
            Map.of(
                "task", "Find three numbers in the project files.",
                "persona", "INVESTIGATOR"));

    // Sub-agent (Investigator) response mock
    String subAgentResult = "I found three numbers: 10, 20, and 30.";

    when(mockModel.chatStream(any(ChatRequest.class), any()))
        // First call: Master delegates
        .thenReturn(
            Future.succeededFuture(
                new ModelResponse("Delegating...", List.of(delegateCall), new TokenUsage(10, 10))))
        // Second call (Sub-agent execution internally uses ModelGateway too): Investigator returns
        // findings
        .thenReturn(
            Future.succeededFuture(
                new ModelResponse(subAgentResult, Collections.emptyList(), new TokenUsage(5, 5))))
        // Third call: Master receives sub-agent result and finishes
        .thenReturn(
            Future.succeededFuture(
                new ModelResponse(
                    "The sum of 10, 20, and 30 is 60.",
                    Collections.emptyList(),
                    new TokenUsage(10, 10))));

    SessionContext context = ganglia.sessionManager().createSession(UUID.randomUUID().toString());

    ganglia
        .agentLoop()
        .run("Find three numbers and sum them up", context)
        .onComplete(
            testContext.succeeding(
                result -> {
                  testContext.verify(
                      () -> {
                        assertTrue(
                            result.contains("60"),
                            "Result should contain the sum 60. Got: " + result);
                        testContext.completeNow();
                      });
                }));
  }
}
