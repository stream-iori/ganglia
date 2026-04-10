package work.ganglia.it.component.tool;

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
import work.ganglia.port.chat.Role;
import work.ganglia.port.chat.SessionContext;
import work.ganglia.port.external.llm.ChatRequest;
import work.ganglia.port.external.llm.ModelResponse;
import work.ganglia.port.external.tool.ToolCall;
import work.ganglia.port.internal.state.TokenUsage;

public class FileReadIT extends MockModelIT {

  @Test
  void readFile_returnsPaginationMetadata(Vertx vertx, VertxTestContext testContext) {
    String testFilePath = tempDir.resolve("large_file.txt").toString();
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < 20; i++) {
      sb.append("Line ").append(i).append("\n");
    }
    vertx.fileSystem().writeFileBlocking(testFilePath, Buffer.buffer(sb.toString()));

    ToolCall readCall =
        new ToolCall(
            "call_1", "read_file", Map.of("path", testFilePath, "start_line", 1, "end_line", 5));

    when(mockModel.chatStream(any(ChatRequest.class), any()))
        .thenReturn(
            Future.succeededFuture(
                new ModelResponse(
                    "Reading first part of the file.", List.of(readCall), new TokenUsage(10, 10))))
        .thenReturn(
            Future.succeededFuture(
                new ModelResponse(
                    "I have read the first 5 lines.",
                    Collections.emptyList(),
                    new TokenUsage(10, 10))));

    SessionContext context = newSession();
    String sessionId = context.sessionId();

    ganglia
        .agentLoop()
        .run("Read the first 5 lines of large_file.txt", context)
        .compose(response -> ganglia.sessionManager().getSession(sessionId))
        .onComplete(
            testContext.succeeding(
                resultContext ->
                    testContext.verify(
                        () -> {
                          boolean foundTmpHint =
                              resultContext.history().stream()
                                  .anyMatch(
                                      m ->
                                          m.role() == Role.TOOL
                                              && m.content() != null
                                              && m.content()
                                                  .contains("[Output from 'read_file' was large"));

                          assertTrue(
                              foundTmpHint,
                              "Tmp file hint not found in history: " + resultContext.history());
                          testContext.completeNow();
                        })));
  }
}
