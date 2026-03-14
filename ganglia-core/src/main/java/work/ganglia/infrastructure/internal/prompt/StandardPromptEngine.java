package work.ganglia.infrastructure.internal.prompt;

import work.ganglia.port.internal.prompt.ContextFragment;
import work.ganglia.kernel.todo.ToDoContextSource;
import work.ganglia.util.Constants;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import work.ganglia.port.external.llm.LLMRequest;
import work.ganglia.port.chat.Message;
import work.ganglia.port.external.llm.ModelOptions;
import work.ganglia.port.chat.SessionContext;
import work.ganglia.infrastructure.internal.prompt.context.*;
import work.ganglia.kernel.subagent.SubAgentContextSource;
import work.ganglia.port.internal.memory.MemoryService;
import work.ganglia.infrastructure.internal.memory.TokenCounter;
import work.ganglia.port.chat.*;
import work.ganglia.port.external.llm.*;
import work.ganglia.port.external.tool.*;
import work.ganglia.port.internal.state.*;;
import java.util.Collections;
import work.ganglia.infrastructure.internal.prompt.context.*;
import work.ganglia.kernel.subagent.SubAgentContextSource;
import work.ganglia.kernel.task.AgentTaskFactory;
import work.ganglia.port.internal.skill.SkillRuntime;
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
    private AgentTaskFactory taskFactory;

    public StandardPromptEngine(Vertx vertx,
                                MemoryService memoryService,
                                SkillRuntime skillRuntime,
                                AgentTaskFactory taskFactory,
                                TokenCounter tokenCounter) {
        MarkdownContextResolver resolver = new MarkdownContextResolver(vertx);
        this.tokenCounter = tokenCounter;
        this.composer = new ContextComposer(this.tokenCounter);
        this.taskFactory = taskFactory;

        // Initialize default sources
        sources.add(new PersonaContextSource());
        sources.add(new FileContextSource(vertx, resolver, Constants.FILE_GANGLIA_MD));
        sources.add(new EnvironmentSource(vertx));
        sources.add(new SkillContextSource(skillRuntime));
        if (this.taskFactory != null) {
            sources.add(new ToolContextSource(this.taskFactory));
        }
        sources.add(new ToDoContextSource());
        sources.add(new MemoryContextSource(memoryService));
        sources.add(new SubAgentContextSource());
    }

    public void setTaskFactory(AgentTaskFactory taskFactory) {
        this.taskFactory = taskFactory;
        // Re-add ToolContextSource if it wasn't added during construction
        boolean hasToolSource = sources.stream().anyMatch(s -> s instanceof ToolContextSource);
        if (!hasToolSource && taskFactory != null) {
            sources.add(new ToolContextSource(taskFactory));
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
            modelHistory.addAll(sanitizeHistory(prunedHistory));

            // 3. Resolve Model Options
            ModelOptions currentOptions = context.modelOptions();
            if (currentOptions == null) {
                // TODO: Hardcoded fallback, should ideally come from ConfigManager via constructor if needed
                currentOptions = new ModelOptions(0.0, 4096, "gpt-4o", true);
            }

            // 4. Resolve Tools (with sub-agent filtering)
            var tools = taskFactory.getAvailableDefinitions(context);

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

    private List<Message> sanitizeHistory(List<Message> history) {
        if (history == null || history.isEmpty()) return Collections.emptyList();
        
        List<Message> sanitized = new ArrayList<>();
        for (int i = 0; i < history.size(); i++) {
            Message current = history.get(i);
            
            // Rule 1: Collapse consecutive USER messages (keep only the LAST one)
            if (current.role() == Role.USER) {
                if (i + 1 < history.size() && history.get(i + 1).role() == Role.USER) {
                    continue; // Skip current, wait for the next one
                }
            }
            
            // Rule 2: Strategy B - Remove orphaned Assistant messages with tool calls
            if (current.role() == Role.ASSISTANT && current.toolCalls() != null && !current.toolCalls().isEmpty()) {
                boolean hasToolResponse = false;
                if (i + 1 < history.size() && history.get(i + 1).role() == Role.TOOL) {
                    hasToolResponse = true;
                }
                
                if (!hasToolResponse) {
                    continue; // Skip this assistant message because it has tool_calls but no tool response follows
                }
            }
            
            sanitized.add(current);
        }
        return sanitized;
    }
}
