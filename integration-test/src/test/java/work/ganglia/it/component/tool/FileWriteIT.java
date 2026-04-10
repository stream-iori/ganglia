package work.ganglia.it.component.tool;

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

import work.ganglia.it.support.MockModelIT;
import work.ganglia.port.chat.SessionContext;
import work.ganglia.port.external.llm.ChatRequest;
import work.ganglia.port.external.llm.ModelResponse;
import work.ganglia.port.external.tool.ToolCall;
import work.ganglia.port.internal.state.TokenUsage;

public class FileWriteIT extends MockModelIT {

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
}
