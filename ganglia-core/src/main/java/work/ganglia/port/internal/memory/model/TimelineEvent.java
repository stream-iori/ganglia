package work.ganglia.port.internal.memory.model;

import java.time.Instant;
import java.util.List;

/**
 * 5. Automated Timeline Event
 */
public record TimelineEvent(
    String eventId,
    String description,
    MemoryCategory category,
    Instant timestamp,
    List<String> affectedFiles
) {}