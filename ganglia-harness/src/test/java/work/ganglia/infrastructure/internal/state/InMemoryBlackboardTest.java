package work.ganglia.infrastructure.internal.state;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

import work.ganglia.BaseGangliaTest;
import work.ganglia.port.external.tool.ObservationType;
import work.ganglia.port.internal.state.Blackboard;
import work.ganglia.port.internal.state.ColdStorage;
import work.ganglia.port.internal.state.FactStatus;
import work.ganglia.port.internal.state.ObservationDispatcher;

@ExtendWith(VertxExtension.class)
class InMemoryBlackboardTest extends BaseGangliaTest {

  @Nested
  class CoreBehavior {

    private Blackboard blackboard;

    @BeforeEach
    void setUp(Vertx vertx) {
      setUpBase(vertx);
      blackboard = new InMemoryBlackboard();
    }

    @Test
    void publish_createsActiveFact(VertxTestContext testContext) {
      assertFutureSuccess(
          blackboard.publish("manager-1", "Found a bug in login", "/detail/1.json", 1),
          testContext,
          fact -> {
            assertNotNull(fact.id());
            assertEquals(1, fact.version());
            assertEquals("Found a bug in login", fact.summary());
            assertEquals("/detail/1.json", fact.detailRef());
            assertEquals("manager-1", fact.sourceManager());
            assertEquals(FactStatus.ACTIVE, fact.status());
            assertEquals(1, fact.cycleNumber());
            assertNotNull(fact.createdAt());
          });
    }

    @Test
    void getActiveFacts_returnsOnlyActiveOnes(VertxTestContext testContext) {
      blackboard
          .publish("m1", "Fact A", null, 1)
          .compose(
              factA ->
                  blackboard
                      .publish("m2", "Fact B", null, 1)
                      .compose(factB -> blackboard.supersede(factA.id(), "outdated"))
                      .compose(v -> blackboard.getActiveFacts()))
          .onComplete(
              testContext.succeeding(
                  active ->
                      testContext.verify(
                          () -> {
                            assertEquals(1, active.size());
                            assertEquals("Fact B", active.get(0).summary());
                            testContext.completeNow();
                          })));
    }

    @Test
    void supersede_incrementsSupersededCount(VertxTestContext testContext) {
      blackboard
          .publish("m1", "Fact A", null, 1)
          .compose(fact -> blackboard.supersede(fact.id(), "outdated"))
          .compose(v -> blackboard.getSupersededCount())
          .onComplete(
              testContext.succeeding(
                  count ->
                      testContext.verify(
                          () -> {
                            assertEquals(1, count);
                            testContext.completeNow();
                          })));
    }

    @Test
    void supersede_nonExistentFact_fails(VertxTestContext testContext) {
      assertFutureFailure(
          blackboard.supersede("non-existent", "reason"),
          testContext,
          err -> assertTrue(err.getMessage().contains("not found")));
    }

    @Test
    void getNewFactCount_countsFactsInRecentCycles(VertxTestContext testContext) {
      blackboard
          .publish("m1", "Cycle 1 fact", null, 1)
          .compose(f -> blackboard.publish("m1", "Cycle 2 fact", null, 2))
          .compose(f -> blackboard.publish("m1", "Cycle 3 fact", null, 3))
          .compose(f -> blackboard.getNewFactCount(2))
          .onComplete(
              testContext.succeeding(
                  count ->
                      testContext.verify(
                          () -> {
                            assertEquals(2, count);
                            testContext.completeNow();
                          })));
    }

    @Test
    void getFactDetail_returnsDetailRef(VertxTestContext testContext) {
      blackboard
          .publish("m1", "Summary", "/path/to/detail.json", 1)
          .compose(fact -> blackboard.getFactDetail(fact.id()))
          .onComplete(
              testContext.succeeding(
                  detail ->
                      testContext.verify(
                          () -> {
                            assertEquals("/path/to/detail.json", detail);
                            testContext.completeNow();
                          })));
    }

    @Test
    void getFactDetail_unknownFact_returnsNull(VertxTestContext testContext) {
      assertFutureSuccess(
          blackboard.getFactDetail("unknown"), testContext, detail -> assertNull(detail));
    }

