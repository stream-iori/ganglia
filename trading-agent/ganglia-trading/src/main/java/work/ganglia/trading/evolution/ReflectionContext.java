package work.ganglia.trading.evolution;

import work.ganglia.kernel.loop.AgentLoopFactory;
import work.ganglia.port.chat.SessionContext;
import work.ganglia.trading.memory.TradingMemoryStore;

/**
 * Encapsulates the context needed for a single role's reflection. Replaces the 7-parameter method
 * signature in {@link Reflector#reflectForRole}.
 */
public record ReflectionContext(
    String persona,
    String roleDecision,
    String marketSituation,
    String tradeOutcome,
    TradingMemoryStore memoryStore,
    AgentLoopFactory loopFactory,
    SessionContext parentContext) {}
