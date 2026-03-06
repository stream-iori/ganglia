package work.ganglia.infrastructure.internal.prompt;

import work.ganglia.util.Constants;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import work.ganglia.port.external.llm.LLMRequest;
import work.ganglia.port.chat.Message;
import work.ganglia.port.external.llm.ModelOptions;
import work.ganglia.port.chat.SessionContext;
import work.ganglia.infrastructure.internal.prompt.context.*;
import work.ganglia.port.internal.memory.MemoryService;
import work.ganglia.infrastructure.internal.memory.TokenCounter;
import work.ganglia.port.chat.*;
import work.ganglia.port.external.llm.*;
import work.ganglia.port.external.tool.*;
import work.ganglia.port.internal.state.*;;
import work.ganglia.infrastructure.internal.prompt.context.*;
import work.ganglia.kernel.task.SchedulableFactory;
import work.ganglia.infrastructure.internal.skill.SkillRuntime;
import work.ganglia.port.internal.prompt.PromptEngine;
import work.ganglia.port.internal.prompt.ContextSource;

import java.util.ArrayList;
import java.util.List;

/**
 * Standard implementation of PromptEngine using the ContextEngine mechanism.
 */
public class StandardPromptEngine implements PromptEngine {
    private final List<ContextSource> sources = new ArrayList<>();
    private final ContextComposer composer;
    private final TokenCounter tokenCounter;
    private SchedulableFactory scheduleableFactory;

    public StandardPromptEngine(Vertx vertx,
                                MemoryService memoryService,
                                SkillRuntime skillRuntime,
                                SchedulableFactory scheduleableFactory,
                                TokenCounter tokenCounter) {
        MarkdownContextResolver resolver = new MarkdownContextResolver(vertx);
        this.tokenCounter = tokenCounter;
        this.composer = new ContextComposer(this.tokenCounter);
        this.scheduleableFactory = scheduleableFactory;

        // Initialize default sources
        sources.add(new PersonaContextSource());
        sources.add(new FileContextSource(vertx, resolver, Constants.FILE_GANGLIA_MD));
        sources.add(new EnvironmentSource(vertx));
        sources.add(new SkillContextSource(skillRuntime));
        if (this.scheduleableFactory != null) {
            sources.add(new ToolContextSource(this.scheduleableFactory));
        }
        sources.add(new ToDoContextSource());
        sources.add(new MemoryContextSource(memoryService));
        sources.add(new SubAgentContextSource());
    }

    public void setSchedulableFactory(SchedulableFactory scheduleableFactory) {
        this.scheduleableFactory = scheduleableFactory;
        // Re-add ToolContextSource if it wasn't added during construction
        boolean hasToolSource = sources.stream().anyMatch(s -> s instanceof ToolContextSource);
        if (!hasToolSource && scheduleableFactory != null) {
            sources.add(new ToolContextSource(scheduleableFactory));
        }
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
                currentOptions = new ModelOptions(0.0, 4096, "gpt-4o", true);
            }

            // 4. Resolve Tools (with sub-agent filtering)
            var tools = scheduleableFactory.getAvailableDefinitions(context);

            boolean isSub = (boolean) context.metadata().getOrDefault("is_sub_agent", false);
            if (isSub) {
                String persona = (String) context.metadata().getOrDefault("sub_agent_persona", "GENERAL");
                if ("INVESTIGATOR".equals(persona)) {
                    // Filter out tools that modify files
                    tools = tools.stream()
                        .filter(t -> !t.name().equals("write_file") && !t.name().equals("replace_in_file") && !t.name().equals("run_shell_command"))
                        .toList();
                }
            }

            return new LLMRequest(modelHistory, tools, currentOptions);
        });
    }
}
