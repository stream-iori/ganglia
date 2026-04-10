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
import work.ganglia.port.chat.SessionContext;
import work.ganglia.port.external.llm.ChatRequest;
import work.ganglia.port.external.llm.ModelResponse;
import work.ganglia.port.external.tool.ToolCall;
import work.ganglia.port.internal.state.TokenUsage;

public class FileDiscoveryIT extends MockModelIT {

  @Test
  void listAndGrep_discoversCodePattern(Vertx vertx, VertxTestContext testContext) {
    String testFile = tempDir.resolve("discovery_test.java").toString();
    String content =
        "public class DiscoveryTest { public void hello() { System.out.println(\"SECRET_CODE_123\"); } }";
    vertx.fileSystem().writeFileBlocking(testFile, Buffer.buffer(content));

    ToolCall listCall = new ToolCall("c1", "list_directory", Map.of("path", tempDir.toString()));
    ToolCall grepCall =
        new ToolCall(
            "c2", "grep_search", Map.of("path", tempDir.toString(), "pattern", "SECRET_CODE_123"));

    when(mockModel.chatStream(any(ChatRequest.class), any()))
        .thenReturn(
            Future.succeededFuture(
                new ModelResponse("Listing files...", List.of(listCall), new TokenUsage(1, 1))))
        .thenReturn(
            Future.succeededFuture(
                new ModelResponse("Searching code...", List.of(grepCall), new TokenUsage(1, 1))))
        .thenReturn(
            Future.succeededFuture(
                new ModelResponse(
                    "Found the secret code in discovery_test.java",
                    Collections.emptyList(),
                    new TokenUsage(1, 1))));

    SessionContext context = newSession();

    ganglia
        .agentLoop()
        .run("Find the secret code 'SECRET_CODE_123' in " + tempDir, context)
        .onComplete(
            testContext.succeeding(
                result ->
                    testContext.verify(
                        () -> {
                          assertTrue(result.contains("discovery_test.java"));
                          testContext.completeNow();
                        })));
  }
}
