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
import java.nio.file.Files;
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
public class MemoryRetrievalIT {

  private Ganglia ganglia;
  private ModelGateway mockModel;

  @TempDir Path sharedTempDir;

  @BeforeEach
  void setUp(Vertx vertx, VertxTestContext testContext) {
    mockModel = mock(ModelGateway.class);
    when(mockModel.chat(any(ChatRequest.class)))
        .thenReturn(Future.failedFuture("Reflection disabled in tests"));

    String projectRoot = sharedTempDir.toAbsolutePath().toString();

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
  void testAgentRetrievesFromMemory(Vertx vertx, VertxTestContext testContext) {
    Path memoryPath = sharedTempDir.resolve(".ganglia/memory/MEMORY.md");
    try {
      Files.createDirectories(memoryPath.getParent());
    } catch (Exception e) {
      testContext.failNow(e);
      return;
    }
    String memoryFile = memoryPath.toString();
    String secret = "The secret code is 998877";
    vertx.fileSystem().writeFileBlocking(memoryFile, Buffer.buffer(secret));

    // Mock Tool Calls: grep for secret in the memory file
    ToolCall grepCall =
        new ToolCall(
            "c1",
            "grep_search",
            Map.of("path", sharedTempDir.toString(), "pattern", "secret code"));

    when(mockModel.chatStream(any(ChatRequest.class), any()))
        .thenReturn(
            Future.succeededFuture(
                new ModelResponse("Searching memory...", List.of(grepCall), new TokenUsage(1, 1))))
        .thenReturn(
            Future.succeededFuture(
                new ModelResponse(
                    "The code is 998877.", Collections.emptyList(), new TokenUsage(1, 1))));

    SessionContext context = ganglia.sessionManager().createSession(UUID.randomUUID().toString());

    ganglia
        .agentLoop()
        .run("Find the secret code in " + sharedTempDir, context)
        .onComplete(
            testContext.succeeding(
                (String result) -> {
                  testContext.verify(
                      () -> {
                        assertTrue(result.contains("998877"));
                        testContext.completeNow();
                      });
                }));
  }
}
