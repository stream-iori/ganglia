package work.ganglia.api.webui.model;

/**
 * Event for high-frequency terminal output stream, sent on a separate bypass topic.
 */
public record TtyEvent(
    String toolCallId,
    String text,
    boolean isError
) {}
