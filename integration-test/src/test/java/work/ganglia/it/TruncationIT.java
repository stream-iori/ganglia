package work.ganglia.it;

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
import work.ganglia.coding.CodingAgentBuilder;
import work.ganglia.port.chat.Role;
import work.ganglia.port.chat.SessionContext;
import work.ganglia.port.external.llm.ChatRequest;
import work.ganglia.port.external.llm.ModelGateway;
import work.ganglia.port.external.llm.ModelResponse;
import work.ganglia.port.external.tool.CommandExecutor;
import work.ganglia.port.external.tool.ToolCall;
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

  private Future<work.ganglia.Ganglia> bootstrapWithExecutor(Vertx vertx, CommandExecutor executor)
      throws IOException {
    String projectRoot = tempDir.toRealPath().toString();
    return CodingAgentBuilder.bootstrap(
        vertx,
        BootstrapOptions.builder()
            .projectRoot(projectRoot)
            .modelGatewayOverride(mockModel)
            .commandExecutor(executor)
            .overrideConfig(new JsonObject().put("webui", new JsonObject().put("enabled", false)))
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
  void layer1_oversizedShellOutput_truncationMarkerReachesLlm(
      Vertx vertx, VertxTestContext testContext) throws IOException {

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
                          assertTrue(
                              captured.get().contains("[OUTPUT TRUNCATED"),
                              "VertxProcess truncation marker must reach the LLM. Got: "
                                  + captured
                                      .get()
                                      .substring(0, Math.min(200, captured.get().length())));
                          testContext.completeNow();
                        })));
  }

  // -------------------------------------------------------------------------
  // Layer 2: ObservationCompressionHook truncates ERROR-status outputs
  // -------------------------------------------------------------------------

  @Test
  void layer2_errorStatusWithLargeOutput_hookTruncatesBeforeLlmSeesIt(
      Vertx vertx, VertxTestContext testContext) throws IOException {

    // Non-zero exit → CommandResultHandler.fromResult → ToolInvokeResult.error(output)
    // The old ObservationCompressionHook skipped non-SUCCESS statuses.  Now it must truncate them.
    // We use 1 000 lines × ~7 tokens/line ≈ 7 000 tokens > 3 000 (truncator threshold).
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
                          // The hook must have truncated the ERROR output.
                          assertTrue(
                              captured.get().contains("[TRUNCATED:"),
                              "ObservationCompressionHook must truncate oversized ERROR output. "
                                  + "Got length: "
                                  + captured.get().length());
                          // The raw oversized error must not appear verbatim in the LLM request.
                          assertFalse(
                              captured.get().length() >= OVERSIZED_ERROR_OUTPUT.length(),
                              "Truncated output must be shorter than the raw error output");
                          testContext.completeNow();
                        })));
  }

  // -------------------------------------------------------------------------
  // Layer 3: Oversized SUCCESS output is truncated by the hook fallback path,
  //          and capToolMessages provides a second safety net in prepareRequest
  // -------------------------------------------------------------------------

  @Test
  void layer3_oversizedSuccessOutput_isTruncatedBeforeLlmReceivesIt(
      Vertx vertx, VertxTestContext testContext) throws IOException {

    // Zero exit → CommandResultHandler.fromResult → ToolInvokeResult.success(output)
    // LLM compressor sees length > 4 000 chars → requiresCompression() = true → calls chat()
    // chat() fails → .recover() → TokenAwareTruncator.truncate() → [TRUNCATED:] marker
    // capToolMessages() in StandardPromptEngine provides an additional 4 000-token cap on top.
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
                          assertTrue(
                              captured.get().contains("[TRUNCATED:"),
                              "Oversized SUCCESS output must be truncated. Got: "
                                  + captured
                                      .get()
                                      .substring(0, Math.min(200, captured.get().length())));
                          assertFalse(
                              captured.get().length() >= OVERSIZED_SUCCESS_OUTPUT.length(),
                              "Truncated output must be shorter than the original");
                          testContext.completeNow();
                        })));
  }
}
