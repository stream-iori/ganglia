package work.ganglia.port.internal.memory.model;

import java.util.List;

/** 3. Hybrid Search Query Multi-dimensional filtering (Text, Category, Tags). */
public record MemoryQuery(
    String keyword, // Text match (full text or title/summary)
    List<MemoryCategory> categories, // Filter by category
    List<MemoryTag> tags, // Filter by tags
    int limit) {}