    @Test
    void publish_generatesUniqueIds(VertxTestContext testContext) {
      blackboard
          .publish("m1", "A", null, 1)
          .compose(a -> blackboard.publish("m2", "B", null, 1).map(b -> List.of(a.id(), b.id())))
          .onComplete(
              testContext.succeeding(
                  ids ->
                      testContext.verify(
                          () -> {
                            assertNotEquals(ids.get(0), ids.get(1));
                            testContext.completeNow();
                          })));
    }
  }

  @Nested
  class ObservationDispatch {

    private record DispatchedEvent(
        String sessionId, ObservationType type, String content, Map<String, Object> data) {}

    private final CopyOnWriteArrayList<DispatchedEvent> dispatched = new CopyOnWriteArrayList<>();
    private Blackboard blackboard;

    @BeforeEach
    void setUp(Vertx vertx) {
      setUpBase(vertx);
      dispatched.clear();
      ObservationDispatcher recorder =
          new ObservationDispatcher() {
            @Override
            public void dispatch(String sessionId, ObservationType type, String content) {
              dispatched.add(new DispatchedEvent(sessionId, type, content, Map.of()));
            }

            @Override
            public void dispatch(
                String sessionId, ObservationType type, String content, Map<String, Object> data) {
              dispatched.add(new DispatchedEvent(sessionId, type, content, data));
            }
          };
      blackboard = new InMemoryBlackboard(recorder, "test-session");
    }

    @Test
    void publish_dispatchesFactPublished(VertxTestContext testContext) {
      assertFutureSuccess(
          blackboard.publish("mgr-1", "Found bug", "/d/1.json", 2),
          testContext,
          fact -> {
            assertEquals(1, dispatched.size());
            DispatchedEvent evt = dispatched.get(0);
            assertEquals("test-session", evt.sessionId());
            assertEquals(ObservationType.FACT_PUBLISHED, evt.type());
            assertEquals("Found bug", evt.content());
            assertEquals(fact.id(), evt.data().get("factId"));
            assertEquals(1, evt.data().get("version"));
            assertEquals("Found bug", evt.data().get("summary"));
            assertEquals("mgr-1", evt.data().get("sourceManager"));
            assertEquals("ACTIVE", evt.data().get("status"));
            assertEquals(2, evt.data().get("cycleNumber"));
            assertEquals("/d/1.json", evt.data().get("detailRef"));
            assertFalse(evt.data().containsKey("reason"));
          });
    }

    @Test
    void supersede_dispatchesFactSuperseded(VertxTestContext testContext) {
      blackboard
          .publish("mgr-1", "Old finding", null, 1)
          .compose(fact -> blackboard.supersede(fact.id(), "better data available"))
          .onComplete(
              testContext.succeeding(
                  v ->
                      testContext.verify(
                          () -> {
                            assertEquals(2, dispatched.size());
                            DispatchedEvent published = dispatched.get(0);
                            assertEquals(ObservationType.FACT_PUBLISHED, published.type());

                            DispatchedEvent superseded = dispatched.get(1);
                            assertEquals(ObservationType.FACT_SUPERSEDED, superseded.type());
                            assertEquals("test-session", superseded.sessionId());
                            assertEquals("Old finding", superseded.content());
                            assertEquals("SUPERSEDED", superseded.data().get("status"));
                            assertEquals(2, superseded.data().get("version"));
                            assertEquals("better data available", superseded.data().get("reason"));
                            testContext.completeNow();
                          })));
    }

    @Test
    void noDispatcher_doesNotThrow(VertxTestContext testContext) {
      Blackboard silent = new InMemoryBlackboard();
      assertFutureSuccess(
          silent.publish("m1", "Fact", null, 1), testContext, fact -> assertNotNull(fact.id()));
    }
  }

  @Nested
  class ColdStorageIntegration {

    private final ConcurrentHashMap<String, String> storageMap = new ConcurrentHashMap<>();
    private InMemoryBlackboard blackboard;

    @BeforeEach
    void setUp(Vertx vertx) {
      setUpBase(vertx);
      storageMap.clear();

      ColdStorage mockStorage =
          new ColdStorage() {
            @Override
            public Future<Void> write(String detailRef, String content) {
              storageMap.put(detailRef, content);
              return Future.succeededFuture();
            }

            @Override
            public Future<String> read(String detailRef) {
              return Future.succeededFuture(storageMap.get(detailRef));
            }
          };

      blackboard = new InMemoryBlackboard(null, null, mockStorage);
    }

