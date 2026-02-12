package me.stream.ganglia.core;

import me.stream.ganglia.core.llm.ModelGateway;
import me.stream.ganglia.core.loop.ReActAgentLoop;
import me.stream.ganglia.core.session.SessionManager;
import me.stream.ganglia.tools.ToolExecutor;

/**
 * A container for the bootstrapped Ganglia core components.
 */
public record Ganglia(
    ModelGateway modelGateway,
    ToolExecutor toolExecutor,
    SessionManager sessionManager,
    ReActAgentLoop agentLoop
) {}
