package work.ganglia.port.external.tool;

/**
 * Types of observations that can occur during the agent loop.
 */
public enum ObservationType {
    /**
     * A new turn has started.
     */
    TURN_STARTED,

    /**
     * Reasoning phase has started.
     */
    REASONING_STARTED,

    /**
     * A token has been received from the model (for streaming).
     */
    TOKEN_RECEIVED,

    /**
     * Reasoning phase has finished.
     */
    REASONING_FINISHED,

    /**
     * A tool execution has started.
     */
    TOOL_STARTED,

    /**
     * A tool execution has finished.
     */
    TOOL_FINISHED,

    /**
     * The turn has completed.
     */
    TURN_FINISHED,

    /**
     * An error occurred in the loop.
     */
    ERROR,

    /**
     * A system-level event like context compression.
     */
    SYSTEM_EVENT
}