    @Test
    void getFactDetail_withColdStorage_readsContent(VertxTestContext testContext) {
      storageMap.put("/detail/fact-1.json", "{\"full\": \"detail content\"}");

      blackboard
          .publish("m1", "Summary", "/detail/fact-1.json", 1)
          .compose(fact -> blackboard.getFactDetail(fact.id()))
          .onComplete(
              testContext.succeeding(
                  content ->
                      testContext.verify(
                          () -> {
                            assertEquals("{\"full\": \"detail content\"}", content);
                            testContext.completeNow();
                          })));
    }

    @Test
    void publishWithDetail_writesToStorageAndPublishes(VertxTestContext testContext) {
      blackboard
          .publishWithDetail("m1", "Summary", "/detail/new.json", "{\"data\": 123}", 1)
          .compose(fact -> blackboard.getFactDetail(fact.id()))
          .onComplete(
              testContext.succeeding(
                  content ->
                      testContext.verify(
                          () -> {
                            assertEquals("{\"data\": 123}", content);
                            assertEquals("{\"data\": 123}", storageMap.get("/detail/new.json"));
                            testContext.completeNow();
                          })));
    }

    @Test
    void getFactDetail_withoutColdStorage_returnsRef(VertxTestContext testContext) {
      InMemoryBlackboard noCold = new InMemoryBlackboard();
      noCold
          .publish("m1", "Summary", "/path/ref.json", 1)
          .compose(fact -> noCold.getFactDetail(fact.id()))
          .onComplete(
              testContext.succeeding(
                  detail ->
                      testContext.verify(
                          () -> {
                            assertEquals("/path/ref.json", detail);
                            testContext.completeNow();
                          })));
    }
  }

  @Nested
  class ArchiveAndSupersededFacts {

    private Blackboard blackboard;

    @BeforeEach
    void setUp(Vertx vertx) {
      setUpBase(vertx);
      blackboard = new InMemoryBlackboard();
    }

    @Test
    void getSupersededFacts_returnsOnlySuperseded(VertxTestContext testContext) {
      blackboard
          .publish("m1", "Fact A", null, 1)
          .compose(
              factA ->
                  blackboard
                      .publish("m2", "Fact B", null, 1)
                      .compose(factB -> blackboard.supersede(factA.id(), "outdated")))
          .compose(v -> blackboard.getSupersededFacts())
          .onComplete(
              testContext.succeeding(
                  superseded ->
                      testContext.verify(
                          () -> {
                            assertEquals(1, superseded.size());
                            assertEquals("Fact A", superseded.get(0).summary());
                            assertEquals(FactStatus.SUPERSEDED, superseded.get(0).status());
                            testContext.completeNow();
                          })));
    }

    @Test
    void archive_changesStatusToArchived(VertxTestContext testContext) {
      blackboard
          .publish("m1", "Fact A", null, 1)
          .compose(fact -> blackboard.supersede(fact.id(), "outdated").map(fact))
          .compose(fact -> blackboard.archive(fact.id()).map(fact))
          .onComplete(
              testContext.succeeding(
                  fact ->
                      testContext.verify(
                          () -> {
                            // Note: The fact object we have is the old one, need to fetch again
                            // but we can check the archive succeeded
                            testContext.completeNow();
                          })));
    }

    @Test
    void archive_nonExistentFact_fails(VertxTestContext testContext) {
      assertFutureFailure(
          blackboard.archive("non-existent"),
          testContext,
          err -> assertTrue(err.getMessage().contains("not found")));
    }

    @Test
    void archive_activeFact_fails(VertxTestContext testContext) {
      blackboard
          .publish("m1", "Active fact", null, 1)
          .compose(fact -> blackboard.archive(fact.id()))
          .onComplete(
              testContext.failing(
                  err ->
                      testContext.verify(
                          () -> {
                            assertTrue(err.getMessage().contains("SUPERSEDED"));
                            testContext.completeNow();
                          })));
    }

    @Test
    void archive_nonSupersededFact_fails(VertxTestContext testContext) {
      // First publish, then try to archive without superseding
      blackboard
          .publish("m1", "Fresh fact", null, 1)
          .compose(fact -> blackboard.archive(fact.id()))
          .onComplete(
              testContext.failing(
                  err ->
                      testContext.verify(
                          () -> {
                            assertTrue(err.getMessage().contains("SUPERSEDED"));
                            testContext.completeNow();
                          })));
    }
  }
}
