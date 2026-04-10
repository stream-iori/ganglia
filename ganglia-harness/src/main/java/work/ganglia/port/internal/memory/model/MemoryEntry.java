package work.ganglia.port.internal.memory.model;

import java.time.Instant;
import java.util.List;

/** The core memory entity, containing both the compressed summary and full details. */
public record MemoryEntry(
    String id,
    String title,
    String summary, // Highly condensed context
    String fullContent, // Full details (not provided during progressive disclosure)
    MemoryCategory category,
    List<MemoryTag> tags,
    Instant timestamp,
    List<String> relatedFiles // Associated code or document paths
    ) {}
