package work.ganglia.memory;

import io.vertx.core.Future;
import work.ganglia.core.model.SessionContext;
import work.ganglia.core.prompt.context.ContextFragment;

import java.util.List;

/**
 * A pluggable module for the Ganglia Memory system.
 * Memory modules can provide context before reasoning and react to system events.
 */
public interface MemoryModule {
    
    /**
     * @return The unique identifier of this memory module.
     */
    String id();

    /**
     * Provides memory context fragments to be injected into the prompt.
     *
     * @param context The current session context.
     * @return A Future completing with a list of context fragments.
     */
    Future<List<ContextFragment>> provideContext(SessionContext context);

    /**
     * Handles a memory lifecycle event.
     *
     * @param event The memory event.
     * @return A Future completing when the event is handled.
     */
    Future<Void> onEvent(MemoryEvent event);
}