package work.ganglia.kernel.subagent;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

import work.ganglia.BaseGangliaTest;
import work.ganglia.kernel.loop.AgentLoop;
import work.ganglia.kernel.loop.AgentLoopFactory;
import work.ganglia.port.chat.SessionContext;
import work.ganglia.port.external.tool.ObservationType;
import work.ganglia.port.internal.state.ObservationDispatcher;

@ExtendWith(VertxExtension.class)
class DefaultGraphExecutorFingerprintTest extends BaseGangliaTest {

  private final AtomicInteger loopCallCount = new AtomicInteger(0);
  private final ConcurrentHashMap<String, String> capturedPrompts = new ConcurrentHashMap<>();
  private final CopyOnWriteArrayList<ObservationType> dispatchedTypes =
      new CopyOnWriteArrayList<>();

  private AgentLoopFactory loopFactory;
  private ObservationDispatcher dispatcher;

  @BeforeEach
  void setUp(Vertx vertx) {
    setUpBase(vertx);
    loopCallCount.set(0);
    capturedPrompts.clear();
    dispatchedTypes.clear();

    loopFactory =
        () ->
            new AgentLoop() {
              @Override
              public Future<String> run(
                  String userInput,
                  SessionContext context,
                  work.ganglia.port.internal.state.AgentSignal signal) {
                return run(userInput, context);
              }

              @Override
              public Future<String> run(String userInput, SessionContext context) {
                loopCallCount.incrementAndGet();
                String sessionId = context.sessionId();
                capturedPrompts.put(sessionId, userInput);
                return Future.succeededFuture("Result from " + sessionId);
              }

              @Override
              public Future<String> resume(
                  String askId,
                  String toolOutput,
                  SessionContext context,
                  work.ganglia.port.internal.state.AgentSignal signal) {
                return Future.succeededFuture("resumed");
              }

              @Override
              public void stop(String sessionId) {}
            };

    dispatcher =
        new ObservationDispatcher() {
          @Override
          public void dispatch(String sessionId, ObservationType type, String content) {
            dispatchedTypes.add(type);
          }

          @Override
          public void dispatch(
              String sessionId, ObservationType type, String content, Map<String, Object> data) {
            dispatchedTypes.add(type);
          }
        };
  }

  @Test
  void executeNode_cacheHit_skipsExecution(VertxTestContext testContext) {
    DefaultGraphExecutor executor =
        new DefaultGraphExecutor(loopFactory, null, dispatcher, Duration.ofMinutes(10));

    TaskNode node = new TaskNode("n1", "Do analysis", "GENERAL", List.of(), null);
    TaskGraph graph = new TaskGraph(List.of(node));
    SessionContext ctx = createSessionContext("parent");

    // First execution — cache miss
    executor
        .execute(graph, ctx)
        .compose(
            firstReport -> {
              assertEquals(1, loopCallCount.get(), "First execution should call loop");

              // Second execution — same graph, should hit cache
              return executor.execute(graph, ctx);
            })
        .onComplete(
            testContext.succeeding(
                secondReport ->
                    testContext.verify(
                        () -> {
                          assertEquals(
                              1,
                              loopCallCount.get(),
                              "Second execution should use cache, loop called only once");
                          assertTrue(
                              dispatchedTypes.contains(ObservationType.FINGERPRINT_CACHE_HIT),
                              "Should dispatch FINGERPRINT_CACHE_HIT");
                          testContext.completeNow();
                        })));
  }

  @Test
  void executeNode_cacheMiss_executesNormally(VertxTestContext testContext) {
    DefaultGraphExecutor executor =
        new DefaultGraphExecutor(loopFactory, null, dispatcher, Duration.ofMinutes(10));

    TaskNode node1 = new TaskNode("n1", "Task A", "GENERAL", List.of(), null);
    TaskNode node2 = new TaskNode("n1", "Task B", "GENERAL", List.of(), null);
    SessionContext ctx = createSessionContext("parent");

    executor
        .execute(new TaskGraph(List.of(node1)), ctx)
        .compose(r -> executor.execute(new TaskGraph(List.of(node2)), ctx))
        .onComplete(
            testContext.succeeding(
                report ->
                    testContext.verify(
                        () -> {
                          assertEquals(
                              2, loopCallCount.get(), "Different tasks should both execute");
                          testContext.completeNow();
                        })));
  }

  @Test
  void executeNode_cacheExpired_reExecutes(VertxTestContext testContext) {
    // TTL of 0 means immediate expiry
    DefaultGraphExecutor executor =
        new DefaultGraphExecutor(loopFactory, null, dispatcher, Duration.ZERO);

    TaskNode node = new TaskNode("n1", "Do analysis", "GENERAL", List.of(), null);
    TaskGraph graph = new TaskGraph(List.of(node));
    SessionContext ctx = createSessionContext("parent");

    executor
        .execute(graph, ctx)
        .compose(r -> executor.execute(graph, ctx))
        .onComplete(
            testContext.succeeding(
                report ->
                    testContext.verify(
                        () -> {
                          assertEquals(2, loopCallCount.get(), "Expired cache should re-execute");
                          testContext.completeNow();
                        })));
  }

  @Test
  void executeNode_dispatchesCacheMissEvent(VertxTestContext testContext) {
    DefaultGraphExecutor executor =
        new DefaultGraphExecutor(loopFactory, null, dispatcher, Duration.ofMinutes(10));

    TaskNode node = new TaskNode("n1", "Do analysis", "GENERAL", List.of(), null);
    TaskGraph graph = new TaskGraph(List.of(node));
    SessionContext ctx = createSessionContext("parent");

    executor
        .execute(graph, ctx)
        .onComplete(
            testContext.succeeding(
                report ->
                    testContext.verify(
                        () -> {
                          assertTrue(
                              dispatchedTypes.contains(ObservationType.FINGERPRINT_CACHE_MISS),
                              "Should dispatch FINGERPRINT_CACHE_MISS on first execution");
                          testContext.completeNow();
                        })));
  }

  @Test
  void executeNode_withoutCaching_noCacheEvents(VertxTestContext testContext) {
    // No dispatcher, no TTL — use simple constructor
    DefaultGraphExecutor executor = new DefaultGraphExecutor(loopFactory);

    TaskNode node = new TaskNode("n1", "Do analysis", "GENERAL", List.of(), null);
    TaskGraph graph = new TaskGraph(List.of(node));
    SessionContext ctx = createSessionContext("parent");

    executor
        .execute(graph, ctx)
        .onComplete(
            testContext.succeeding(
                report ->
                    testContext.verify(
                        () -> {
                          assertEquals(1, loopCallCount.get());
                          assertTrue(dispatchedTypes.isEmpty(), "No events without dispatcher");
                          testContext.completeNow();
                        })));
  }
}
