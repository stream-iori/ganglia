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
public class SubAgentGraphIT {

  private Ganglia ganglia;
  private ModelGateway mockModel;

  @BeforeEach
  void setUp(Vertx vertx, VertxTestContext testContext, @TempDir Path tempDir)
      throws java.io.IOException {
    mockModel = mock(ModelGateway.class);
    // Default failure for bootstrap
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
  void testSequentialGraphExecution(Vertx vertx, VertxTestContext testContext) {
    // Mock Tool Call: execute a DAG
    ToolCall graphCall =
        new ToolCall(
            "c1",
            "propose_task_graph",
            Map.of(
                "nodes",
                List.of(
                    Map.of("id", "n1", "task", "Task 1", "persona", "GENERAL"),
                    Map.of(
                        "id",
                        "n2",
                        "task",
                        "Task 2",
                        "persona",
                        "GENERAL",
                        "dependencies",
                        List.of("n1"))),
                "approved",
                true));

    when(mockModel.chatStream(any(ChatRequest.class), any()))
        .thenReturn(
            Future.succeededFuture(
                new ModelResponse(
                    "Executing graph...", List.of(graphCall), new TokenUsage(10, 10))))
        .thenReturn(
            Future.succeededFuture(
                new ModelResponse("Task 1 Done", Collections.emptyList(), new TokenUsage(5, 5))))
        .thenReturn(
            Future.succeededFuture(
                new ModelResponse("Task 2 Done", Collections.emptyList(), new TokenUsage(5, 5))))
        .thenReturn(
            Future.succeededFuture(
                new ModelResponse(
                    "Graph completed successfully.",
                    Collections.emptyList(),
                    new TokenUsage(10, 10))));

    SessionContext context = ganglia.sessionManager().createSession(UUID.randomUUID().toString());

    ganglia
        .agentLoop()
        .run("Run a sequence of tasks", context)
        .onComplete(
            testContext.succeeding(
                result -> {
                  testContext.verify(
                      () -> {
                        assertTrue(result.contains("successfully"));
                        testContext.completeNow();
                      });
                }));
  }
}
