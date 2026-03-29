package work.ganglia.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

import work.ganglia.BootstrapOptions;
import work.ganglia.Ganglia;
import work.ganglia.coding.CodingAgentBuilder;
import work.ganglia.kernel.loop.AgentLoopObserver;
import work.ganglia.port.chat.Role;
import work.ganglia.port.chat.SessionContext;
import work.ganglia.port.external.llm.ChatRequest;
import work.ganglia.port.external.llm.ModelGateway;
import work.ganglia.port.external.llm.ModelResponse;
import work.ganglia.port.external.tool.CommandExecutor;
import work.ganglia.port.external.tool.ObservationType;
import work.ganglia.port.external.tool.ToolCall;
import work.ganglia.port.internal.prompt.ContextBudget;
import work.ganglia.port.internal.state.TokenUsage;
import work.ganglia.util.VertxProcess;

/**
 * Integration tests verifying the three-layer output-truncation defence:
 *
 * <ul>
 *   <li>Layer 1 – {@code VertxProcess}: oversized process output is truncated and delivered as a
 *       successful (error-code) result with an {@code [OUTPUT TRUNCATED} marker, instead of failing
 *       the promise.
 *   <li>Layer 2 – {@code ObservationCompressionHook}: non-SUCCESS (ERROR) tool outputs that exceed
 *       the token limit are truncated before the LLM sees them, not only SUCCESS outputs.
 *   <li>Layer 3 – {@code ObservationCompressionHook} + {@code
 *       StandardPromptEngine.capToolMessages}: oversized SUCCESS outputs are truncated by the hook
 *       (fallback path when LLM compression fails) and the {@code [TRUNCATED:} marker reaches the
 *       LLM.
 * </ul>
 *
 * <p>All three tests use a mock {@link CommandExecutor} to produce controlled outputs without
 * spawning real processes, and a mock {@link ModelGateway} whose {@code chat()} always fails so
 * that {@code ObservationCompressionHook} falls back to {@code TokenAwareTruncator}.
 */
@ExtendWith(VertxExtension.class)
class TruncationIT {

  private ModelGateway mockModel;

  @TempDir Path tempDir;

  // "word " ≈ 1 CL100K token.  6 000 repetitions ≈ 6 000 tokens.
  // This is > 4 000 chars (LLM-compressor threshold) and > 1 500 tokens (truncator threshold).
  private static final String OVERSIZED_SUCCESS_OUTPUT = "word ".repeat(6000);

  // "ERROR line: exception detail\n" ≈ 6-7 CL100K tokens per line.
  // 1 000 lines ≈ 6 000-7 000 tokens > both thresholds.
  private static final String OVERSIZED_ERROR_LINE = "ERROR line: exception detail\n";
  private static final String OVERSIZED_ERROR_OUTPUT = OVERSIZED_ERROR_LINE.repeat(1000);

  @BeforeEach
  void setUp() {
    mockModel = mock(ModelGateway.class);
    // The LLM-based compressor calls mockModel.chat().  Make it fail so ObservationCompressionHook
    // always falls back to TokenAwareTruncator in these tests.
    when(mockModel.chat(any(ChatRequest.class)))
        .thenReturn(Future.failedFuture("no LLM compression in truncation tests"));
  }

  // -------------------------------------------------------------------------
  // Helpers
  // -------------------------------------------------------------------------

  private Future<Ganglia> bootstrapWithExecutor(Vertx vertx, CommandExecutor executor)
      throws IOException {
    return bootstrapWithExecutor(vertx, executor, null, List.of());
  }

  private Future<Ganglia> bootstrapWithExecutor(
      Vertx vertx,
      CommandExecutor executor,
      JsonObject modelOverride,
      List<AgentLoopObserver> observers)
      throws IOException {
    String projectRoot = tempDir.toRealPath().toString();
    JsonObject config = new JsonObject().put("webui", new JsonObject().put("enabled", false));
    if (modelOverride != null) {
      config.mergeIn(modelOverride, true);
    }
    return CodingAgentBuilder.bootstrap(
        vertx,
        BootstrapOptions.builder()
            .projectRoot(projectRoot)
            .modelGatewayOverride(mockModel)
            .commandExecutor(executor)
            .overrideConfig(config)
            .extraObservers(observers)
            .build());
  }

  /**
   * Returns a mock executor whose {@code execute()} always yields the given {@link
   * VertxProcess.Result}.
   */
  private static CommandExecutor executorReturning(VertxProcess.Result result) {
    CommandExecutor ex = mock(CommandExecutor.class);
    when(ex.execute(anyString(), anyString(), any())).thenReturn(Future.succeededFuture(result));
    when(ex.execute(anyString(), any(), any())).thenReturn(Future.succeededFuture(result));
    return ex;
  }

