package work.ganglia.stubs;

import io.vertx.core.Future;

import work.ganglia.kernel.loop.AgentLoop;
import work.ganglia.port.chat.SessionContext;
import work.ganglia.port.internal.state.AgentSignal;

/**
 * Stub implementation of {@link AgentLoop} for testing.
 *
 * <p>Returns a predefined response for any input.
 */
public class StubAgentLoop implements AgentLoop {

  private final String response;
  private final boolean shouldFail;

  /** Creates a stub that returns the given response. */
  public StubAgentLoop(String response) {
    this(response, false);
  }

  /**
   * Creates a stub that either returns the response or fails.
   *
   * @param response the response to return on success
   * @param shouldFail if true, run() will return a failed future
   */
  public StubAgentLoop(String response, boolean shouldFail) {
    this.response = response;
    this.shouldFail = shouldFail;
  }

  @Override
  public Future<String> run(String userInput, SessionContext context, AgentSignal signal) {
    if (shouldFail) {
      return Future.failedFuture("StubAgentLoop: simulated failure");
    }
    return Future.succeededFuture(response);
  }

  @Override
  public Future<String> resume(
      String askId, String toolOutput, SessionContext context, AgentSignal signal) {
    if (shouldFail) {
      return Future.failedFuture("StubAgentLoop: simulated failure");
    }
    return Future.succeededFuture(response);
  }

  @Override
  public void stop(String sessionId) {
    // No-op for testing
  }
}
