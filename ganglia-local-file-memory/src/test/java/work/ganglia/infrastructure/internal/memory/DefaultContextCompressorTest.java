package work.ganglia.infrastructure.internal.memory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

import work.ganglia.config.ConfigManager;
import work.ganglia.port.chat.Message;
import work.ganglia.port.chat.Turn;
import work.ganglia.port.external.llm.ChatRequest;
import work.ganglia.port.external.llm.ModelGateway;
import work.ganglia.port.external.llm.ModelResponse;
import work.ganglia.stubs.StubConfigManager;

@ExtendWith(VertxExtension.class)
public class DefaultContextCompressorTest {

  private ModelGateway model;
  private ConfigManager configManager;
  private DefaultContextCompressor compressor;

  @BeforeEach
  void setUp(Vertx vertx) {
    model = mock(ModelGateway.class);
    configManager = new StubConfigManager(vertx);
    compressor = new DefaultContextCompressor(model, configManager);
  }

  @Test
  void testSummarize(VertxTestContext testContext) {
    Turn turn = Turn.newTurn("t1", Message.user("Hello"));
    ModelResponse mockResponse = new ModelResponse("Summary result", Collections.emptyList(), null);

    when(model.chat(any(ChatRequest.class))).thenReturn(Future.succeededFuture(mockResponse));

    compressor
        .summarize(List.of(turn), null)
        .onComplete(
            testContext.succeeding(
                result -> {
                  testContext.verify(
                      () -> {
                        assertEquals("Summary result", result);
                        verify(model).chat(any(ChatRequest.class));
                        testContext.completeNow();
                      });
                }));
  }

  @Test
  void testReflect(VertxTestContext testContext) {
    Turn turn = Turn.newTurn("t1", Message.user("Hello"));
    ModelResponse mockResponse = new ModelResponse("Fact 1", Collections.emptyList(), null);

    when(model.chat(any(ChatRequest.class))).thenReturn(Future.succeededFuture(mockResponse));

    compressor
        .reflect(turn)
        .onComplete(
            testContext.succeeding(
                result -> {
                  testContext.verify(
                      () -> {
                        assertEquals("Fact 1", result);
                        testContext.completeNow();
                      });
                }));
  }

  @Test
  void testCompress(VertxTestContext testContext) {
    Turn turn = Turn.newTurn("t1", Message.user("Hello"));
    ModelResponse mockResponse =
        new ModelResponse("Compressed state", Collections.emptyList(), null);

    when(model.chat(any(ChatRequest.class))).thenReturn(Future.succeededFuture(mockResponse));

    compressor
        .compress(List.of(turn))
        .onComplete(
            testContext.succeeding(
                result -> {
                  testContext.verify(
                      () -> {
                        assertEquals("Compressed state", result);
                        testContext.completeNow();
                      });
                }));
  }

  @Test
  void testCompressUsesStructuredPrompt(VertxTestContext testContext) {
    Turn turn = Turn.newTurn("t1", Message.user("Modified file src/Main.java"));
    ModelResponse mockResponse =
        new ModelResponse("## Key Decisions\n- test", Collections.emptyList(), null);

    when(model.chat(
            argThat(
                (ChatRequest req) ->
                    req.messages().get(0).content().contains("CRITICAL: You MUST preserve"))))
        .thenReturn(Future.succeededFuture(mockResponse));

    compressor
        .compress(List.of(turn))
        .onComplete(
            testContext.succeeding(
                result -> {
                  testContext.verify(
                      () -> {
                        assertNotNull(result);
                        verify(model)
                            .chat(
                                argThat(
                                    (ChatRequest req) ->
                                        req.messages()
                                            .get(0)
                                            .content()
                                            .contains("## Key Decisions")));
                        testContext.completeNow();
                      });
                }));
  }

  @Test
  void testExtractKeyFacts(VertxTestContext testContext) {
    Turn turn =
        Turn.newTurn("t1", Message.user("Fix bug in auth"))
            .withResponse(Message.assistant("Fixed the auth bug"));
    ModelResponse mockResponse =
        new ModelResponse(
            "- [DECISION] Fix auth bug\n- [FILE] src/Auth.java", Collections.emptyList(), null);

    when(model.chat(any(ChatRequest.class))).thenReturn(Future.succeededFuture(mockResponse));

    compressor
        .extractKeyFacts(turn, "- [STATE] Working on auth module")
        .onComplete(
            testContext.succeeding(
                result -> {
                  testContext.verify(
                      () -> {
                        assertEquals("- [DECISION] Fix auth bug\n- [FILE] src/Auth.java", result);
                        verify(model).chat(any(ChatRequest.class));
                        testContext.completeNow();
                      });
                }));
  }

  @Test
  void testExtractKeyFactsWithNullExistingSummary(VertxTestContext testContext) {
    Turn turn = Turn.newTurn("t1", Message.user("Hello"));
    ModelResponse mockResponse =
        new ModelResponse("- [STATE] Initial greeting", Collections.emptyList(), null);

    when(model.chat(any(ChatRequest.class))).thenReturn(Future.succeededFuture(mockResponse));

    compressor
        .extractKeyFacts(turn, null)
        .onComplete(
            testContext.succeeding(
                result -> {
                  testContext.verify(
                      () -> {
                        assertEquals("- [STATE] Initial greeting", result);
                        testContext.completeNow();
                      });
                }));
  }
}
