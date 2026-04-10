package work.ganglia.kernel.subagent.blackboard;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

import work.ganglia.BaseGangliaTest;
import work.ganglia.kernel.loop.AgentLoop;
import work.ganglia.kernel.loop.AgentLoopFactory;
import work.ganglia.port.chat.SessionContext;
import work.ganglia.port.external.tool.ObservationType;
import work.ganglia.port.internal.state.FactStatus;
import work.ganglia.port.internal.state.ObservationDispatcher;

/**
 * Tests for {@link BlackboardSummarizer}.
 *
 * <p>Note: Full end-to-end tests require a working LLM gateway. These tests focus on the
 * orchestration logic.
 */
@ExtendWith(VertxExtension.class)
class BlackboardSummarizerTest extends BaseGangliaTest {

  private TestableBlackboardSummarizer summarizer;
  private TestObservationDispatcher dispatcher;
  private SessionContext parentContext;

  /** A testable subclass that allows controlling the LLM response. */
  static class TestableBlackboardSummarizer extends BlackboardSummarizer {
    private String stubResponse = "Test summary";
    private boolean shouldFail;

    TestableBlackboardSummarizer(
        work.ganglia.port.internal.state.Blackboard blackboard,
        AgentLoopFactory loopFactory,
        ObservationDispatcher dispatcher,
        String sessionId,
        int maxSupersededToSummarize) {
      super(blackboard, loopFactory, dispatcher, sessionId, maxSupersededToSummarize);
    }

    void setStubResponse(String response) {
      this.stubResponse = response;
    }

    void setShouldFail(boolean shouldFail) {
      this.shouldFail = shouldFail;
    }
  }

  static class TestAgentLoopFactory implements AgentLoopFactory {
    String response = "Test summary";
    boolean shouldFail = false;

    @Override
    public AgentLoop createLoop() {
      return new work.ganglia.stubs.StubAgentLoop(response, shouldFail);
    }
  }

  static class TestObservationDispatcher implements ObservationDispatcher {
    private final CopyOnWriteArrayList<RecordedEvent> events = new CopyOnWriteArrayList<>();

    record RecordedEvent(
        String sessionId, ObservationType type, String content, Map<String, Object> data) {}

    @Override
    public void dispatch(String sessionId, ObservationType type, String content) {
      events.add(new RecordedEvent(sessionId, type, content, Map.of()));
    }

    @Override
    public void dispatch(
        String sessionId, ObservationType type, String content, Map<String, Object> data) {
      events.add(new RecordedEvent(sessionId, type, content, data));
    }

    public boolean hasEvent(ObservationType type) {
      return events.stream().anyMatch(e -> e.type == type);
    }
  }

  @BeforeEach
  void setUp(Vertx vertx) {
    setUpBase(vertx);
    dispatcher = new TestObservationDispatcher();
    parentContext = createSessionContext("parent-session");
  }

  @Test
  void constructor_doesNotThrow() {
    var blackboard = new work.ganglia.infrastructure.internal.state.InMemoryBlackboard();
    var loopFactory = new TestAgentLoopFactory();
    var summarizer =
        new BlackboardSummarizer(blackboard, loopFactory, dispatcher, "test-session", 10);
    assertNotNull(summarizer);
  }

  @Test
  void summarize_withNoSupersededFacts_returnsEmpty(VertxTestContext testContext) {
    var blackboard = new work.ganglia.infrastructure.internal.state.InMemoryBlackboard();
    var loopFactory = new TestAgentLoopFactory();
    var summarizer =
        new BlackboardSummarizer(blackboard, loopFactory, dispatcher, "test-session", 10);

    summarizer
        .summarize(parentContext)
        .onComplete(
            testContext.succeeding(
                result ->
                    testContext.verify(
                        () -> {
                          assertEquals(0, result.archivedCount());
                          assertTrue(result.summaryMessage().contains("No superseded facts"));
                          testContext.completeNow();
                        })));
  }

  @Test
  void getSupersededFacts_returnsOnlySuperseded(VertxTestContext testContext) {
    var blackboard = new work.ganglia.infrastructure.internal.state.InMemoryBlackboard();

    blackboard
        .publish("m1", "Fact A", null, 1)
        .compose(
            factA ->
                blackboard
                    .publish("m2", "Fact B", null, 1)
                    .compose(factB -> blackboard.supersede(factA.id(), "outdated")))
        .compose(
            v ->
                ((work.ganglia.infrastructure.internal.state.InMemoryBlackboard) blackboard)
                    .getSupersededFacts())
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
    var blackboard = new work.ganglia.infrastructure.internal.state.InMemoryBlackboard();

    blackboard
        .publish("m1", "Test fact", null, 1)
        .compose(fact -> blackboard.supersede(fact.id(), "outdated").map(fact))
        .compose(fact -> blackboard.archive(fact.id()).map(fact))
        .onComplete(
            testContext.succeeding(
                v ->
                    testContext.verify(
                        () -> {
                          // Verify by checking that getSupersededFacts no longer returns it
                          ((work.ganglia.infrastructure.internal.state.InMemoryBlackboard)
                                  blackboard)
                              .getSupersededFacts()
                              .onComplete(
                                  testContext.succeeding(
                                      superseded ->
                                          testContext.verify(
                                              () -> {
                                                assertEquals(0, superseded.size());
                                                testContext.completeNow();
                                              })));
                        })));
  }
}
