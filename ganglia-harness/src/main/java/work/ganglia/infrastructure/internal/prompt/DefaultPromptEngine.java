package work.ganglia.infrastructure.internal.prompt;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import io.vertx.core.Future;
import io.vertx.core.Vertx;

import work.ganglia.infrastructure.internal.prompt.context.*;
import work.ganglia.kernel.hook.builtin.TokenAwareTruncator;
import work.ganglia.kernel.subagent.SubAgentContextSource;
import work.ganglia.kernel.task.AgentTaskFactory;
import work.ganglia.port.chat.*;
import work.ganglia.port.chat.Message;
import work.ganglia.port.chat.SessionContext;
import work.ganglia.port.external.llm.LLMRequest;
import work.ganglia.port.external.llm.ModelOptions;
import work.ganglia.port.external.tool.*;
import work.ganglia.port.internal.memory.MemoryService;
import work.ganglia.port.internal.prompt.ContextAnalysis;
import work.ganglia.port.internal.prompt.ContextBudget;
import work.ganglia.port.internal.prompt.ContextFragment;
import work.ganglia.port.internal.prompt.ContextManagementConfig;
import work.ganglia.port.internal.prompt.ContextSource;
import work.ganglia.port.internal.prompt.GuidelineContextSource;
import work.ganglia.port.internal.prompt.PromptCacheStats;
import work.ganglia.port.internal.prompt.PromptEngine;
import work.ganglia.port.internal.prompt.WorkflowContextSource;
import work.ganglia.port.internal.skill.SkillRuntime;
import work.ganglia.port.internal.state.*;
import work.ganglia.util.TokenCounter;

/** Standard implementation of PromptEngine using the ContextEngine mechanism. */
public class DefaultPromptEngine implements PromptEngine {

  private final List<ContextSource> sources = new ArrayList<>();
  private final ContextComposer composer;
  private final TokenCounter tokenCounter;
  private final ContextAnalyzer contextAnalyzer;
  private final TokenAwareTruncator toolOutputTruncator;
  private final ToolResultBudgetEnforcer toolResultEnforcer;
  private final Vertx vertx;
  private final MemoryService memoryService;
  private final SkillRuntime skillRuntime;
  private final work.ganglia.config.ModelConfigProvider modelConfigProvider;
  private final ContextManagementConfig config;
  private final ContextBudget budget;
  private final Supplier<AgentTaskFactory> taskFactoryProvider;
  private ObservationDispatcher dispatcher;
  private final Set<String> budgetEmittedSessions = new HashSet<>();
  private volatile boolean toolRegistrationDirty = false;

  public DefaultPromptEngine(
      Vertx vertx,
      MemoryService memoryService,
      SkillRuntime skillRuntime,
      Supplier<AgentTaskFactory> taskFactoryProvider,
      TokenCounter tokenCounter,
      work.ganglia.config.ModelConfigProvider modelConfigProvider) {
    this(
        vertx,
        memoryService,
        skillRuntime,
        taskFactoryProvider,
        tokenCounter,
        List.of(),
        modelConfigProvider);
  }

  public DefaultPromptEngine(
      Vertx vertx,
      MemoryService memoryService,
      SkillRuntime skillRuntime,
      Supplier<AgentTaskFactory> taskFactoryProvider,
      TokenCounter tokenCounter,
      List<ContextSource> extraSources,
      work.ganglia.config.ModelConfigProvider modelConfigProvider) {
    this(
        vertx,
        memoryService,
        skillRuntime,
        taskFactoryProvider,
        tokenCounter,
        extraSources,
        modelConfigProvider,
        null);
  }

  /**
   * Creates a DefaultPromptEngine with unified configuration.
   *
   * @param vertx the Vert.x instance
   * @param memoryService the memory service
   * @param skillRuntime the skill runtime
   * @param taskFactoryProvider the task factory provider
   * @param tokenCounter the token counter
   * @param extraSources additional context sources
   * @param modelConfigProvider the model configuration provider
   * @param config the unified context management configuration (nullable)
   */
  public DefaultPromptEngine(
      Vertx vertx,
      MemoryService memoryService,
      SkillRuntime skillRuntime,
      Supplier<AgentTaskFactory> taskFactoryProvider,
      TokenCounter tokenCounter,
      List<ContextSource> extraSources,
      work.ganglia.config.ModelConfigProvider modelConfigProvider,
      ContextManagementConfig config) {
    this.tokenCounter = tokenCounter;
    this.contextAnalyzer = new ContextAnalyzer(this.tokenCounter);
    this.composer = new ContextComposer(this.tokenCounter);
    this.config =
        config != null
            ? config
            : ContextManagementConfig.fromModel(
                modelConfigProvider.getContextLimit(), modelConfigProvider.getMaxTokens());
    this.budget = this.config.budget();
    this.toolOutputTruncator = new TokenAwareTruncator(tokenCounter, budget.toolOutputPerMessage());
    this.toolResultEnforcer =
        new ToolResultBudgetEnforcer(tokenCounter, budget.toolOutputAggregate());
    this.taskFactoryProvider = taskFactoryProvider;
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
    if (this.taskFactoryProvider != null
        && sources.stream().noneMatch(s -> s instanceof ToolContextSource)) {
      sources.add(new ToolContextSource(this.taskFactoryProvider));
    }
    if (sources.stream().noneMatch(s -> s instanceof MemoryContextSource)) {
      sources.add(new MemoryContextSource(memoryService));
    }
    if (sources.stream().noneMatch(s -> s instanceof SubAgentContextSource)) {
      sources.add(new SubAgentContextSource());
    }
  }

