package work.ganglia.infrastructure.internal.memory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
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

  /**
   * When multiple chunks are compressed and the merged result fits within the utility context
   * limit, no additional LLM call should be made to re-compress the merge. Verifies the fix for the
   * duplicate-compression bug where both branches of the if/else called compressText().
   */
  @Test
  void compress_chunkedMergeWithinLimit_doesNotRecompress(VertxTestContext testContext) {
    // Use a low utilityContextLimit so we can trigger chunking with small turns.
    // StubConfigManager returns default 32000 which is too high for practical test data.
    // Create a compressor with a config that reports utilityContextLimit = 100.
    var lowLimitConfig =
        new work.ganglia.config.ModelConfigProvider() {
          @Override
          public work.ganglia.config.model.ModelConfig getModelConfig(String key) {
            return null;
          }

          @Override
          public String getModel() {
            return "test";
          }

          @Override
          public String getUtilityModel() {
            return "test";
          }

          @Override
          public double getTemperature() {
            return 0;
          }

          @Override
          public int getContextLimit() {
            return 1000;
          }

          @Override
          public int getMaxTokens() {
            return 100;
          }

          @Override
          public boolean isStream() {
            return false;
          }

          @Override
          public boolean isUtilityStream() {
            return false;
          }

          @Override
          public String getBaseUrl() {
            return "http://localhost";
          }

          @Override
          public String getProvider() {
            return "test";
          }

          @Override
          public int getUtilityContextLimit() {
            return 100; // Very low: 80% = 80 tokens triggers chunking, 60% = 60 per chunk
          }
        };
    DefaultContextCompressor lowLimitCompressor =
        new DefaultContextCompressor(model, lowLimitConfig);

    // Each turn ~50 tokens via toString(). Combined ~100 tokens > 80% of 100 = 80 → chunking.
    // Each individually ~50 < 60% of 100 = 60 → fits in a single chunk.
    List<Turn> turns = new ArrayList<>();
    turns.add(Turn.newTurn("t1", Message.user("word ".repeat(40))));
    turns.add(Turn.newTurn("t2", Message.user("word ".repeat(40))));

    // Each chunk compression returns a short summary (well within limit when merged)
    ModelResponse shortSummary = new ModelResponse("Chunk summary.", Collections.emptyList(), null);
    when(model.chat(any(ChatRequest.class))).thenReturn(Future.succeededFuture(shortSummary));

    lowLimitCompressor
        .compress(turns)
        .onComplete(
            testContext.succeeding(
                result -> {
                  testContext.verify(
                      () -> {
                        // With the bug fix: 2 chunk compressions only, no extra re-compression.
                        // Each chunk triggers one LLM call → exactly 2 calls total.
                        verify(model, times(2)).chat(any(ChatRequest.class));
                        // Result should be the merged chunk summaries, not a re-compressed version
                        assertEquals("Chunk summary.\n\n---\n\nChunk summary.", result);
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
