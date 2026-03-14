package work.ganglia;

import work.ganglia.config.ConfigManager;
import work.ganglia.port.external.llm.ModelGateway;
import work.ganglia.kernel.AgentEnv;
import work.ganglia.kernel.loop.ReActAgentLoop;
import work.ganglia.port.internal.state.SessionManager;
import work.ganglia.port.external.tool.ToolExecutor;

/**
 * A container for the bootstrapped Ganglia core components.
 */
public record Ganglia(
    ModelGateway modelGateway,
    ToolExecutor toolExecutor,
    SessionManager sessionManager,
    ReActAgentLoop agentLoop,
    ConfigManager configManager,
    AgentEnv env
) {}
