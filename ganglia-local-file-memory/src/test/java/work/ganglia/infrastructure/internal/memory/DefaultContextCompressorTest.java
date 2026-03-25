package work.ganglia.infrastructure.internal.memory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
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
}
