package work.ganglia.core;

import work.ganglia.core.config.ConfigManager;
import work.ganglia.core.llm.ModelGateway;
import work.ganglia.core.loop.StandardAgentLoop;
import work.ganglia.core.session.SessionManager;
import work.ganglia.tools.ToolExecutor;

/**
 * A container for the bootstrapped Ganglia core components.
 */
public record Ganglia(
    ModelGateway modelGateway,
    ToolExecutor toolExecutor,
    SessionManager sessionManager,
    StandardAgentLoop agentLoop,
    ConfigManager configManager
) {}
