package work.ganglia.trading.evolution;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Future;

import work.ganglia.kernel.loop.AgentLoop;
import work.ganglia.kernel.loop.AgentLoopFactory;
import work.ganglia.port.chat.SessionContext;
import work.ganglia.trading.memory.FinancialSituationMemory;
import work.ganglia.trading.memory.TradingMemoryStore;
import work.ganglia.trading.pipeline.TradingPipelineOrchestrator.PipelineResult;

/**
 * Post-pipeline reflector that performs 4-step reflection per role and writes structured lessons
 * into role-isolated BM25 memory.
 *
 * <p>Reflection steps: Reasoning -> Improvement -> Summary -> Query. Each role's reflection runs
 * independently and can be parallelized.
 */
public class Reflector {
  private static final Logger logger = LoggerFactory.getLogger(Reflector.class);

  /**
   * Reflects on a completed pipeline run, generating lessons for each reflective role.
   *
   * @param result the completed pipeline result
   * @param tradeOutcome description of the trade result (e.g. "+5% gain on AAPL")
   * @param memoryStore the role-isolated memory store to write lessons into
   * @param loopFactory factory for creating LLM agent loops
   * @param parentContext the parent session context
   * @return future that completes when all role reflections are done
   */
  public Future<Void> reflect(
      PipelineResult result,
      String tradeOutcome,
      TradingMemoryStore memoryStore,
      AgentLoopFactory loopFactory,
      SessionContext parentContext) {

    Map<String, String> roleDecisions = buildRoleDecisions(result);
    String marketSituation = result.perceptionReport();

    List<Future<Void>> futures = new ArrayList<>();
    for (var entry : roleDecisions.entrySet()) {
      String persona = entry.getKey();
      String decision = entry.getValue();

      if (!memoryStore.hasRole(persona)) {
        continue;
      }

      ReflectionContext ctx =
          new ReflectionContext(
              persona,
              decision,
              marketSituation,
              tradeOutcome,
              memoryStore,
              loopFactory,
              parentContext);
      futures.add(reflectForRole(ctx));
    }

    if (futures.isEmpty()) {
      return Future.succeededFuture();
    }

    return Future.all(futures).mapEmpty();
  }

  private Future<Void> reflectForRole(ReflectionContext ctx) {
    String prompt =
        ReflectorPrompts.buildReflectionPrompt(
            ctx.persona(), ctx.roleDecision(), ctx.marketSituation(), ctx.tradeOutcome());

    SessionContext sessionCtx =
        ctx.parentContext().withNewMetadata("sub_agent_persona", ctx.persona());
    AgentLoop loop = ctx.loopFactory().createLoop();

    logger.debug("Starting reflection for persona={}", ctx.persona());

    return loop.run(prompt, sessionCtx)
        .map(
            output -> {
              String summary = ReflectorPrompts.extractSummary(output);
              String query = ReflectorPrompts.extractQuery(output);
              String situation = query.isEmpty() ? ctx.marketSituation() : query;

              FinancialSituationMemory memory = ctx.memoryStore().forRole(ctx.persona());
              if (memory != null) {
                memory.addSituation(situation, summary);
                logger.debug(
                    "Stored reflection for persona={}: situation='{}', lesson='{}'",
                    ctx.persona(),
                    situation,
                    summary);
              }
              return (Void) null;
            })
        .recover(
            err -> {
              logger.warn("Reflection failed for persona={}: {}", ctx.persona(), err.getMessage());
              return Future.succeededFuture();
            });
  }

  private Map<String, String> buildRoleDecisions(PipelineResult result) {
    return Map.of(
        "BULL_RESEARCHER", extractOrDefault(result.debateReport(), "No debate output"),
        "BEAR_RESEARCHER", extractOrDefault(result.debateReport(), "No debate output"),
        "TRADER", extractOrDefault(result.traderReport(), "No trader output"),
        "INVEST_JUDGE", extractOrDefault(result.debateReport(), "No debate output"),
        "PORTFOLIO_MANAGER", extractOrDefault(result.riskReport(), "No risk output"));
  }

  private String extractOrDefault(String value, String defaultValue) {
    return (value != null && !value.isEmpty()) ? value : defaultValue;
  }
}
