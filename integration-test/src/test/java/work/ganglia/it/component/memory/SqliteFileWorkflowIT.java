package work.ganglia.it.component.memory;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import io.vertx.junit5.VertxTestContext;

import work.ganglia.it.support.SqliteModelIT;
import work.ganglia.port.chat.SessionContext;
import work.ganglia.port.external.llm.ChatRequest;
import work.ganglia.port.external.llm.ModelResponse;
import work.ganglia.port.external.tool.ToolCall;
import work.ganglia.port.internal.state.TokenUsage;

/**
 * Verifies that standard file operations (write, read) work correctly when the agent is backed by
 * SQLite memory rather than filesystem memory. This ensures the memory backend swap does not break
 * core tool functionality.
 */
public class SqliteFileWorkflowIT extends SqliteModelIT {

  @Test
  void writeAndReadFile_withSqliteBackend(Vertx vertx, VertxTestContext testContext) {
    String testFile = "hello.txt";
    String content = "Hello from SQLite-backed agent!";

    ToolCall writeCall =
        new ToolCall("c1", "write_file", Map.of("file_path", testFile, "content", content));

    String readPath;
    try {
      readPath = tempDir.toRealPath().resolve(testFile).toString();
    } catch (Exception e) {
      testContext.failNow(e);
      return;
    }
    ToolCall readCall = new ToolCall("c2", "read_file", Map.of("path", readPath));

    when(mockModel.chatStream(any(ChatRequest.class), any()))
        .thenReturn(
            Future.succeededFuture(
                new ModelResponse("Writing file.", List.of(writeCall), new TokenUsage(1, 1))))
        .thenReturn(
            Future.succeededFuture(
                new ModelResponse("Reading back.", List.of(readCall), new TokenUsage(1, 1))))
        .thenReturn(
            Future.succeededFuture(
                new ModelResponse(
                    "File verified.", Collections.emptyList(), new TokenUsage(1, 1))));

    SessionContext context = newSession();

    ganglia
        .agentLoop()
        .run("Write hello.txt and read it back.", context)
        .onComplete(
            testContext.succeeding(
                result ->
                    testContext.verify(
                        () -> {
                          try {
                            Path filePath = tempDir.toRealPath().resolve(testFile);
                            assertTrue(Files.exists(filePath), "File should exist: " + filePath);
                            assertEquals(
                                content, Files.readString(filePath), "File content mismatch");
                          } catch (Exception e) {
                            throw new RuntimeException(e);
                          }
                          testContext.completeNow();
                        })));
  }
}
