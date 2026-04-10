package work.ganglia.trading.memory;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.vertx.core.Vertx;
import io.vertx.junit5.VertxTestContext;

import work.ganglia.BaseGangliaTest;
import work.ganglia.infrastructure.internal.state.InMemoryBlackboard;
import work.ganglia.port.chat.Message;
import work.ganglia.port.chat.SessionContext;
import work.ganglia.port.internal.state.Blackboard;
import work.ganglia.trading.config.TradingConfig;

class TradingMemoryInterceptorTest extends BaseGangliaTest {

  private Blackboard blackboard;
  private TradingMemoryInterceptor interceptor;

  @BeforeEach
  void setUp(Vertx vertx) {
    setUpBase(vertx);
    blackboard = new InMemoryBlackboard();
    interceptor = new TradingMemoryInterceptor(blackboard);
  }

  @Nested
  class PreTurn {

    @Test
    void injectsRoleMemories_intoContext(VertxTestContext testContext) {
      blackboard
          .publish(
              "manager1", "Prior bull analysis on AAPL", null, 1, Map.of("role", "BULL_RESEARCHER"))
          .compose(
              f1 ->
                  blackboard.publish(
                      "manager1",
                      "Strong momentum indicators for AAPL",
                      null,
                      1,
                      Map.of("role", "BULL_RESEARCHER")))
          .compose(
              f2 -> {
                SessionContext context =
                    createSessionContext().withNewMetadata("sub_agent_persona", "BULL_RESEARCHER");
                return interceptor.preTurn(context, "Analyze AAPL");
              })
          .onComplete(
              testContext.succeeding(
                  result ->
                      testContext.verify(
                          () -> {
                            String memories = (String) result.metadata().get("injected_memories");
                            assertNotNull(memories);
                            assertTrue(memories.contains("Prior bull analysis on AAPL"));
                            assertTrue(memories.contains("Strong momentum indicators for AAPL"));
                            testContext.completeNow();
                          })));
    }

    @Test
    void filtersByRoleTag(VertxTestContext testContext) {
      blackboard
          .publish("manager1", "Bull finding", null, 1, Map.of("role", "BULL_RESEARCHER"))
          .compose(
              f1 ->
                  blackboard.publish(
                      "manager1", "Bear finding", null, 1, Map.of("role", "BEAR_RESEARCHER")))
          .compose(
              f2 -> {
                SessionContext context =
                    createSessionContext().withNewMetadata("sub_agent_persona", "BEAR_RESEARCHER");
                return interceptor.preTurn(context, "Analyze risk");
              })
          .onComplete(
              testContext.succeeding(
                  result ->
                      testContext.verify(
                          () -> {
                            String memories = (String) result.metadata().get("injected_memories");
                            assertNotNull(memories);
                            assertTrue(memories.contains("Bear finding"));
                            assertFalse(memories.contains("Bull finding"));
                            testContext.completeNow();
                          })));
    }

    @Test
    void injectsNothing_whenEmpty(VertxTestContext testContext) {
      SessionContext context =
          createSessionContext().withNewMetadata("sub_agent_persona", "BULL_RESEARCHER");

      assertFutureSuccess(
          interceptor.preTurn(context, "Analyze AAPL"),
          testContext,
          result -> {
            Object memories = result.metadata().get("injected_memories");
            assertTrue(memories == null || (memories instanceof String s && s.isEmpty()));
          });
    }
  }

  @Nested
  class PostTurn {

    @Test
    void capturesTurnOutput_asFact(VertxTestContext testContext) {
      SessionContext context =
          createSessionContext()
              .withNewMetadata("sub_agent_persona", "BULL_RESEARCHER")
              .withNewMetadata("cycle_number", 1);
      Message response = Message.assistant("Bull analysis: strong momentum");

      interceptor
          .postTurn(context, response)
          .compose(v -> blackboard.getActiveFacts())
          .onComplete(
              testContext.succeeding(
                  facts ->
                      testContext.verify(
                          () -> {
                            assertEquals(1, facts.size());
                            assertTrue(
                                facts.get(0).summary().contains("Bull analysis: strong momentum"));
                            testContext.completeNow();
                          })));
    }

