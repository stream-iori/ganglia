package work.ganglia.kernel.hook;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.vertx.core.Future;
import io.vertx.junit5.VertxTestContext;

import work.ganglia.BaseGangliaTest;
import work.ganglia.port.chat.Message;
import work.ganglia.port.chat.SessionContext;
import work.ganglia.port.external.tool.ToolCall;
import work.ganglia.port.internal.hook.AgentInterceptor;

class InterceptorPipelineTest extends BaseGangliaTest {

  private InterceptorPipeline pipeline;
  private SessionContext context;

  @BeforeEach
  void setUpPipeline() {
    pipeline = new InterceptorPipeline(null);
    context = createSessionContext();
  }

  @Test
  void testPreTurnChaining(VertxTestContext testContext) {
    AtomicInteger counter = new AtomicInteger(0);
    pipeline.addInterceptor(
        new AgentInterceptor() {
          @Override
          public Future<SessionContext> preTurn(SessionContext ctx, String userInput) {
            counter.incrementAndGet();
            return Future.succeededFuture(ctx);
          }
        });
    pipeline.addInterceptor(
        new AgentInterceptor() {
          @Override
          public Future<SessionContext> preTurn(SessionContext ctx, String userInput) {
            counter.incrementAndGet();
            return Future.succeededFuture(ctx);
          }
        });

    pipeline
        .executePreTurn(context, "hello")
        .onComplete(
            testContext.succeeding(
                res -> {
                  testContext.verify(
                      () -> {
                        assertEquals(2, counter.get());
                        testContext.completeNow();
                      });
                }));
  }

  @Test
  void testPostTurnErrorIsCaught(VertxTestContext testContext) {
    AtomicInteger counter = new AtomicInteger(0);
    pipeline.addInterceptor(
        new AgentInterceptor() {
          @Override
          public Future<Void> postTurn(SessionContext ctx, Message finalResponse) {
            return Future.failedFuture(new RuntimeException("Simulated failure"));
          }
        });
    pipeline.addInterceptor(
        new AgentInterceptor() {
          @Override
          public Future<Void> postTurn(SessionContext ctx, Message finalResponse) {
            counter.incrementAndGet();
            return Future.succeededFuture();
          }
        });

    // The pipeline should succeed despite the first hook failing
    pipeline
        .executePostTurn(context, Message.assistant("done"))
        .onComplete(
            testContext.succeeding(
                res -> {
                  testContext.verify(
                      () -> {
                        assertEquals(1, counter.get());
                        testContext.completeNow();
                      });
                }));
  }

  @Test
  void testPreToolExecuteFailureInterruptsChain(VertxTestContext testContext) {
    AtomicInteger counter = new AtomicInteger(0);
    pipeline.addInterceptor(
        new AgentInterceptor() {
          @Override
          public Future<ToolCall> preToolExecute(ToolCall call, SessionContext ctx) {
            return Future.failedFuture(new SecurityException("Blocked tool"));
          }
        });
    pipeline.addInterceptor(
        new AgentInterceptor() {
          @Override
          public Future<ToolCall> preToolExecute(ToolCall call, SessionContext ctx) {
            counter.incrementAndGet();
            return Future.succeededFuture(call);
          }
        });

    ToolCall call = new ToolCall("c1", "bash", Map.of("cmd", "rm -rf"));
    pipeline
        .executePreToolExecute(call, context)
        .onComplete(
            testContext.failing(
                err -> {
                  testContext.verify(
                      () -> {
                        assertEquals(0, counter.get());
                        assertEquals("Blocked tool", err.getMessage());
                        testContext.completeNow();
                      });
                }));
  }
}
