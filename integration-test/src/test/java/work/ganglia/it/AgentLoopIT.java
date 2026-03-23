package work.ganglia.it;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
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
public class AgentLoopIT {

  private Ganglia ganglia;
  private ModelGateway mockModel;
  private Path tempDir;

  @BeforeEach
  void setUp(Vertx vertx, VertxTestContext testContext, @TempDir Path tempDir)
      throws java.io.IOException {
    this.tempDir = tempDir;
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
  void testConsecutiveToolFailuresAbortLoop(Vertx vertx, VertxTestContext testContext) {
    // 3 consecutive calls to read a nonexistent file — should trigger ConsecutiveFailurePolicy
    ToolCall read1 = new ToolCall("c1", "read_file", Map.of("path", "no_such_file_1.txt"));
    ToolCall read2 = new ToolCall("c2", "read_file", Map.of("path", "no_such_file_2.txt"));
    ToolCall read3 = new ToolCall("c3", "read_file", Map.of("path", "no_such_file_3.txt"));

    when(mockModel.chatStream(any(ChatRequest.class), any()))
        .thenReturn(
            Future.succeededFuture(
                new ModelResponse("Reading file 1.", List.of(read1), new TokenUsage(1, 1))))
        .thenReturn(
            Future.succeededFuture(
                new ModelResponse("Reading file 2.", List.of(read2), new TokenUsage(1, 1))))
        .thenReturn(
            Future.succeededFuture(
                new ModelResponse("Reading file 3.", List.of(read3), new TokenUsage(1, 1))));

    SessionContext context = ganglia.sessionManager().createSession(UUID.randomUUID().toString());

    ganglia
        .agentLoop()
        .run("Read three missing files", context)
        .onComplete(
            testContext.failing(
                err -> {
                  testContext.verify(
                      () -> {
                        assertTrue(err.getMessage().contains("repetitive task failures"));
                        testContext.completeNow();
                      });
                }));
  }

  @Test
  void testFileConcatenation(Vertx vertx, VertxTestContext testContext) {
    String file1 = tempDir.resolve("file1.txt").toString();
    String file2 = tempDir.resolve("file2.txt").toString();
    vertx.fileSystem().writeFileBlocking(file1, Buffer.buffer("Part 1"));
    vertx.fileSystem().writeFileBlocking(file2, Buffer.buffer("Part 2"));

    // Mock Tool Calls: list, then read 1, then read 2
    ToolCall listCall = new ToolCall("c1", "list_directory", Map.of("path", "."));
    ToolCall readCall1 = new ToolCall("c2", "read_file", Map.of("path", "file1.txt"));
    ToolCall readCall2 = new ToolCall("c3", "read_file", Map.of("path", "file2.txt"));

    when(mockModel.chatStream(any(ChatRequest.class), any()))
        .thenReturn(
            Future.succeededFuture(
                new ModelResponse("Listing files.", List.of(listCall), new TokenUsage(1, 1))))
        .thenReturn(
            Future.succeededFuture(
                new ModelResponse("Reading file 1.", List.of(readCall1), new TokenUsage(1, 1))))
        .thenReturn(
            Future.succeededFuture(
                new ModelResponse("Reading file 2.", List.of(readCall2), new TokenUsage(1, 1))))
        .thenReturn(
            Future.succeededFuture(
                new ModelResponse(
                    "The content is: Part 1 Part 2",
                    Collections.emptyList(),
                    new TokenUsage(1, 1))));

    SessionContext context = ganglia.sessionManager().createSession(UUID.randomUUID().toString());

    ganglia
        .agentLoop()
        .run("Concatenate file1.txt and file2.txt", context)
        .onComplete(
            testContext.succeeding(
                (String result) -> {
                  testContext.verify(
                      () -> {
                        assertTrue(result.contains("Part 1 Part 2"));
                        testContext.completeNow();
                      });
                }));
  }
}
