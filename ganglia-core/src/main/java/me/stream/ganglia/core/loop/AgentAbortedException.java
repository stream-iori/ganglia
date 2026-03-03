package me.stream.ganglia.core.loop;

/**
 * Thrown when the agent loop is forcefully aborted via an AgentSignal.
 */
public class AgentAbortedException extends RuntimeException {
    public AgentAbortedException() {
        super("Agent execution was forcefully aborted by user signal.");
    }
}
