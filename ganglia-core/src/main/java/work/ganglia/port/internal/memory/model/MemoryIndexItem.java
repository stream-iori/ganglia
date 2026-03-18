package work.ganglia.port.internal.memory.model;

import java.time.Instant;

/**
 * 1. Progressive Disclosure Index Item
 * Contains only metadata, injected into the Agent's lightweight Context.
 */
public record MemoryIndexItem(
    String id, 
    String title, 
    MemoryCategory category, 
    Instant timestamp
) {}