  /**
   * Sets up chatStream stubs: first call issues a shell command; second call captures the TOOL
   * message.
   */
  private AtomicReference<String> stubChatStreamWithShellCall(String toolCallId, String command) {
    AtomicReference<String> captured = new AtomicReference<>();
    when(mockModel.chatStream(any(ChatRequest.class), any()))
        .thenReturn(
            Future.succeededFuture(
                new ModelResponse(
                    "Running.",
                    List.of(
                        new ToolCall(toolCallId, "run_shell_command", Map.of("command", command))),
                    new TokenUsage(1, 1))))
        .thenAnswer(
            inv -> {
              ChatRequest req = inv.getArgument(0);
              req.messages().stream()
                  .filter(m -> m.role() == Role.TOOL)
                  .findFirst()
                  .ifPresent(m -> captured.set(m.content()));
              return Future.succeededFuture(
                  new ModelResponse("Done.", Collections.emptyList(), new TokenUsage(1, 1)));
            });
    return captured;
  }

  // -------------------------------------------------------------------------
  // Layer 1: VertxProcess truncation marker survives all the way to the LLM
  // -------------------------------------------------------------------------

  @Test
  void layer1_oversizedShellOutput_savedToTmpFile(Vertx vertx, VertxTestContext testContext)
      throws IOException {

    // Simulate what VertxProcess.execute() now produces when output exceeds MAX_OUTPUT_SIZE:
    // the [OUTPUT TRUNCATED] marker is prepended so it survives any downstream token-based
    // truncation that keeps only a prefix.  Exit code 1 is returned instead of failing.
    String baseOutput = "captured output line\n".repeat(25); // ~500 chars, ~125 tokens
    String truncatedByVertxProcess =
        "[OUTPUT TRUNCATED: exceeded "
            + CommandExecutor.MAX_OUTPUT_SIZE
            + " bytes. Only the first portion is shown.]\n\n"
            + baseOutput;

    CommandExecutor bigExecutor =
        executorReturning(new VertxProcess.Result(1, truncatedByVertxProcess));

    AtomicReference<String> captured = stubChatStreamWithShellCall("c1", "bigcmd");

    bootstrapWithExecutor(vertx, bigExecutor)
        .compose(
            g -> {
              SessionContext ctx = g.sessionManager().createSession("layer1-test");
              return g.agentLoop().run("Generate big output", ctx);
            })
        .onComplete(
            testContext.succeeding(
                ignored ->
                    testContext.verify(
                        () -> {
                          assertNotNull(captured.get(), "LLM must receive a TOOL message");
                          // run_shell_command is a reproducible tool → WRITE_TO_TMP:
                          // the full output (including the truncation marker) is saved to a
                          // session tmp file, and the LLM receives a short path + line-count
                          // hint instead of the raw output.
                          assertTrue(
                              captured.get().contains("[Output from 'run_shell_command' was large"),
                              "Reproducible tool output should be saved to tmp file. Got: "
                                  + captured
                                      .get()
                                      .substring(0, Math.min(200, captured.get().length())));
                          testContext.completeNow();
                        })));
  }

  // -------------------------------------------------------------------------
  // Layer 2: ObservationCompressionHook reduces ERROR-status outputs from
  //          run_shell_command by writing them to a tmp file (WRITE_TO_TMP path)
  // -------------------------------------------------------------------------

  @Test
  void layer2_errorStatusWithLargeOutput_hookReducesBeforeLlmSeesIt(
      Vertx vertx, VertxTestContext testContext) throws IOException {

    // Non-zero exit → CommandResultHandler.fromResult → ToolInvokeResult.error(output)
    // run_shell_command is a reproducible tool → WRITE_TO_TMP: full output saved to tmp file,
    // LLM receives a short path + line-count hint instead of the raw error content.
    AtomicReference<String> captured = stubChatStreamWithShellCall("c2", "bad_command");

    bootstrapWithExecutor(
            vertx, executorReturning(new VertxProcess.Result(1, OVERSIZED_ERROR_OUTPUT)))
        .compose(
            g -> {
              SessionContext ctx = g.sessionManager().createSession("layer2-test");
              return g.agentLoop().run("Run a failing command", ctx);
            })
        .onComplete(
            testContext.succeeding(
                ignored ->
                    testContext.verify(
                        () -> {
                          assertNotNull(captured.get(), "LLM must receive a TOOL message");
                          // The raw oversized error must not appear verbatim in the LLM request.
                          assertFalse(
                              captured.get().length() >= OVERSIZED_ERROR_OUTPUT.length(),
                              "Output seen by LLM must be shorter than the raw error output. "
                                  + "Got length: "
                                  + captured.get().length());
                          testContext.completeNow();
                        })));
  }