    @Test
    void tagsFact_withCurrentRole(VertxTestContext testContext) {
      SessionContext context =
          createSessionContext()
              .withNewMetadata("sub_agent_persona", "BULL_RESEARCHER")
              .withNewMetadata("cycle_number", 1);
      Message response = Message.assistant("Bull analysis: strong momentum");

      interceptor
          .postTurn(context, response)
          .compose(v -> blackboard.getActiveFacts())
          .onComplete(
              testContext.succeeding(
                  facts ->
                      testContext.verify(
                          () -> {
                            assertEquals(1, facts.size());
                            assertEquals("BULL_RESEARCHER", facts.get(0).tags().get("role"));
                            testContext.completeNow();
                          })));
    }
  }

  @Nested
  class HistoricalMemory {

    @Test
    void injectsHistoricalMemories_fromBM25Store(VertxTestContext testContext) {
      TradingMemoryStore store = new TradingMemoryStore(TradingConfig.defaults());
      store
          .forRole("BULL_RESEARCHER")
          .addSituation(
              "Apple stock rising on strong iPhone sales",
              "Momentum continuation likely, services revenue is key driver");

      TradingMemoryInterceptor withHistory = new TradingMemoryInterceptor(blackboard, store);
      SessionContext context =
          createSessionContext().withNewMetadata("sub_agent_persona", "BULL_RESEARCHER");

      assertFutureSuccess(
          withHistory.preTurn(context, "Apple iPhone sales growth"),
          testContext,
          result -> {
            String historical = (String) result.metadata().get("historical_memories");
            assertNotNull(historical);
            assertTrue(historical.contains("Lessons from similar past situations"));
            assertTrue(historical.contains("Momentum continuation"));
          });
    }

    @Test
    void noHistoricalMemories_whenStoreEmpty(VertxTestContext testContext) {
      TradingMemoryStore store = new TradingMemoryStore(TradingConfig.defaults());
      TradingMemoryInterceptor withHistory = new TradingMemoryInterceptor(blackboard, store);
      SessionContext context =
          createSessionContext().withNewMetadata("sub_agent_persona", "BULL_RESEARCHER");

      assertFutureSuccess(
          withHistory.preTurn(context, "analyze"),
          testContext,
          result -> {
            assertNull(result.metadata().get("historical_memories"));
          });
    }

    @Test
    void nonReflectiveRole_skipsHistorical(VertxTestContext testContext) {
      TradingMemoryStore store = new TradingMemoryStore(TradingConfig.defaults());
      TradingMemoryInterceptor withHistory = new TradingMemoryInterceptor(blackboard, store);
      SessionContext context =
          createSessionContext().withNewMetadata("sub_agent_persona", "MARKET_ANALYST");

      assertFutureSuccess(
          withHistory.preTurn(context, "analyze"),
          testContext,
          result -> {
            assertNull(result.metadata().get("historical_memories"));
          });
    }
  }

  @Nested
  class RoleIsolation {

    @Test
    void bullMemories_notVisibleToBear(VertxTestContext testContext) {
      blackboard
          .publish("manager1", "Bull exclusive insight", null, 1, Map.of("role", "BULL_RESEARCHER"))
          .compose(
              f -> {
                SessionContext context =
                    createSessionContext().withNewMetadata("sub_agent_persona", "BEAR_RESEARCHER");
                return interceptor.preTurn(context, "Analyze risk");
              })
          .onComplete(
              testContext.succeeding(
                  result ->
                      testContext.verify(
                          () -> {
                            Object memories = result.metadata().get("injected_memories");
                            assertTrue(
                                memories == null || (memories instanceof String s && s.isEmpty()));
                            testContext.completeNow();
                          })));
    }
  }
}
