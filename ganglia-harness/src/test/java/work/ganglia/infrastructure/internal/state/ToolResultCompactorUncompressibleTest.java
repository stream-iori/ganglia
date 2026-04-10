package work.ganglia.infrastructure.internal.state;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;

import work.ganglia.BaseGangliaTest;
import work.ganglia.port.chat.Message;
import work.ganglia.port.chat.SessionContext;
import work.ganglia.port.chat.Turn;

@ExtendWith(VertxExtension.class)
class ToolResultCompactorUncompressibleTest extends BaseGangliaTest {

  private ToolResultCompactor compactor;

  @BeforeEach
  void setUp(Vertx vertx) {
    setUpBase(vertx);
    compactor = new ToolResultCompactor();
  }

  @Test
  void realityAnchor_results_neverCompacted() {
    // Build a context with a reality_anchor tool result and a bash tool result
    Instant oldTime = Instant.now().minusSeconds(7200); // 2 hours ago

    Message userMsg = Message.user("Run tests");
    Message assistantMsg = Message.assistant("Running validation...");
    Message realityAnchorResult =
        new Message(
            "ra-msg-1",
            work.ganglia.port.chat.Role.TOOL,
            "# Validation Report\nunit-tests: PASSED",
            null,
            new Message.ToolObservation("tc-1", "reality_anchor"),
            oldTime);
    Message bashResult =
        new Message(
            "bash-msg-1",
            work.ganglia.port.chat.Role.TOOL,
            "lots of bash output here",
            null,
            new Message.ToolObservation("tc-2", "bash"),
            oldTime);

    Turn turn =
        new Turn(
            "turn-1", userMsg, List.of(assistantMsg, realityAnchorResult, bashResult), null, 0);

    SessionContext ctx =
        new SessionContext("test-session", List.of(turn), null, null, List.of(), null, null);

    // Compact with null filter (all tools) — reality_anchor should still be skipped
    SessionContext result = compactor.compactByCacheTtl(ctx, 1000, 0, null);

    // bash result should be cleared
    var steps = result.previousTurns().get(0).intermediateSteps();
    Message bashAfter =
        steps.stream()
            .filter(
                m -> m.toolObservation() != null && "bash".equals(m.toolObservation().toolName()))
            .findFirst()
            .orElse(null);
    assertNotNull(bashAfter);
    assertEquals("[Old tool result cleared]", bashAfter.content());

    // reality_anchor result should be preserved
    Message raAfter =
        steps.stream()
            .filter(
                m ->
                    m.toolObservation() != null
                        && "reality_anchor".equals(m.toolObservation().toolName()))
            .findFirst()
            .orElse(null);
    assertNotNull(raAfter);
    assertTrue(
        raAfter.content().contains("Validation Report"),
        "reality_anchor output should be preserved");
  }

  @Test
  void uncompressibleTools_setContainsRealityAnchor() {
    assertTrue(ToolResultCompactor.UNCOMPRESSIBLE_TOOLS.contains("reality_anchor"));
  }
}
