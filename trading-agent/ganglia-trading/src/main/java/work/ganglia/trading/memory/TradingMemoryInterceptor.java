package work.ganglia.trading.memory;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Future;

import work.ganglia.port.chat.Message;
import work.ganglia.port.chat.SessionContext;
import work.ganglia.port.internal.hook.AgentInterceptor;
import work.ganglia.port.internal.state.Blackboard;

/**
 * An AgentInterceptor that injects role-specific memories before each turn and captures new
 * memories after each turn. Combines two memory sources:
 *
 * <ol>
 *   <li>Blackboard facts (cycle-scoped, for current pipeline run)
 *   <li>BM25 historical memories (persistent, from past reflections)
 * </ol>
 */
public class TradingMemoryInterceptor implements AgentInterceptor {
  private static final Logger logger = LoggerFactory.getLogger(TradingMemoryInterceptor.class);
  private static final int HISTORICAL_TOP_K = 2;

  private final Blackboard blackboard;
  private final TradingMemoryStore memoryStore;

  public TradingMemoryInterceptor(Blackboard blackboard) {
    this(blackboard, null);
  }

  public TradingMemoryInterceptor(Blackboard blackboard, TradingMemoryStore memoryStore) {
    this.blackboard = blackboard;
    this.memoryStore = memoryStore;
  }

  @Override
  public Future<SessionContext> preTurn(SessionContext context, String userInput) {
    if (!(context.metadata().get("sub_agent_persona") instanceof String persona)) {
      return Future.succeededFuture(context);
    }
    logger.debug("Injecting role memories for persona={}", persona);
    return blackboard
        .getActiveFacts(Map.of("role", persona))
        .map(
            facts -> {
              SessionContext ctx = context;

              // 1. Inject cycle-scoped blackboard facts
              if (!facts.isEmpty()) {
                String memories =
                    facts.stream().map(f -> "- " + f.summary()).collect(Collectors.joining("\n"));
                logger.debug("Injected {} cycle facts for persona={}", facts.size(), persona);
                ctx = ctx.withNewMetadata("injected_memories", memories);
              }

              // 2. Inject BM25 historical memories from past reflections
              if (memoryStore != null && memoryStore.hasRole(persona)) {
                FinancialSituationMemory roleMemory = memoryStore.forRole(persona);
                if (roleMemory != null && roleMemory.size() > 0) {
                  List<FinancialSituationMemory.MemoryMatch> matches =
                      roleMemory.retrieve(userInput, HISTORICAL_TOP_K);
                  if (!matches.isEmpty()) {
                    String historical =
                        matches.stream()
                            .map(m -> "- " + m.advice())
                            .collect(Collectors.joining("\n"));
                    logger.debug(
                        "Injected {} historical memories for persona={}", matches.size(), persona);
                    ctx =
                        ctx.withNewMetadata(
                            "historical_memories",
                            "Lessons from similar past situations:\n" + historical);
                  }
                }
              }

              return ctx;
            });
  }

  @Override
  public Future<Void> postTurn(SessionContext context, Message finalResponse) {
    if (!(context.metadata().get("sub_agent_persona") instanceof String persona)
        || finalResponse == null
        || finalResponse.content() == null) {
      return Future.succeededFuture();
    }
    int cycleNumber = context.metadata().get("cycle_number") instanceof Integer n ? n : 1;
    logger.debug("Capturing turn output as fact for persona={}, cycle={}", persona, cycleNumber);
    return blackboard
        .publish(persona, finalResponse.content(), null, cycleNumber, Map.of("role", persona))
        .mapEmpty();
  }
}
