package work.ganglia.infrastructure.external.llm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.core.Future;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

import work.ganglia.port.chat.Message;
import work.ganglia.port.external.llm.ChatRequest;
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

  private ModelGateway successGateway() {
    return new ModelGateway() {
      @Override
      public Future<ModelResponse> chat(ChatRequest req) {
        return Future.succeededFuture(successResponse);
      }

      @Override
      public Future<ModelResponse> chatStream(ChatRequest req, ExecutionContext ctx) {
        return Future.succeededFuture(successResponse);
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

  @Test
  void testPrimarySucceeds(VertxTestContext testContext) {
    FallbackModelGateway gateway =
        new FallbackModelGateway(successGateway(), successGateway(), "utility-model");

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

  @Test
  void testFallbackOnRateLimit(VertxTestContext testContext) {
    AtomicBoolean utilityUsed = new AtomicBoolean(false);
    ModelGateway utility =
        new ModelGateway() {
          @Override
          public Future<ModelResponse> chat(ChatRequest req) {
            utilityUsed.set(true);
            // Verify model was switched to utility model
            assertEquals("utility-model", req.options().modelName());
            return Future.succeededFuture(
                new ModelResponse(
                    "fallback response", Collections.emptyList(), new TokenUsage(3, 2)));
          }

          @Override
          public Future<ModelResponse> chatStream(ChatRequest req, ExecutionContext ctx) {
            return Future.failedFuture("not used");
          }
        };

    FallbackModelGateway gateway =
        new FallbackModelGateway(failingGateway(429), utility, "utility-model");

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

  @Test
  void testFallbackOnServerError(VertxTestContext testContext) {
    AtomicBoolean utilityUsed = new AtomicBoolean(false);
    ModelGateway utility =
        new ModelGateway() {
          @Override
          public Future<ModelResponse> chat(ChatRequest req) {
            utilityUsed.set(true);
            return Future.succeededFuture(successResponse);
          }

          @Override
          public Future<ModelResponse> chatStream(ChatRequest req, ExecutionContext ctx) {
            return Future.failedFuture("not used");
          }
        };

    FallbackModelGateway gateway =
        new FallbackModelGateway(failingGateway(503), utility, "utility-model");

    gateway
        .chat(request)
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

  @Test
  void testNoFallbackOnClientError(VertxTestContext testContext) {
    // 400 errors should NOT trigger fallback
    FallbackModelGateway gateway =
        new FallbackModelGateway(failingGateway(400), successGateway(), "utility-model");

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

  @Test
  void testFallbackOnStreamRateLimit(VertxTestContext testContext) {
    AtomicBoolean utilityUsed = new AtomicBoolean(false);
    ModelGateway utility =
        new ModelGateway() {
          @Override
          public Future<ModelResponse> chat(ChatRequest req) {
            return Future.failedFuture("not used");
          }

          @Override
          public Future<ModelResponse> chatStream(ChatRequest req, ExecutionContext ctx) {
            utilityUsed.set(true);
            return Future.succeededFuture(successResponse);
          }
        };

    ChatRequest streamRequest =
        new ChatRequest(
            List.of(Message.user("hello")),
            Collections.emptyList(),
            new ModelOptions(0.0, 512, "primary-model", true),
            new AgentSignal());

    FallbackModelGateway gateway =
        new FallbackModelGateway(failingGateway(429), utility, "utility-model");

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

  @Test
  void testNoFallbackOnNonLLMException(VertxTestContext testContext) {
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
        new FallbackModelGateway(failingGateway, successGateway(), "utility-model");

    gateway
        .chat(request)
        .onComplete(
            testContext.failing(
                err -> {
                  testContext.verify(
                      () -> {
                        // Non-LLMException should NOT trigger fallback
                        assertTrue(err instanceof RuntimeException);
                        testContext.completeNow();
                      });
                }));
  }
}
