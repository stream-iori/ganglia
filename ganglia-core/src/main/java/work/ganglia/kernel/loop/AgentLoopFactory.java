package work.ganglia.kernel.loop;

/**
 * Factory for creating AgentLoop instances.
 * This allows sub-tasks and sub-agents to spawn new execution loops
 * without needing a reference to the entire AgentEnv or all its dependencies.
 */
@FunctionalInterface
public interface AgentLoopFactory {
    /**
     * Creates a new, ready-to-use AgentLoop instance.
     *
     * @return A new AgentLoop.
     */
    AgentLoop createLoop();
}
