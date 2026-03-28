package work.ganglia.it;

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
import io.vertx.core.buffer.Buffer;
import io.vertx.junit5.VertxTestContext;

import work.ganglia.port.chat.Role;
import work.ganglia.port.chat.SessionContext;
import work.ganglia.port.external.llm.ChatRequest;
import work.ganglia.port.external.llm.ModelResponse;
import work.ganglia.port.external.tool.ToolCall;
import work.ganglia.port.internal.state.TokenUsage;

public class FileOperationsIT extends MockModelIT {

  @Test
  void writeFile_createsNewFileWithContent(Vertx vertx, VertxTestContext testContext) {
    String testFile = "test.py";
    String content = "def hello():\n    print(\"Hello World\")\n";

    ToolCall writeCall =
        new ToolCall("c1", "write_file", Map.of("file_path", testFile, "content", content));

    when(mockModel.chatStream(any(ChatRequest.class), any()))
        .thenReturn(
            Future.succeededFuture(
                new ModelResponse("Writing file.", List.of(writeCall), new TokenUsage(1, 1))))
        .thenReturn(
            Future.succeededFuture(
                new ModelResponse("Done.", Collections.emptyList(), new TokenUsage(1, 1))));

    SessionContext context = newSession();

    ganglia
        .agentLoop()
        .run("Write a python hello world function to test.py", context)
        .onComplete(
            testContext.succeeding(
                result ->
                    testContext.verify(
                        () -> {
                          Path root = tempDir.toRealPath();
                          assertTrue(Files.exists(root.resolve(testFile)));
                          assertEquals(content, Files.readString(root.resolve(testFile)));
                          testContext.completeNow();
                        })));
  }

  @Test
  void replaceInFile_updatesSurgically(Vertx vertx, VertxTestContext testContext) {
    String testFile = "main.py";
    String originalContent = "line1\nline2\nline3\n";
    String newString = "line2_updated\n";

    try {
      Files.writeString(tempDir.resolve(testFile), originalContent);
    } catch (java.io.IOException e) {
      testContext.failNow(e);
      return;
    }

    ToolCall editCall =
        new ToolCall(
            "c1",
            "replace_in_file",
            Map.of(
                "file_path", testFile,
                "old_string", "line2\n",
                "new_string", newString));

    when(mockModel.chatStream(any(ChatRequest.class), any()))
        .thenReturn(
            Future.succeededFuture(
                new ModelResponse("Editing file.", List.of(editCall), new TokenUsage(1, 1))))
        .thenReturn(
            Future.succeededFuture(
                new ModelResponse("Done.", Collections.emptyList(), new TokenUsage(1, 1))));

    SessionContext context = newSession();

    ganglia
        .agentLoop()
        .run("Update line2 in main.py", context)
        .onComplete(
            testContext.succeeding(
                result ->
                    testContext.verify(
                        () -> {
                          String finalContent = Files.readString(tempDir.resolve(testFile));
                          assertTrue(finalContent.contains("line2_updated"));
                          testContext.completeNow();
                        })));
  }

  @Test
  void applyPatch_modifiesViaUnifiedDiff(Vertx vertx, VertxTestContext testContext) {
    String testFile = "app.java";
    String originalContent = "public class App {}\n";
    String patch =
        "--- app.java\n+++ app.java\n@@ -1,1 +1,1 @@\n-public class App {}\n+public class App { public static void main(String[] args) {} }\n";

    try {
      Files.writeString(tempDir.resolve(testFile), originalContent);
    } catch (java.io.IOException e) {
      testContext.failNow(e);
      return;
    }

    ToolCall patchCall =
        new ToolCall("c1", "apply_patch", Map.of("file_path", testFile, "patch", patch));

    when(mockModel.chatStream(any(ChatRequest.class), any()))
        .thenReturn(
            Future.succeededFuture(
                new ModelResponse("Applying patch.", List.of(patchCall), new TokenUsage(1, 1))))
        .thenReturn(
            Future.succeededFuture(
                new ModelResponse("Done.", Collections.emptyList(), new TokenUsage(1, 1))));

    SessionContext context = newSession();

    ganglia
        .agentLoop()
        .run("Apply patch to app.java", context)
        .onComplete(
            testContext.succeeding(
                result ->
                    testContext.verify(
                        () -> {
                          String finalContent = Files.readString(tempDir.resolve(testFile));
                          assertTrue(finalContent.contains("main"));
                          testContext.completeNow();
                        })));
  }

  @Test
  void parallelWrite_createsTwoFilesSimultaneously(Vertx vertx, VertxTestContext testContext) {
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

    SessionContext context = newSession();

    ganglia
        .agentLoop()
        .run("Create two files", context)
        .onComplete(
            testContext.succeeding(
                result ->
                    testContext.verify(
                        () -> {
                          Path root = tempDir.toRealPath();
                          assertTrue(Files.exists(root.resolve("file1.txt")));
                          assertTrue(Files.exists(root.resolve("file2.txt")));
                          testContext.completeNow();
                        })));
  }

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
                          // read_file is a reproducible tool → WRITE_TO_TMP policy:
                          // full output is saved to a session tmp file, and the TOOL message
                          // in context is replaced with a path + line-count hint.
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