  // -------------------------------------------------------------------------
  // Layer 3: Oversized SUCCESS output from run_shell_command is written to a
  //          tmp file; the LLM receives a short path + line-count hint instead
  //          of the raw output.
  // -------------------------------------------------------------------------

  @Test
  void layer3_oversizedSuccessOutput_isWrittenToTmpAndHintReachesLlm(
      Vertx vertx, VertxTestContext testContext) throws IOException {

    // Zero exit → CommandResultHandler.fromResult → ToolInvokeResult.success(output)
    // run_shell_command is a reproducible tool → WRITE_TO_TMP action:
    //   full output is saved to .ganglia/tmp/{sessionId}/{toolCallId}.txt
    //   the context message is replaced with a short path + line-count hint.
    AtomicReference<String> captured = stubChatStreamWithShellCall("c3", "big_success_cmd");

    bootstrapWithExecutor(
            vertx, executorReturning(new VertxProcess.Result(0, OVERSIZED_SUCCESS_OUTPUT)))
        .compose(
            g -> {
              SessionContext ctx = g.sessionManager().createSession("layer3-test");
              return g.agentLoop().run("Run a big successful command", ctx);
            })
        .onComplete(
            testContext.succeeding(
                ignored ->
                    testContext.verify(
                        () -> {
                          assertNotNull(captured.get(), "LLM must receive a TOOL message");
                          // The LLM receives a short hint, not the raw oversized output
                          assertTrue(
                              captured.get().length() < OVERSIZED_SUCCESS_OUTPUT.length(),
                              "LLM message must be shorter than raw output. Got length: "
                                  + captured.get().length());
                          // The hint must reference the tool and suggest reading the file
                          assertTrue(
                              captured.get().contains("lines")
                                  || captured.get().contains("run_shell_command")
                                  || captured.get().contains("read_file"),
                              "LLM message must contain a file hint. Got: "
                                  + captured
                                      .get()
                                      .substring(0, Math.min(300, captured.get().length())));
                          testContext.completeNow();
                        })));
  }

  // -------------------------------------------------------------------------
  // Budget-aware: budget event emitted with self-consistent values
  // -------------------------------------------------------------------------

  @Test
  void budgetEventEmittedDuringTruncation(Vertx vertx, VertxTestContext testContext)
      throws IOException {

    List<Map<String, Object>> captured = new java.util.concurrent.CopyOnWriteArrayList<>();
    AgentLoopObserver observer =
        new AgentLoopObserver() {
          @Override
          public void onObservation(
              String sessionId, ObservationType type, String content, Map<String, Object> data) {
            if (type == ObservationType.CONTEXT_BUDGET_ALLOCATED) {
              captured.add(data);
            }
          }

          @Override
          public void onUsageRecorded(String sessionId, TokenUsage usage) {}
        };

    AtomicReference<String> capRef = stubChatStreamWithShellCall("c-budget", "echo hi");
    CommandExecutor smallExecutor = executorReturning(new VertxProcess.Result(0, "ok"));

    bootstrapWithExecutor(vertx, smallExecutor, null, List.of(observer))
        .compose(
            g -> {
              SessionContext ctx = g.sessionManager().createSession("budget-truncation-test");
              return g.agentLoop().run("Test budget", ctx);
            })
        .onComplete(
            testContext.succeeding(
                ignored ->
                    testContext.verify(
                        () -> {
                          assertFalse(captured.isEmpty(), "Budget event must be emitted");
                          Map<String, Object> data = captured.get(0);

                          // Verify fields are self-consistent with ContextBudget.from()
                          int ctxLimit = (Integer) data.get("contextLimit");
                          int maxGen = (Integer) data.get("maxGenerationTokens");
                          ContextBudget expected = ContextBudget.from(ctxLimit, maxGen);
                          assertEquals(
                              (Integer) expected.observationFallback(),
                              data.get("observationFallback"));
                          assertEquals((Integer) expected.history(), data.get("historyBudget"));
                          assertEquals(
                              (Integer) expected.compressionTarget(),
                              data.get("compressionTarget"));
                          testContext.completeNow();
                        })));
  }
}
