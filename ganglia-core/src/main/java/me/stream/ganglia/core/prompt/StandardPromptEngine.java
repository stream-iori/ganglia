package me.stream.ganglia.core.prompt;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import me.stream.ganglia.memory.KnowledgeBase;
import me.stream.ganglia.memory.TokenCounter;
import me.stream.ganglia.core.model.*;
import me.stream.ganglia.core.prompt.context.*;
import me.stream.ganglia.skills.SkillPromptInjector;
import me.stream.ganglia.skills.SkillSuggester;
import me.stream.ganglia.tools.ToolExecutor;

import java.util.ArrayList;
import java.util.List;

/**
 * Standard implementation of PromptEngine using the ContextEngine mechanism.
 */
public class StandardPromptEngine implements PromptEngine {
    private final List<ContextSource> sources = new ArrayList<>();
    private final ContextComposer composer;
    private final TokenCounter tokenCounter;
    private final ToolExecutor toolExecutor;

    public StandardPromptEngine(Vertx vertx,
                                KnowledgeBase knowledgeBase,
                                SkillPromptInjector skillInjector,
                                SkillSuggester skillSuggester,
                                ToolExecutor toolExecutor,
                                TokenCounter tokenCounter) {
        MarkdownContextResolver resolver = new MarkdownContextResolver(vertx);
        this.tokenCounter = tokenCounter;
        this.composer = new ContextComposer(this.tokenCounter);
        this.toolExecutor = toolExecutor;

        // Initialize default sources
        sources.add(new PersonaContextSource());
        sources.add(new FileContextSource(vertx, resolver, "GANGLIA.md"));
        sources.add(new EnvironmentSource(vertx));
        sources.add(new SkillContextSource(skillInjector, skillSuggester));
        sources.add(new ToolContextSource(this.toolExecutor));
        sources.add(new ToDoContextSource());
        sources.add(new MemoryContextSource());
    }

    public void addContextSource(ContextSource source) {
        this.sources.add(source);
    }

    @Override
    public Future<String> buildSystemPrompt(SessionContext context) {
        List<Future<List<ContextFragment>>> futures = sources.stream()
            .map(s -> s.getFragments(context))
            .toList();

        return Future.join(futures)
            .map(v -> futures.stream()
                .map(Future::result)
                .flatMap(List::stream)
                .toList()
            )
            .map(allFragments -> composer.compose(allFragments, 2000));
    }

    @Override
    public Future<LLMRequest> prepareRequest(SessionContext context, int iteration) {
        return buildSystemPrompt(context).map(systemPromptContent -> {
            List<Message> modelHistory = new ArrayList<>();
            // 1. Add System Message
            modelHistory.add(Message.system(systemPromptContent));

            // 2. Prune and add session history
            List<Message> prunedHistory = context.getPrunedHistory(2000, tokenCounter); // Keep last 2000 tokens of history
            modelHistory.addAll(prunedHistory);

            // 3. Resolve Model Options
            ModelOptions currentOptions = context.modelOptions();
            if (currentOptions == null) {
                // TODO: Hardcoded fallback, should ideally come from ConfigManager via constructor if needed
                currentOptions = new ModelOptions(0.0, 4096, "gpt-4o");
            }

            // 4. Resolve Tools
            var tools = toolExecutor.getAvailableTools(context);

            return new LLMRequest(modelHistory, tools, currentOptions);
        });
    }
}
