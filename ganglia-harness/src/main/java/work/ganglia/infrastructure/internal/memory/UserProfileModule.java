package work.ganglia.infrastructure.internal.memory;

import java.util.List;

import io.vertx.core.Future;

import work.ganglia.port.chat.SessionContext;
import work.ganglia.port.internal.memory.LongTermMemory;
import work.ganglia.port.internal.memory.MemoryEvent;
import work.ganglia.port.internal.memory.MemoryModule;
import work.ganglia.port.internal.prompt.ContextFragment;

/**
 * Memory module for user profile management. Injects user profile context into prompts and provides
 * guidance on saving user-specific information separately from project knowledge.
 */
public class UserProfileModule implements MemoryModule {
  private final LongTermMemory longTermMemory;

  public UserProfileModule(LongTermMemory longTermMemory) {
    this.longTermMemory = longTermMemory;
  }

  @Override
  public String id() {
    return "user-profile";
  }

  @Override
  public Future<List<ContextFragment>> provideContext(SessionContext context) {
    return longTermMemory
        .readUserProfile()
        .map(
            content -> {
              if (content == null || content.isBlank()) {
                return List.of(buildGuidanceFragment());
              }
              return List.of(buildProfileFragment(content), buildGuidanceFragment());
            })
        .recover(err -> Future.succeededFuture(List.of(buildGuidanceFragment())));
  }

  @Override
  public Future<Void> onEvent(MemoryEvent event) {
    if (event.type() == MemoryEvent.EventType.SESSION_CLOSED) {
      return longTermMemory.ensureUserProfileInitialized();
    }
    return Future.succeededFuture();
  }

  private ContextFragment buildProfileFragment(String content) {
    return ContextFragment.prunable(
        "User Profile",
        "The following is the user's profile (communication style, technical background, preferences):\n\n"
            + content,
        ContextFragment.PRIORITY_MEMORY - 5); // Slightly higher priority than general memory
  }

  private ContextFragment buildGuidanceFragment() {
    String guidance =
        """
        - You maintain separate knowledge stores: 'project' for project knowledge and 'user' for user profile.
        - Use `remember(fact, target="user")` to save user preferences, communication style, or technical background.
        - Use `remember(fact)` or `remember(fact, target="project")` for project conventions, architecture decisions, and lessons learned.
        - User profile persists across projects; project knowledge is project-specific.""";
    return ContextFragment.prunable(
        "Memory Guidance", guidance, ContextFragment.PRIORITY_MEMORY + 5);
  }
}
