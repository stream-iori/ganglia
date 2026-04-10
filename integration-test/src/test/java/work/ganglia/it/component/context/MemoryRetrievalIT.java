package work.ganglia.it.component.context;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.junit5.VertxTestContext;

import work.ganglia.it.support.MockModelIT;
import work.ganglia.port.chat.SessionContext;
import work.ganglia.port.external.llm.ChatRequest;
import work.ganglia.port.external.llm.ModelResponse;
import work.ganglia.port.external.tool.ToolCall;
import work.ganglia.port.internal.state.TokenUsage;

public class MemoryRetrievalIT extends MockModelIT {

  @Test
  void memoryRetrieval_findsFactInMemoryFile(Vertx vertx, VertxTestContext testContext) {
    Path memoryPath = tempDir.resolve(".ganglia/memory/MEMORY.md");
    try {
      Files.createDirectories(memoryPath.getParent());
    } catch (Exception e) {
      testContext.failNow(e);
      return;
    }
    String secret = "The secret code is 998877";
    vertx.fileSystem().writeFileBlocking(memoryPath.toString(), Buffer.buffer(secret));

    ToolCall grepCall =
        new ToolCall(
            "c1", "grep_search", Map.of("path", tempDir.toString(), "pattern", "secret code"));

    when(mockModel.chatStream(any(ChatRequest.class), any()))
        .thenReturn(
            Future.succeededFuture(
                new ModelResponse("Searching memory...", List.of(grepCall), new TokenUsage(1, 1))))
        .thenReturn(
            Future.succeededFuture(
                new ModelResponse(
                    "The code is 998877.", Collections.emptyList(), new TokenUsage(1, 1))));

    SessionContext context = newSession();

    ganglia
        .agentLoop()
        .run("Find the secret code in " + tempDir, context)
        .onComplete(
            testContext.succeeding(
                (String result) ->
                    testContext.verify(
                        () -> {
                          assertTrue(result.contains("998877"));
                          testContext.completeNow();
                        })));
  }
}
