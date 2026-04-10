package work.ganglia.kernel.subagent;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;

import work.ganglia.BaseGangliaTest;
import work.ganglia.port.chat.SessionContext;

@ExtendWith(VertxExtension.class)
class ContextScoperTest extends BaseGangliaTest {

  private SessionContext parentContext;

  @BeforeEach
  void setUp(Vertx vertx) {
    setUpBase(vertx);
    parentContext = createSessionContext("parent-session");
  }

  @Test
  void scope_propagatesMissionContextFromParentMetadata() {
    // Put mission_context in parent metadata
    Map<String, Object> parentMeta = new HashMap<>(parentContext.metadata());
    parentMeta.put("mission_context", "Fix the login bug");
    parentContext =
        new SessionContext(
            parentContext.sessionId(),
            parentContext.previousTurns(),
            parentContext.currentTurn(),
            parentMeta,
            parentContext.activeSkillIds(),
            parentContext.modelOptions(),
            parentContext.compressionState());

    SessionContext child = ContextScoper.scope("child-1", parentContext, null);

    assertEquals("Fix the login bug", child.metadata().get("mission_context"));
  }

  @Test
  void scope_extraMetadataOverridesParentMissionContext() {
    Map<String, Object> parentMeta = new HashMap<>(parentContext.metadata());
    parentMeta.put("mission_context", "Original mission");
    parentContext =
        new SessionContext(
            parentContext.sessionId(),
            parentContext.previousTurns(),
            parentContext.currentTurn(),
            parentMeta,
            parentContext.activeSkillIds(),
            parentContext.modelOptions(),
            parentContext.compressionState());

    Map<String, Object> extra = Map.of("mission_context", "Refined sub-mission");
    SessionContext child = ContextScoper.scope("child-1", parentContext, extra);

    assertEquals("Refined sub-mission", child.metadata().get("mission_context"));
  }

  @Test
  void scope_noMissionContextWhenAbsent() {
    SessionContext child = ContextScoper.scope("child-1", parentContext, null);

    assertNull(child.metadata().get("mission_context"));
  }

  @Test
  void scope_childHasCleanHistory() {
    SessionContext child = ContextScoper.scope("child-1", parentContext, null);

    assertTrue(child.previousTurns().isEmpty());
    assertNull(child.currentTurn());
  }
}
