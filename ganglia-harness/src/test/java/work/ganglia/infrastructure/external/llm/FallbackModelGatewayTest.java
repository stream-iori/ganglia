package work.ganglia.infrastructure.external.llm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import io.vertx.core.Future;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

import work.ganglia.port.chat.Message;
import work.ganglia.port.external.llm.ChatRequest;
import work.ganglia.port.external.llm.LLMException;
import work.ganglia.port.external.llm.ModelGateway;
import work.ganglia.port.external.llm.ModelOptions;
import work.ganglia.port.external.llm.ModelResponse;
import work.ganglia.port.internal.state.AgentSignal;
import work.ganglia.port.internal.state.ExecutionContext;
import work.ganglia.port.internal.state.TokenUsage;
import work.ganglia.stubs.StubExecutionContext;

@ExtendWith(VertxExtension.class)
class FallbackModelGatewayTest {

  private ModelResponse successResponse;
  private ChatRequest request;

  @BeforeEach
  void setUp() {
    successResponse = new ModelResponse("OK", Collections.emptyList(), new TokenUsage(5, 3));
    request =
        new ChatRequest(
            List.of(Message.user("hello")),
            Collections.emptyList(),
            new ModelOptions(0.0, 512, "primary-model", false),
            new AgentSignal());
  }

  /** Creates a gateway that always succeeds with the given response. */
  private ModelGateway alwaysSucceed(ModelResponse response) {
    return new ModelGateway() {
      @Override
      public Future<ModelResponse> chat(ChatRequest req) {
        return Future.succeededFuture(response);
      }

      @Override
      public Future<ModelResponse> chatStream(ChatRequest req, ExecutionContext ctx) {
        return Future.succeededFuture(response);
      }
    };
  }

  /** Creates a gateway that tracks invocations via the flag and succeeds with the response. */
  private ModelGateway trackingGateway(AtomicBoolean flag, ModelResponse response) {
    return new ModelGateway() {
      @Override
      public Future<ModelResponse> chat(ChatRequest req) {
        flag.set(true);
        return Future.succeededFuture(response);
      }

      @Override
      public Future<ModelResponse> chatStream(ChatRequest req, ExecutionContext ctx) {
        flag.set(true);
        return Future.succeededFuture(response);
      }
    };
  }

  private ModelGateway failingGateway(int statusCode) {
    return new ModelGateway() {
      @Override
      public Future<ModelResponse> chat(ChatRequest req) {
        return Future.failedFuture(new LLMException("Error", null, statusCode, null, null));
      }

      @Override
      public Future<ModelResponse> chatStream(ChatRequest req, ExecutionContext ctx) {
        return Future.failedFuture(new LLMException("Error", null, statusCode, null, null));
      }
    };
  }

  // ── primary succeeds ───────────────────────────────────────────────────────

  @Test
  void chat_primarySucceeds_returnsDirectly(VertxTestContext testContext) {
    FallbackModelGateway gateway =
        new FallbackModelGateway(
            alwaysSucceed(successResponse), alwaysSucceed(successResponse), "utility-model");

    gateway
        .chat(request)
        .onComplete(
            testContext.succeeding(
                response -> {
                  testContext.verify(
                      () -> {
                        assertEquals("OK", response.content());
                        testContext.completeNow();
                      });
                }));
  }

  // ── retryable status codes trigger fallback ────────────────────────────────

  static Stream<Integer> retryableStatusCodes() {
    return Stream.of(429, 500, 502, 503, 529);
  }

  @ParameterizedTest(name = "chat fallback on HTTP {0}")
  @MethodSource("retryableStatusCodes")
  void chat_retryableStatusCode_fallsBackToUtility(int statusCode, VertxTestContext testContext) {
    AtomicBoolean utilityUsed = new AtomicBoolean(false);
    ModelResponse fallbackResponse =
        new ModelResponse("fallback response", Collections.emptyList(), new TokenUsage(3, 2));

    FallbackModelGateway gateway =
        new FallbackModelGateway(
            failingGateway(statusCode),
            trackingGateway(utilityUsed, fallbackResponse),
            "utility-model");

    gateway
        .chat(request)
        .onComplete(
            testContext.succeeding(
                response -> {
                  testContext.verify(
                      () -> {
                        assertTrue(utilityUsed.get(), "Utility should have been used");
                        assertEquals("fallback response", response.content());
                        testContext.completeNow();
                      });
                }));
  }

  // ── non-retryable errors do NOT trigger fallback ───────────────────────────

  @ParameterizedTest(name = "chat no fallback on HTTP {0}")
  @ValueSource(ints = {400, 401, 403, 404, 422})
  void chat_clientError_doesNotFallBack(int statusCode, VertxTestContext testContext) {
    FallbackModelGateway gateway =
        new FallbackModelGateway(
            failingGateway(statusCode), alwaysSucceed(successResponse), "utility-model");

    gateway
        .chat(request)
        .onComplete(
            testContext.failing(
                err -> {
                  testContext.verify(
                      () -> {
                        assertTrue(err instanceof LLMException);
                        testContext.completeNow();
                      });
                }));
  }

  // ── streaming fallback ─────────────────────────────────────────────────────

  @Test
  void chatStream_retryableError_fallsBackToUtility(VertxTestContext testContext) {
    AtomicBoolean utilityUsed = new AtomicBoolean(false);

    ChatRequest streamRequest =
        new ChatRequest(
            List.of(Message.user("hello")),
            Collections.emptyList(),
            new ModelOptions(0.0, 512, "primary-model", true),
            new AgentSignal());

    FallbackModelGateway gateway =
        new FallbackModelGateway(
            failingGateway(429), trackingGateway(utilityUsed, successResponse), "utility-model");

    gateway
        .chatStream(streamRequest, new StubExecutionContext())
        .onComplete(
            testContext.succeeding(
                response -> {
                  testContext.verify(
                      () -> {
                        assertTrue(utilityUsed.get());
                        testContext.completeNow();
                      });
                }));
  }

  // ── non-LLMException ──────────────────────────────────────────────────────

  @Test
  void chat_nonLLMException_doesNotFallBack(VertxTestContext testContext) {
    ModelGateway failingGateway =
        new ModelGateway() {
          @Override
          public Future<ModelResponse> chat(ChatRequest req) {
            return Future.failedFuture(new RuntimeException("network error"));
          }

          @Override
          public Future<ModelResponse> chatStream(ChatRequest req, ExecutionContext ctx) {
            return Future.failedFuture("not used");
          }
        };

    FallbackModelGateway gateway =
        new FallbackModelGateway(failingGateway, alwaysSucceed(successResponse), "utility-model");

    gateway
        .chat(request)
        .onComplete(
            testContext.failing(
                err -> {
                  testContext.verify(
                      () -> {
                        assertTrue(err instanceof RuntimeException);
                        testContext.completeNow();
                      });
                }));
  }
}
