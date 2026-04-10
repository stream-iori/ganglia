package work.ganglia.port.internal.memory;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import io.vertx.core.json.JsonObject;

class MemoryEventTest {

  @Test
  void backwardCompatibleConstructor_setsZeroCounts() {
    MemoryEvent event =
        new MemoryEvent(MemoryEvent.EventType.TURN_COMPLETED, "session-1", "goal", null);

    assertEquals(MemoryEvent.EventType.TURN_COMPLETED, event.type());
    assertEquals("session-1", event.sessionId());
    assertEquals("goal", event.goal());
    assertNull(event.turn());
    assertEquals(0, event.turnCount());
    assertEquals(0, event.toolCallCount());
  }

  @Test
  void fullConstructor_preservesCounts() {
    MemoryEvent event =
        new MemoryEvent(MemoryEvent.EventType.SESSION_CLOSED, "session-2", "deploy", null, 15, 42);

    assertEquals(15, event.turnCount());
    assertEquals(42, event.toolCallCount());
  }

  @Test
  void jsonSerialization_roundTrips() {
    MemoryEvent original =
        new MemoryEvent(MemoryEvent.EventType.TURN_COMPLETED, "session-3", "test", null, 5, 10);

    JsonObject json = JsonObject.mapFrom(original);
    MemoryEvent deserialized = json.mapTo(MemoryEvent.class);

    assertEquals(original.type(), deserialized.type());
    assertEquals(original.sessionId(), deserialized.sessionId());
    assertEquals(original.turnCount(), deserialized.turnCount());
    assertEquals(original.toolCallCount(), deserialized.toolCallCount());
  }

  @Test
  void jsonDeserialization_withoutCounts_defaultsToZero() {
    // Simulate an old-format JSON that doesn't have turnCount/toolCallCount
    JsonObject json = new JsonObject();
    json.put("type", "TURN_COMPLETED");
    json.put("sessionId", "session-old");
    json.put("goal", "old goal");
    // No turnCount or toolCallCount fields

    MemoryEvent event = json.mapTo(MemoryEvent.class);

    assertEquals(MemoryEvent.EventType.TURN_COMPLETED, event.type());
    assertEquals("session-old", event.sessionId());
    assertEquals(0, event.turnCount());
    assertEquals(0, event.toolCallCount());
  }
}
