package work.ganglia.it.component.context;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

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

public class LargeOutputHandlingIT extends MockModelIT {

  @Test
  void largeReproducibleOutput_savedToTmpFileWithHint(Vertx vertx, VertxTestContext testContext) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < 500; i++) {
      sb.append("Line ").append(i).append(" data\n");
    }
    String longOutput = sb.toString();
    String projectRoot = tempDir.toAbsolutePath().toString();

    try {
      vertx.fileSystem().writeFileBlocking(projectRoot + "/long.txt", Buffer.buffer(longOutput));
    } catch (Exception e) {
      testContext.failNow(e);
      return;
    }

    ToolCall readFileCall =
        new ToolCall("c1", "read_file", Map.of("path", projectRoot + "/long.txt"));
    when(mockModel.chatStream(any(), any()))
        .thenReturn(
            Future.succeededFuture(
                new ModelResponse("", List.of(readFileCall), new TokenUsage(1, 1))))
        .thenAnswer(
            invocation -> {
              ChatRequest req = invocation.getArgument(0);
              String lastMessage = req.messages().get(req.messages().size() - 1).content();
              assertTrue(
                  lastMessage.contains("[Output from 'read_file' was large"),
                  "Expected tmp file hint, got: "
                      + lastMessage.substring(0, Math.min(200, lastMessage.length())));
              assertTrue(
                  lastMessage.contains("read_file("),
                  "Hint should suggest re-reading via read_file");
              return Future.succeededFuture(
                  new ModelResponse("Done.", Collections.emptyList(), new TokenUsage(1, 1)));
            });

    SessionContext context = newSession();
    ganglia
        .agentLoop()
        .run("Read long.txt", context)
        .onComplete(testContext.succeeding(result -> testContext.verify(testContext::completeNow)));
  }
}
