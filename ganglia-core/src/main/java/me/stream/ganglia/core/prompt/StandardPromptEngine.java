package me.stream.ganglia.core.prompt;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import me.stream.ganglia.memory.KnowledgeBase;
import me.stream.ganglia.memory.TokenCounter;
import me.stream.ganglia.core.model.SessionContext;
import me.stream.ganglia.core.prompt.context.*;
import me.stream.ganglia.skills.SkillPromptInjector;
import me.stream.ganglia.skills.SkillSuggester;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Standard implementation of PromptEngine using the ContextEngine mechanism.
 */
public class StandardPromptEngine implements PromptEngine {
    private final List<ContextSource> sources = new ArrayList<>();
    private final ContextComposer composer;

    public StandardPromptEngine(Vertx vertx, KnowledgeBase knowledgeBase, SkillPromptInjector skillInjector, SkillSuggester skillSuggester) {
        MarkdownContextResolver resolver = new MarkdownContextResolver(vertx);
        this.composer = new ContextComposer(new TokenCounter());

        // Initialize default sources
        sources.add(new PersonaContextSource());
        sources.add(new FileContextSource(vertx, resolver, "GANGLIA.md"));
        sources.add(new EnvironmentSource(vertx));
        sources.add(new SkillContextSource(skillInjector, skillSuggester));
        sources.add(new ToDoContextSource());
        sources.add(new MemoryContextSource());
    }

    @Override
    public Future<String> buildSystemPrompt(SessionContext context) {
        List<Future<List<ContextFragment>>> futures = sources.stream()
                .map(s -> s.getFragments(context))
                .toList();

        return Future.all(futures)
                .map(CompositeFuture::list)
                .map(list -> {
                    List<ContextFragment> allFragments = new ArrayList<>();
                    for (Object obj : list) {
                        if (obj instanceof List) {
                            allFragments.addAll((Collection<? extends ContextFragment>) obj);
                        }
                    }
                    // Limit system prompt to roughly 2k tokens (configurable in future)
                    return composer.compose(allFragments, 2000);
                });
    }
}