package work.ganglia.infrastructure.external.llm;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import java.util.concurrent.Semaphore;
import work.ganglia.port.chat.Message;
import work.ganglia.port.chat.Role;
import work.ganglia.port.external.llm.ModelGateway;

/** Base class for ModelGateways to reduce boilerplate and enforce common constraints. */
public abstract class AbstractModelGateway implements ModelGateway {
  protected final Vertx vertx;
  private final Semaphore semaphore = new Semaphore(5); // Limit to 5 concurrent calls

  protected AbstractModelGateway(Vertx vertx) {
    this.vertx = vertx;
  }

  /** Emits a token received event via the ExecutionContext. */
  protected void publishToken(
      work.ganglia.port.internal.state.ExecutionContext context, String token) {
    if (token == null || token.isEmpty()) return;
    if (context != null) {
      context.emitStream(token);
    }
  }

  /** Common logic to merge multiple system messages into one if needed. */
  protected String mergeSystemMessages(java.util.List<Message> history) {
    return history.stream()
        .filter(m -> m.role() == Role.SYSTEM)
        .map(Message::content)
        .reduce((a, b) -> a + "\n" + b)
        .orElse(null);
  }

  /** Wraps a future with semaphore protection to limit concurrency. */
  protected <T> Future<T> withSemaphore(Future<T> future) {
    if (semaphore.tryAcquire()) {
      return future.onComplete(v -> semaphore.release());
    } else {
      return Future.failedFuture(
          new LLMException("Concurrency limit reached (max 5)", null, 429, null, null));
    }
  }
}
