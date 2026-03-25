package work.ganglia.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.junit5.VertxTestContext;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import work.ganglia.kernel.todo.TaskStatus;
import work.ganglia.kernel.todo.ToDoList;
import work.ganglia.port.chat.SessionContext;
import work.ganglia.port.external.llm.ChatRequest;
import work.ganglia.port.external.llm.ModelResponse;
import work.ganglia.port.external.tool.ToolCall;
import work.ganglia.port.internal.state.TokenUsage;

/**
 * Integration test verifying the full TODO workflow: add tasks → execute → complete → summarize.
 *
 * <p>Simulates a 6-turn agent conversation where the agent decomposes a complex task into TODO
 * items, executes each sub-task, marks them complete (triggering context compression), and produces
 * a final summary.
 */
public class ToDoWorkflowIT extends MockModelIT {

  @Test
  void todoWorkflow_addExecuteCompleteAndSummarize(Vertx vertx, VertxTestContext testContext)
      throws Exception {
    String projectRoot = tempDir.toRealPath().toString();

    // Turn 1: Agent decomposes the task into two TODO items
    ToolCall addTask1 =
        new ToolCall("c1", "todo_add", Map.of("description", "Write greeting file"));
    ToolCall addTask2 = new ToolCall("c2", "todo_add", Map.of("description", "Read greeting file"));

    // Turn 2: Agent executes first sub-task — write a file
    ToolCall writeFile =
        new ToolCall(
            "c3", "write_file", Map.of("file_path", "greeting.txt", "content", "Hello World"));

    // Turn 3: Agent marks first task complete (triggers context compression)
    ToolCall completeTask1 = new ToolCall("c4", "todo_complete", Map.of("id", "1"));

    // Turn 4: Agent executes second sub-task — read the file
    ToolCall readFile =
        new ToolCall("c5", "read_file", Map.of("path", projectRoot + "/greeting.txt"));

    // Turn 5: Agent marks second task complete (triggers context compression)
    ToolCall completeTask2 = new ToolCall("c6", "todo_complete", Map.of("id", "2"));

    // Turn 6: Agent returns final summary (no tool calls → exits loop)

    when(mockModel.chatStream(any(ChatRequest.class), any()))
        .thenReturn(
            Future.succeededFuture(
                new ModelResponse(
                    "I'll decompose this into tasks.",
                    List.of(addTask1, addTask2),
                    new TokenUsage(10, 10))))
        .thenReturn(
            Future.succeededFuture(
                new ModelResponse(
                    "Writing the greeting file.", List.of(writeFile), new TokenUsage(10, 10))))
        .thenReturn(
            Future.succeededFuture(
                new ModelResponse(
                    "First task done.", List.of(completeTask1), new TokenUsage(10, 10))))
        .thenReturn(
            Future.succeededFuture(
                new ModelResponse(
                    "Reading the greeting file.", List.of(readFile), new TokenUsage(10, 10))))
        .thenReturn(
            Future.succeededFuture(
                new ModelResponse(
                    "Second task done.", List.of(completeTask2), new TokenUsage(10, 10))))
        .thenReturn(
            Future.succeededFuture(
                new ModelResponse(
                    "All tasks completed successfully. The greeting file has been written and verified.",
                    Collections.emptyList(),
                    new TokenUsage(10, 10))));

    // Override chat() for the compressor.summarize() calls triggered by todo_complete
    when(mockModel.chat(any(ChatRequest.class)))
        .thenReturn(
            Future.succeededFuture(
                new ModelResponse(
                    "Wrote greeting.txt with Hello World.",
                    Collections.emptyList(),
                    new TokenUsage(5, 5))))
        .thenReturn(
            Future.succeededFuture(
                new ModelResponse(
                    "Read greeting.txt and verified content.",
                    Collections.emptyList(),
                    new TokenUsage(5, 5))));

    SessionContext context = newSession();
    String sessionId = context.sessionId();

    ganglia
        .agentLoop()
        .run("Write a greeting file and then read it back to verify the content.", context)
        .compose(response -> ganglia.sessionManager().getSession(sessionId))
        .onComplete(
            testContext.succeeding(
                resultContext ->
                    testContext.verify(
                        () -> {
                          // 1. Verify file was created with correct content
                          Path greetingFile = tempDir.toRealPath().resolve("greeting.txt");
                          assertTrue(
                              Files.exists(greetingFile), "greeting.txt should have been created");
                          assertEquals(
                              "Hello World",
                              Files.readString(greetingFile),
                              "greeting.txt content mismatch");

                          // 2. Verify ToDoList in session metadata
                          // After JSON round-trip, metadata values are raw maps;
                          // convert back to ToDoList via Jackson
                          Object todoObj = resultContext.metadata().get("todo_list");
                          assertNotNull(todoObj, "todo_list should exist in session metadata");

                          ObjectMapper mapper = new ObjectMapper();
                          ToDoList todoList = mapper.convertValue(todoObj, ToDoList.class);
                          assertEquals(2, todoList.items().size(), "Should have 2 tasks");

                          // 3. Verify both tasks are DONE
                          assertEquals(
                              TaskStatus.DONE,
                              todoList.items().get(0).status(),
                              "Task 1 should be DONE");
                          assertEquals(
                              TaskStatus.DONE,
                              todoList.items().get(1).status(),
                              "Task 2 should be DONE");

                          // 4. Verify each completed task has a compression summary
                          assertNotNull(
                              todoList.items().get(0).result(),
                              "Task 1 should have a compression summary");
                          assertNotNull(
                              todoList.items().get(1).result(),
                              "Task 2 should have a compression summary");

                          testContext.completeNow();
                        })));
  }
}