  public void addContextSource(ContextSource source) {
    this.sources.add(source);
  }

  public void setDispatcher(ObservationDispatcher dispatcher) {
    this.dispatcher = dispatcher;
  }

  public ContextBudget getBudget() {
    return budget;
  }

  /** Returns the context management configuration. */
  public ContextManagementConfig getConfig() {
    return config;
  }

  /**
   * Marks that tool registrations have changed, invalidating the prompt cache. Should be called
   * whenever the available tool set is modified (tools added, removed, or updated).
   */
  public void markToolRegistrationChanged() {
    this.toolRegistrationDirty = true;
    composer.clearCache();
  }

  /** Returns the cache statistics from the last compose call. */
  public PromptCacheStats getLastCacheStats() {
    return composer.getLastCacheStats();
  }

  @Override
  public Future<String> buildSystemPrompt(SessionContext context) {
    // Clear cache if tool registrations have changed
    if (toolRegistrationDirty) {
      composer.clearCache();
      toolRegistrationDirty = false;
    }

    List<Future<List<ContextFragment>>> futures =
        sources.stream().map(s -> s.getFragments(context)).toList();

    return Future.join(futures)
        .map(v -> futures.stream().map(Future::result).flatMap(List::stream).toList())
        .map(
            allFragments -> {
              String result = composer.compose(allFragments, budget.systemPrompt());

              // Emit cache stats observation
              PromptCacheStats cacheStats = composer.getLastCacheStats();
              if (dispatcher != null && cacheStats.stablePrefixTokens() > 0) {
                dispatcher.dispatch(
                    context.sessionId(),
                    ObservationType.PROMPT_CACHE_STATS,
                    "prompt_cache_stats",
                    Map.of(
                        "cacheHit", cacheStats.cacheHit(),
                        "stablePrefixTokens", cacheStats.stablePrefixTokens(),
                        "stableFragmentCount", cacheStats.stableFragmentCount(),
                        "volatileFragmentCount", cacheStats.volatileFragmentCount()));
              }

              return result;
            });
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
              // Emit budget event once per session
              if (dispatcher != null && budgetEmittedSessions.add(context.sessionId())) {
                dispatcher.dispatch(
                    context.sessionId(),
                    ObservationType.CONTEXT_BUDGET_ALLOCATED,
                    "context_budget_allocated",
                    Map.of(
                        "contextLimit", budget.contextLimit(),
                        "maxGenerationTokens", budget.maxGenerationTokens(),
                        "systemPromptBudget", budget.systemPrompt(),
                        "historyBudget", budget.history(),
                        "currentTurnBudget", budget.currentTurnBudget(),
                        "toolOutputBudget", budget.toolOutputPerMessage(),
                        "observationFallback", budget.observationFallback(),
                        "compressionTarget", budget.compressionTarget()));
              }

              List<Message> prunedHistory =
                  context.getPrunedHistory(
                      budget.history(), budget.currentTurnBudget(), tokenCounter);
              modelHistory.addAll(capToolMessages(sanitizeHistory(prunedHistory)));

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

              // 4. Resolve Tools
              AgentTaskFactory resolvedFactory =
                  taskFactoryProvider != null ? taskFactoryProvider.get() : null;
              List<ToolDefinition> tools =
                  resolvedFactory != null
                      ? resolvedFactory.getAvailableDefinitions(context)
                      : Collections.emptyList();

              // 5. Emit context analysis observation
              if (dispatcher != null) {
                ContextAnalysis analysis = contextAnalyzer.analyze(context, systemPromptContent);
                dispatcher.dispatch(
                    context.sessionId(),
                    ObservationType.CONTEXT_ANALYSIS,
                    "context_analysis",
                    analysis.toObservationData());
              }

              return new LLMRequest(modelHistory, tools, currentOptions);
            });
  }

  /**
   * Caps oversized TOOL messages to {@code budget.toolOutputPerMessage()} tokens so they don't
   * consume the entire context window. Messages already capped by {@code
   * ObservationCompressionHook} (i.e. {@link Message.ToolObservation#outputCapped()} is true) are
   * skipped to avoid double-truncation. Then enforces aggregate budget for all tool outputs.
   */
  private List<Message> capToolMessages(List<Message> messages) {
    // First apply per-message cap
    List<Message> perMessageCapped =
        messages.stream()
            .map(
                m -> {
                  if (m.role() != Role.TOOL) return m;
                  if (m.content() == null) return m;
                  // Skip messages already processed by ObservationCompressionHook
                  if (m.toolObservation() != null && m.toolObservation().outputCapped()) return m;
                  String toolName =
                      m.toolObservation() != null ? m.toolObservation().toolName() : "tool-output";
                  String capped = toolOutputTruncator.truncate(m.content(), toolName);
                  if (capped.equals(m.content())) {
                    return m; // unchanged — no truncation needed
                  }
                  return Message.toolCapped(
                      m.toolObservation().toolCallId(), m.toolObservation().toolName(), capped);
                })
            .toList();

    // Then enforce aggregate budget
    return toolResultEnforcer.enforce(perMessageCapped);
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
