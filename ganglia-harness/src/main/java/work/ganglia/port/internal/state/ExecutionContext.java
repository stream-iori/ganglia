package work.ganglia.port.internal.state;

/**
 * Provides execution context for tools and other components, allowing them to report progress and
 * errors without depending on underlying infrastructure (e.g., Vert.x EventBus).
 */
public interface ExecutionContext {
  /**
   * @return The ID of the current session.
   */
  String sessionId();

  /**
   * @return The ID of the current span.
   */
  default String spanId() {
    return null;
  }

  /**
   * Emits a chunk of streaming output (e.g., TTY logs or generated tokens).
   *
   * @param chunk The output chunk.
   */
  void emitStream(String chunk);

  /**
   * Emits an error that occurred during execution.
   *
   * @param error The exception.
   */
  void emitError(Throwable error);
}
