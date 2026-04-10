package work.ganglia.trading;

import java.util.Map;

import io.vertx.core.Future;

import work.ganglia.kernel.loop.AgentLoop;
import work.ganglia.kernel.loop.AgentLoopFactory;
import work.ganglia.port.chat.SessionContext;
import work.ganglia.port.external.tool.ObservationType;
import work.ganglia.port.internal.state.AgentSignal;
import work.ganglia.port.internal.state.ObservationDispatcher;

/**
 * Shared test utilities for creating stub AgentLoop instances and helpers used across trading-agent
 * tests.
 */
public final class StubAgentLoopSupport {

  private StubAgentLoopSupport() {}

  /**
   * Extracts the TASK: line from agent input, returning the lowercased task text. Falls back to the
   * entire input lowercased if no TASK: line is found.
   */
  public static String extractTaskLine(String userInput) {
    for (String line : userInput.split("\n")) {
      if (line.startsWith("TASK: ")) {
        return line.substring(6).toLowerCase();
      }
    }
    return userInput.toLowerCase();
  }

  /** Creates a no-op ObservationDispatcher for tests. */
  public static ObservationDispatcher noOpDispatcher() {
    return new ObservationDispatcher() {
      @Override
      public void dispatch(String sid, ObservationType type, String content) {}

      @Override
      public void dispatch(
          String sid, ObservationType type, String content, Map<String, Object> data) {}
    };
  }

  /**
   * Creates an AgentLoopFactory whose loop returns role-appropriate canned text based on the TASK:
   * line in the prompt.
   */
  public static AgentLoopFactory smartStubLoopFactory() {
    return () ->
        new AgentLoop() {
          @Override
          public Future<String> run(String userInput, SessionContext ctx, AgentSignal signal) {
            String taskLine = extractTaskLine(userInput);

            // Portfolio Manager — must produce a parseable trading signal
            if (taskLine.contains("portfolio risk recommendation")) {
              return Future.succeededFuture(
                  "**Final Verdict: BUY**\n**Confidence: 0.85**\nRationale: Strong fundamentals.");
            }
            // Risk debate auditors
            if (taskLine.contains("aggressive risk")) {
              return Future.succeededFuture("Aggressive: High upside potential justifies risk.");
            }
            if (taskLine.contains("balanced risk")) {
              return Future.succeededFuture("Balanced: Risk-reward ratio is favorable.");
            }
            if (taskLine.contains("conservative risk")) {
              return Future.succeededFuture("Conservative: Capital preservation concerns noted.");
            }
            // Trader phase
            if (taskLine.contains("trading action") || taskLine.contains("position sizing")) {
              return Future.succeededFuture(
                  "Trader proposal: Buy 100 shares at market.\n"
                      + "FINAL TRANSACTION PROPOSAL: **BUY**");
            }
            // Research debate nodes
            if (taskLine.contains("bullish")) {
              return Future.succeededFuture("Bull case: Strong momentum and growth trajectory.");
            }
            if (taskLine.contains("bearish")) {
              return Future.succeededFuture("Bear case: Overvaluation risks exist.");
            }
            if (taskLine.contains("investment verdict")) {
              return Future.succeededFuture("Investment verdict: Bullish with moderate risk.");
            }
            // Perception phase nodes
            if (taskLine.contains("market data")) {
              return Future.succeededFuture("Market analysis: Bullish momentum detected.");
            }
            if (taskLine.contains("fundamental data")) {
              return Future.succeededFuture("Fundamentals: Strong earnings growth.");
            }
            if (taskLine.contains("news")) {
              return Future.succeededFuture("News: Positive press coverage.");
            }
            if (taskLine.contains("social media")) {
              return Future.succeededFuture("Social: Positive community sentiment.");
            }
            if (taskLine.contains("aggregate and synthesize")) {
              return Future.succeededFuture(
                  "Perception summary: Overall bullish outlook for AAPL.");
            }
            return Future.succeededFuture("Generic analysis result.");
          }

          @Override
          public Future<String> resume(
              String askId, String toolOutput, SessionContext ctx, AgentSignal signal) {
            return Future.succeededFuture("resumed");
          }

          @Override
          public void stop(String sessionId) {}
        };
  }

  /** Creates an AgentLoopFactory whose loop always fails. */
  public static AgentLoopFactory failingLoopFactory() {
    return () ->
        new AgentLoop() {
          @Override
          public Future<String> run(String userInput, SessionContext ctx, AgentSignal signal) {
            return Future.failedFuture(new RuntimeException("Simulated agent failure"));
          }

          @Override
          public Future<String> resume(
              String askId, String toolOutput, SessionContext ctx, AgentSignal signal) {
            return Future.failedFuture(new RuntimeException("Simulated agent failure"));
          }

          @Override
          public void stop(String sessionId) {}
        };
  }
}
