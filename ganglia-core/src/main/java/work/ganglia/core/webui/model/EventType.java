package work.ganglia.core.webui.model;

/**
 * Enumeration of event types sent from server to client.
 */
public enum EventType {
    THOUGHT,
    TOOL_START,
    TOOL_OUTPUT_STREAM,
    TOOL_RESULT,
    ASK_USER,
    AGENT_MESSAGE,
    SYSTEM_ERROR,
    FILE_CONTENT,
    FILE_TREE,
    TOKEN,
    USER_MESSAGE
}
