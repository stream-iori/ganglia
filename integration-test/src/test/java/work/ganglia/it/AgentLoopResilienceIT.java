package work.ganglia.it;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.junit5.VertxTestContext;

import work.ganglia.kernel.loop.AgentAbortedException;
import work.ganglia.kernel.loop.AgentLoopObserver;
import work.ganglia.kernel.loop.DefaultObservationDispatcher;
import work.ganglia.kernel.loop.ReActAgentLoop;
import work.ganglia.port.chat.SessionContext;
import work.ganglia.port.external.llm.ChatRequest;
import work.ganglia.port.external.llm.ModelResponse;
import work.ganglia.port.external.tool.ObservationType;
import work.ganglia.port.external.tool.ToolCall;
import work.ganglia.port.internal.state.AgentSignal;
import work.ganglia.port.internal.state.TokenUsage;

public class AgentLoopResilienceIT extends MockModelIT {

  @Test
  void basicWorkflow_completesListDirectoryTask(Vertx vertx, VertxTestContext testContext) {
    ToolCall call = new ToolCall("c1", "list_directory", Map.of("path", "."));

    when(mockModel.chatStream(any(ChatRequest.class), any()))
        .thenReturn(
            Future.succeededFuture(
                new ModelResponse("I will list files.", List.of(call), new TokenUsage(1, 1))))
        .thenReturn(
            Future.succeededFuture(
                new ModelResponse(
                    "Workflow complete.", Collections.emptyList(), new TokenUsage(1, 1))));

    SessionContext context = newSession();

    ganglia
        .agentLoop()
        .run("Run complete workflow", context)
        .onComplete(
            testContext.succeeding(
                result ->
                    testContext.verify(
                        () -> {
                          assertTrue(result.contains("complete"));
                          testContext.completeNow();
                        })));
  }

  @Test
  void fileConcatenation_readsAndCombinesMultipleFiles(Vertx vertx, VertxTestContext testContext) {
    String file1 = tempDir.resolve("file1.txt").toString();
    String file2 = tempDir.resolve("file2.txt").toString();
    vertx.fileSystem().writeFileBlocking(file1, Buffer.buffer("Part 1"));
    vertx.fileSystem().writeFileBlocking(file2, Buffer.buffer("Part 2"));

    ToolCall listCall = new ToolCall("c1", "list_directory", Map.of("path", "."));
    ToolCall readCall1 = new ToolCall("c2", "read_file", Map.of("path", "file1.txt"));
    ToolCall readCall2 = new ToolCall("c3", "read_file", Map.of("path", "file2.txt"));

    when(mockModel.chatStream(any(ChatRequest.class), any()))
        .thenReturn(
            Future.succeededFuture(
                new ModelResponse("Listing files.", List.of(listCall), new TokenUsage(1, 1))))
        .thenReturn(
            Future.succeededFuture(
                new ModelResponse("Reading file 1.", List.of(readCall1), new TokenUsage(1, 1))))
        .thenReturn(
            Future.succeededFuture(
                new ModelResponse("Reading file 2.", List.of(readCall2), new TokenUsage(1, 1))))
        .thenReturn(
            Future.succeededFuture(
                new ModelResponse(
                    "The content is: Part 1 Part 2",
                    Collections.emptyList(),
                    new TokenUsage(1, 1))));

    SessionContext context = newSession();

    ganglia
        .agentLoop()
        .run("Concatenate file1.txt and file2.txt", context)
        .onComplete(
            testContext.succeeding(
                (String result) ->
                    testContext.verify(
                        () -> {
                          assertTrue(result.contains("Part 1 Part 2"));
                          testContext.completeNow();
                        })));
  }

  @Test
  void abortSignal_doesNotPublishErrorObservation(Vertx vertx, VertxTestContext testContext) {
    SessionContext context = newSession();
    AgentSignal signal = new AgentSignal();

    AtomicBoolean errorObserved = new AtomicBoolean(false);
    ReActAgentLoop loop = ganglia.agentLoop();
    if (loop.getDispatcher() instanceof DefaultObservationDispatcher dod) {
      dod.register(
          new AgentLoopObserver() {
            @Override
            public void onObservation(
                String sessionId, ObservationType type, String content, Map<String, Object> data) {
              if (type == ObservationType.ERROR) {
                errorObserved.set(true);
              }
            }

            @Override
            public void onUsageRecorded(
                String sessionId, work.ganglia.port.internal.state.TokenUsage usage) {}
          });
    }

    signal.abort();
    ganglia
        .agentLoop()
        .run("Abort me", context, signal)
        .onComplete(
            testContext.failing(
                err ->
                    testContext.verify(
                        () -> {
                          assertTrue(err instanceof AgentAbortedException);
                          assertTrue(
                              !errorObserved.get(),
                              "Should NOT publish ERROR observation for AgentAbortedException IT");
                          testContext.completeNow();
                        })));
  }

  @Test
  void consecutiveToolFailures_abortsWithPolicyError(Vertx vertx, VertxTestContext testContext) {
    ToolCall read1 = new ToolCall("c1", "read_file", Map.of("path", "no_such_file_1.txt"));
    ToolCall read2 = new ToolCall("c2", "read_file", Map.of("path", "no_such_file_2.txt"));
    ToolCall read3 = new ToolCall("c3", "read_file", Map.of("path", "no_such_file_3.txt"));

    when(mockModel.chatStream(any(ChatRequest.class), any()))
        .thenReturn(
            Future.succeededFuture(
                new ModelResponse("Reading file 1.", List.of(read1), new TokenUsage(1, 1))))
        .thenReturn(
            Future.succeededFuture(
                new ModelResponse("Reading file 2.", List.of(read2), new TokenUsage(1, 1))))
        .thenReturn(
            Future.succeededFuture(
                new ModelResponse("Reading file 3.", List.of(read3), new TokenUsage(1, 1))));

    SessionContext context = newSession();

    ganglia
        .agentLoop()
        .run("Read three missing files", context)
        .onComplete(
            testContext.failing(
                err ->
                    testContext.verify(
                        () -> {
                          assertTrue(err.getMessage().contains("repetitive task failures"));
                          testContext.completeNow();
                        })));
  }

  @Test
  void networkFailure_retriesAndSucceeds(Vertx vertx, VertxTestContext testContext) {
    when(mockModel.chatStream(any(ChatRequest.class), any()))
        .thenReturn(Future.failedFuture(new java.io.IOException("Temporary error")))
        .thenReturn(
            Future.succeededFuture(
                new ModelResponse(
                    "Success after retry.", Collections.emptyList(), new TokenUsage(1, 1))));

    SessionContext context = newSession();

    ganglia
        .agentLoop()
        .run("Test retry", context)
        .onComplete(
            testContext.succeeding(
                result ->
                    testContext.verify(
                        () -> {
                          assertTrue(result.contains("Success"));
                          verify(mockModel, times(2)).chatStream(any(ChatRequest.class), any());
                          testContext.completeNow();
                        })));
  }
}
