package work.ganglia.infrastructure.internal.memory;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Future;

import work.ganglia.port.chat.SessionContext;
import work.ganglia.port.internal.memory.MemoryEvent;
import work.ganglia.port.internal.memory.MemoryModule;
import work.ganglia.port.internal.memory.SessionStore;
import work.ganglia.port.internal.memory.model.SessionRecord;
import work.ganglia.port.internal.prompt.ContextFragment;

/**
 * Memory module that persists session records to the SessionStore when sessions close. Tracks
 * session metadata (start time, goal) as events arrive, then saves the complete record on
 * SESSION_CLOSED.
 */
public class SessionPersistenceModule implements MemoryModule {
  private static final Logger logger = LoggerFactory.getLogger(SessionPersistenceModule.class);

  private final SessionStore sessionStore;
  private final ConcurrentHashMap<String, SessionMeta> activeSessions = new ConcurrentHashMap<>();

  private static class SessionMeta {
    volatile Instant startTime;
    volatile String goal;
  }

  public SessionPersistenceModule(SessionStore sessionStore) {
    this.sessionStore = sessionStore;
  }

  @Override
  public String id() {
    return "session-persistence";
  }

  @Override
  public Future<List<ContextFragment>> provideContext(SessionContext context) {
    return Future.succeededFuture(List.of());
  }

  @Override
  public Future<Void> onEvent(MemoryEvent event) {
    String sessionId = event.sessionId();

    switch (event.type()) {
      case TURN_COMPLETED -> {
        SessionMeta meta = activeSessions.computeIfAbsent(sessionId, k -> new SessionMeta());
        if (meta.startTime == null) {
          meta.startTime = Instant.now();
        }
        if (event.goal() != null && !event.goal().isBlank()) {
          meta.goal = event.goal();
        }
      }
      case SESSION_CLOSED -> {
        SessionMeta meta = activeSessions.remove(sessionId);
        Instant startTime = meta != null && meta.startTime != null ? meta.startTime : Instant.now();
        String goal = meta != null && meta.goal != null ? meta.goal : "(no goal recorded)";
        Instant endTime = Instant.now();

        SessionRecord record =
            new SessionRecord(
                sessionId,
                goal,
                "", // summary will be empty for now; Phase 5 can add LLM summarization
                event.turnCount(),
                event.toolCallCount(),
                startTime,
                endTime);

        return sessionStore
            .saveSession(record)
            .onSuccess(v -> logger.debug("Session persisted: {}", sessionId))
            .onFailure(err -> logger.error("Failed to persist session: {}", sessionId, err));
      }
      default -> {}
    }
    return Future.succeededFuture();
  }
}
