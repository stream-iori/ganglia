package work.ganglia.kernel.hook;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.vertx.core.Future;
import io.vertx.junit5.VertxTestContext;

import work.ganglia.BaseGangliaTest;
import work.ganglia.infrastructure.external.tool.model.ToolInvokeResult;
import work.ganglia.port.chat.Message;
import work.ganglia.port.chat.SessionContext;
import work.ganglia.port.external.tool.ObservationType;
import work.ganglia.port.external.tool.ToolCall;
import work.ganglia.port.internal.hook.AgentInterceptor;
import work.ganglia.port.internal.state.ObservationDispatcher;

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

  @Test
  void testToolDurationMsInObservation(VertxTestContext testContext) {
    List<Map<String, Object>> observedData = Collections.synchronizedList(new ArrayList<>());

    ObservationDispatcher capturingDispatcher =
        new ObservationDispatcher() {
          @Override
          public void dispatch(String sessionId, ObservationType type, String content) {}

          @Override
          public void dispatch(
              String sessionId, ObservationType type, String content, Map<String, Object> data) {
            if (type == ObservationType.TOOL_FINISHED && data != null) {
              observedData.add(data);
            }
          }
        };

    InterceptorPipeline pipelineWithDispatcher = new InterceptorPipeline(capturingDispatcher);

    ToolCall call = new ToolCall("c1", "bash", Map.of("cmd", "ls"));
    ToolInvokeResult result = ToolInvokeResult.success("file1.txt");
    long startMs = System.currentTimeMillis();

    pipelineWithDispatcher
        .executePostToolExecute(call, result, context, startMs)
        .onComplete(
            testContext.succeeding(
                res -> {
                  testContext.verify(
                      () -> {
                        assertEquals(1, observedData.size());
                        Map<String, Object> data = observedData.get(0);
                        assertNotNull(data.get("durationMs"), "durationMs should be present");
                        assertTrue(
                            (Long) data.get("durationMs") >= 0,
                            "durationMs should be non-negative");
                        testContext.completeNow();
                      });
                }));
  }
}
