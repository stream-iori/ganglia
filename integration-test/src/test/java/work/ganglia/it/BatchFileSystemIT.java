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
public class BatchFileSystemIT {

  private Ganglia ganglia;
  private ModelGateway mockModel;
  private Path tempDir;

  @BeforeEach
  void setUp(Vertx vertx, VertxTestContext testContext, @TempDir Path tempDir)
      throws java.io.IOException {
    this.tempDir = tempDir;
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
  void testBatchFileCreation(Vertx vertx, VertxTestContext testContext) {
    ToolCall call1 =
        new ToolCall("c1", "write_file", Map.of("file_path", "file1.txt", "content", "content1"));
    ToolCall call2 =
        new ToolCall("c2", "write_file", Map.of("file_path", "file2.txt", "content", "content2"));

    when(mockModel.chatStream(any(ChatRequest.class), any()))
        .thenReturn(
            Future.succeededFuture(
                new ModelResponse("Creating files.", List.of(call1, call2), new TokenUsage(1, 1))))
        .thenReturn(
            Future.succeededFuture(
                new ModelResponse("Done.", Collections.emptyList(), new TokenUsage(1, 1))));

    SessionContext context = ganglia.sessionManager().createSession(UUID.randomUUID().toString());

    ganglia
        .agentLoop()
        .run("Create two files", context)
        .onComplete(
            testContext.succeeding(
                result -> {
                  testContext.verify(
                      () -> {
                        try {
                          Path root = tempDir.toRealPath();
                          assertTrue(java.nio.file.Files.exists(root.resolve("file1.txt")));
                          assertTrue(java.nio.file.Files.exists(root.resolve("file2.txt")));
                          testContext.completeNow();
                        } catch (java.io.IOException e) {
                          testContext.failNow(e);
                        }
                      });
                }));
  }
}
