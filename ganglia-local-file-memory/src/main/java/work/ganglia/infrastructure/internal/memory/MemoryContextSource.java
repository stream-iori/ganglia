package work.ganglia.infrastructure.internal.memory;

import java.util.List;
import java.util.stream.Collectors;

import io.vertx.core.Future;

import work.ganglia.port.chat.SessionContext;
import work.ganglia.port.internal.memory.MemoryStore;
import work.ganglia.port.internal.prompt.ContextFragment;
import work.ganglia.port.internal.prompt.ContextSource;

public class MemoryContextSource implements ContextSource {

  private final MemoryStore memoryStore;

  public MemoryContextSource(MemoryStore memoryStore) {
    this.memoryStore = memoryStore;
  }

  @Override
  public Future<List<ContextFragment>> getFragments(SessionContext sessionContext) {
    return memoryStore
        .getRecentIndex(10)
        .map(
            items -> {
              if (items == null || items.isEmpty()) {
                return List.of();
              }

              String content =
                  items.stream()
                      .map(
                          item ->
                              String.format(
                                  "- ID: %s | Category: %s | Title: %s",
                                  item.id(), item.category(), item.title()))
                      .collect(Collectors.joining("\n"));

              ContextFragment fragment =
                  new ContextFragment(
                      "Memory Index (Progressive Disclosure)",
                      "The following are titles of recent compressed observations and memories. Use `recall_memory` tool with the ID to view the full details if they seem relevant.\n"
                          + content,
                      40, // Priority
                      false // Not mandatory
                      );

              return List.of(fragment);
            });
  }
}
