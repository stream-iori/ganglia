package work.ganglia.infrastructure.internal.prompt;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import work.ganglia.infrastructure.internal.prompt.context.*;
import work.ganglia.kernel.subagent.SubAgentContextSource;
import work.ganglia.kernel.task.AgentTaskFactory;
import work.ganglia.port.chat.*;
import work.ganglia.port.chat.Message;
import work.ganglia.port.chat.SessionContext;
import work.ganglia.port.external.llm.LLMRequest;
import work.ganglia.port.external.llm.ModelOptions;
import work.ganglia.port.external.tool.*;
import work.ganglia.port.internal.memory.MemoryService;
import work.ganglia.port.internal.prompt.ContextFragment;
import work.ganglia.port.internal.prompt.ContextSource;
import work.ganglia.port.internal.prompt.GuidelineContextSource;
import work.ganglia.port.internal.prompt.PromptEngine;
import work.ganglia.port.internal.prompt.WorkflowContextSource;
import work.ganglia.port.internal.skill.SkillRuntime;
import work.ganglia.port.internal.state.*;
import work.ganglia.util.TokenCounter;

/** Standard implementation of PromptEngine using the ContextEngine mechanism. */
public class StandardPromptEngine implements PromptEngine {
  private final List<ContextSource> sources = new ArrayList<>();
  private final ContextComposer composer;
  private final TokenCounter tokenCounter;
  private final Vertx vertx;
  private final MemoryService memoryService;
  private final SkillRuntime skillRuntime;
  private final work.ganglia.config.ModelConfigProvider modelConfigProvider;
  private AgentTaskFactory taskFactory;

  public StandardPromptEngine(
      Vertx vertx,
      MemoryService memoryService,
      SkillRuntime skillRuntime,
      AgentTaskFactory taskFactory,
      TokenCounter tokenCounter,
      work.ganglia.config.ModelConfigProvider modelConfigProvider) {
    this(
        vertx,
        memoryService,
        skillRuntime,
        taskFactory,
        tokenCounter,
        List.of(),
        modelConfigProvider);
  }

  public StandardPromptEngine(
      Vertx vertx,
      MemoryService memoryService,
      SkillRuntime skillRuntime,
      AgentTaskFactory taskFactory,
      TokenCounter tokenCounter,
      List<ContextSource> extraSources,
      work.ganglia.config.ModelConfigProvider modelConfigProvider) {
    this.tokenCounter = tokenCounter;
    this.composer = new ContextComposer(this.tokenCounter);
    this.taskFactory = taskFactory;
    this.vertx = vertx;
    this.memoryService = memoryService;
    this.skillRuntime = skillRuntime;
    this.modelConfigProvider = modelConfigProvider;

    if (extraSources != null) {
      sources.addAll(extraSources);
    }

    ensureCoreSources();
  }

  private void ensureCoreSources() {
    if (sources.stream().noneMatch(s -> s instanceof PersonaContextSource)) {
      sources.add(new PersonaContextSource());
    }
    if (sources.stream().noneMatch(s -> s instanceof WorkflowContextSource)) {
      sources.add(
          sessionContext ->
              io.vertx.core.Future.succeededFuture(
                  List.of(
                      work.ganglia.port.internal.prompt.ContextFragment.mandatory(
                          "Workflow",
                          "You operate using an iterative Plan -> Act -> Validate loop.",
                          work.ganglia.port.internal.prompt.ContextFragment.PRIORITY_WORKFLOW))));
    }
    if (sources.stream().noneMatch(s -> s instanceof GuidelineContextSource)) {
      // No default mandates, user must provide them via Markdown or CodingMandates
    }
    if (sources.stream().noneMatch(s -> s instanceof EnvironmentSource)) {
      sources.add(new EnvironmentSource(vertx));
    }
    if (sources.stream().noneMatch(s -> s instanceof SkillContextSource)) {
      sources.add(new SkillContextSource(skillRuntime));
    }
    if (this.taskFactory != null
        && sources.stream().noneMatch(s -> s instanceof ToolContextSource)) {
      sources.add(new ToolContextSource(this.taskFactory));
    }
    if (sources.stream().noneMatch(s -> s instanceof MemoryContextSource)) {
      sources.add(new MemoryContextSource(memoryService));
    }
    if (sources.stream().noneMatch(s -> s instanceof SubAgentContextSource)) {
      sources.add(new SubAgentContextSource());
    }
  }

  public void setTaskFactory(AgentTaskFactory taskFactory) {
    this.taskFactory = taskFactory;
    ensureCoreSources();
  }

  public void addContextSource(ContextSource source) {
    this.sources.add(source);
  }

  @Override
  public Future<String> buildSystemPrompt(SessionContext context) {
    List<Future<List<ContextFragment>>> futures =
        sources.stream().map(s -> s.getFragments(context)).toList();

    return Future.join(futures)
        .map(v -> futures.stream().map(Future::result).flatMap(List::stream).toList())
        .map(allFragments -> composer.compose(allFragments, 2000));
  }

  @Override
  public Future<LLMRequest> prepareRequest(SessionContext context, int iteration) {
    return buildSystemPrompt(context)
        .map(
            systemPromptContent -> {
              List<Message> modelHistory = new ArrayList<>();
              // 1. Add System Message
              modelHistory.add(Message.system(systemPromptContent));

              // 2. Prune and add session history
              List<Message> prunedHistory =
                  context.getPrunedHistory(2000, tokenCounter); // Keep last 2000 tokens of history
              modelHistory.addAll(sanitizeHistory(prunedHistory));

              // 3. Resolve Model Options
              ModelOptions currentOptions = context.modelOptions();
              if (currentOptions == null && modelConfigProvider != null) {
                currentOptions =
                    new ModelOptions(
                        modelConfigProvider.getTemperature(),
                        modelConfigProvider.getMaxTokens(),
                        modelConfigProvider.getModel(),
                        modelConfigProvider.isStream());
              }
              if (currentOptions == null) {
                // Last ditch fallback if provider is missing
                currentOptions = new ModelOptions(0.0, 4096, "gpt-4o", true);
              }

              // 4. Resolve Tools
              List<ToolDefinition> tools =
                  taskFactory != null
                      ? taskFactory.getAvailableDefinitions(context)
                      : Collections.emptyList();

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
      if (current.role() == Role.ASSISTANT
          && current.toolCalls() != null
          && !current.toolCalls().isEmpty()) {
        boolean hasToolResponse = false;
        if (i + 1 < history.size() && history.get(i + 1).role() == Role.TOOL) {
          hasToolResponse = true;
        }

        if (!hasToolResponse) {
          continue; // Skip this assistant message because it has tool_calls but no tool response
          // follows
        }
      }

      sanitized.add(current);
    }
    return sanitized;
  }
}
