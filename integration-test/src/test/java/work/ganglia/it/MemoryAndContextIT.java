package work.ganglia.it;

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

import work.ganglia.port.chat.Message;
import work.ganglia.port.chat.SessionContext;
import work.ganglia.port.external.llm.ChatRequest;
import work.ganglia.port.external.llm.ModelResponse;
import work.ganglia.port.external.tool.ToolCall;
import work.ganglia.port.internal.state.TokenUsage;

public class MemoryAndContextIT extends MockModelIT {

  @Test
  void observationCompression_compressesLargeOutputAndRecalls(
      Vertx vertx, VertxTestContext testContext) {
    // Each line is ~10 chars. 500 lines = 5000 chars.
    // 5000 > 4000 (compression threshold).
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
              assertTrue(lastMessage.contains("ID: "));
              String id = lastMessage.split("ID: ")[1].split("\\.")[0].trim();
              return Future.succeededFuture(
                  new ModelResponse(
                      "",
                      List.of(new ToolCall("c2", "recall_memory", Map.of("id", id))),
                      new TokenUsage(1, 1)));
            })
        .thenReturn(
            Future.succeededFuture(
                new ModelResponse("Done.", Collections.emptyList(), new TokenUsage(1, 1))));

    when(mockModel.chat(any()))
        .thenReturn(
            Future.succeededFuture(
                new ModelResponse(
                    "Summary: Found long text.", Collections.emptyList(), new TokenUsage(5, 5))));

    SessionContext context = newSession();
    ganglia
        .agentLoop()
        .run("Read long.txt", context)
        .onComplete(testContext.succeeding(result -> testContext.verify(testContext::completeNow)));
  }

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

  @Test
  void historyCompression_triggersOnLargeHistory(Vertx vertx, VertxTestContext testContext) {
    SessionContext context = newSession();

    for (int i = 0; i < 10; i++) {
      context = context.withNewMessage(Message.user("User message " + i));
      context =
          context.withNewMessage(
              Message.assistant("Assistant message " + i, Collections.emptyList()));
    }

    when(mockModel.chat(any(ChatRequest.class)))
        .thenReturn(
            Future.succeededFuture(
                new ModelResponse(
                    "Summarized history.", Collections.emptyList(), new TokenUsage(10, 10))));

    assertTrue(context.history().size() >= 20);
    testContext.completeNow();
  }
}
