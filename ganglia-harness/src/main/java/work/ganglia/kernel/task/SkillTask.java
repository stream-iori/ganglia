package work.ganglia.kernel.task;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import io.vertx.core.Future;

import work.ganglia.port.chat.SessionContext;
import work.ganglia.port.external.tool.ObservationType;
import work.ganglia.port.external.tool.ToolCall;
import work.ganglia.port.external.tool.ToolSet;
import work.ganglia.port.internal.skill.SkillManifest;
import work.ganglia.port.internal.skill.SkillRuntime;
import work.ganglia.port.internal.skill.SkillService;
import work.ganglia.port.internal.state.ExecutionContext;
import work.ganglia.port.internal.state.ObservationDispatcher;

public class SkillTask implements AgentTask {
  private final ToolCall call;
  private final SkillService skillService;
  private final SkillRuntime skillRuntime;
  private final ObservationDispatcher dispatcher;

  public SkillTask(ToolCall call, SkillService skillService, SkillRuntime skillRuntime) {
    this(call, skillService, skillRuntime, null);
  }

  public SkillTask(
      ToolCall call,
      SkillService skillService,
      SkillRuntime skillRuntime,
      ObservationDispatcher dispatcher) {
    this.call = call;
    this.skillService = skillService;
    this.skillRuntime = skillRuntime;
    this.dispatcher = dispatcher;
  }

  @Override
  public String id() {
    return call.id();
  }

  @Override
  public String name() {
    return call.toolName();
  }

  @Override
  public ToolCall getToolCall() {
    return call;
  }

  @Override
  public Future<AgentTaskResult> execute(
      SessionContext context, ExecutionContext executionContext) {
    String toolName = call.toolName();
    long startMs = System.currentTimeMillis();
    String sessionId = context.sessionId();

    if (dispatcher != null) {
      Map<String, Object> startData = new HashMap<>();
      startData.put("toolName", toolName);
      startData.put("sessionId", sessionId);
      dispatcher.dispatch(
          sessionId,
          ObservationType.SKILL_STARTED,
          toolName,
          startData,
          "skill-" + java.util.UUID.randomUUID().toString().substring(0, 8),
          executionContext.spanId());
    }

    Future<AgentTaskResult> result;
    if ("list_available_skills".equals(toolName)) {
      result = listSkills();
    } else if ("activate_skill".equals(toolName)) {
      result = activateSkill(call.arguments(), context);
    } else {
      // Try tools from active skills
      List<ToolSet> activeSkillTools = skillRuntime.getActiveSkillsTools(context);
      Future<AgentTaskResult> found = null;
      for (ToolSet ts : activeSkillTools) {
        if (ts.getDefinitions().stream().anyMatch(d -> d.name().equals(toolName))) {
          found =
              ts.execute(call, context, executionContext)
                  .map(
                      invokeResult -> {
                        AgentTaskResult.Status status =
                            switch (invokeResult.status()) {
                              case SUCCESS -> AgentTaskResult.Status.SUCCESS;
                              case ERROR -> AgentTaskResult.Status.ERROR;
                              case EXCEPTION -> AgentTaskResult.Status.EXCEPTION;
                              case INTERRUPT -> AgentTaskResult.Status.INTERRUPT;
                            };
                        return new AgentTaskResult(
                            status,
                            invokeResult.output(),
                            invokeResult.modifiedContext(),
                            invokeResult.metadata());
                      });
          break;
        }
      }
      result =
          found != null
              ? found
              : Future.succeededFuture(AgentTaskResult.error("Unknown skill tool: " + toolName));
    }

    return result.map(
        taskResult -> {
          if (dispatcher != null) {
            Map<String, Object> finishData = new HashMap<>();
            finishData.put("toolName", toolName);
            finishData.put("status", taskResult.status().name());
            finishData.put("durationMs", System.currentTimeMillis() - startMs);
            dispatcher.dispatch(sessionId, ObservationType.SKILL_FINISHED, toolName, finishData);
          }
          return taskResult;
        });
  }

  private Future<AgentTaskResult> listSkills() {
    List<SkillManifest> skills = skillService.getAvailableSkills();
    if (skills.isEmpty()) {
      return Future.succeededFuture(AgentTaskResult.success("No skills available."));
    }
    String result =
        skills.stream()
            .map(s -> "- " + s.id() + ": " + s.name() + " - " + s.description())
            .collect(Collectors.joining("\\n"));
    return Future.succeededFuture(AgentTaskResult.success("Available skills:\\n" + result));
  }

  private Future<AgentTaskResult> activateSkill(Map<String, Object> args, SessionContext context) {
    String skillId = (String) args.get("skillId");
    Object confirmedObj = args.getOrDefault("confirmed", false);
    boolean confirmed = false;
    if (confirmedObj instanceof Boolean) {
      confirmed = (Boolean) confirmedObj;
    } else if (confirmedObj instanceof String) {
      confirmed = Boolean.parseBoolean((String) confirmedObj);
    }

    Optional<SkillManifest> skillOpt = skillService.getSkill(skillId);
    if (skillOpt.isEmpty()) {
      return Future.succeededFuture(AgentTaskResult.error("Skill not found: " + skillId));
    }
    SkillManifest skill = skillOpt.get();

    if (context.activeSkillIds().contains(skillId)) {
      return Future.succeededFuture(AgentTaskResult.success("Skill already active: " + skillId));
    }

    if (!confirmed) {
      String prompt =
          "Requesting activation of skill: "
              + skill.name()
              + " ("
              + skill.id()
              + ")\n"
              + "Description: "
              + skill.description()
              + "\n"
              + "Proceed with activation?";

      Map<String, Object> question = new java.util.HashMap<>();
      question.put("question", prompt);
      question.put("header", "Skill Activation");
      question.put("type", "choice");
      question.put(
          "options",
          java.util.List.of(
              java.util.Map.of(
                  "label", "Activate", "value", "yes", "description", "Enable " + skill.name()),
              java.util.Map.of(
                  "label", "Cancel", "value", "no", "description", "Stay with current skills")));

      Map<String, Object> metadata = new java.util.HashMap<>();
      metadata.put("questions", java.util.List.of(question));

      return Future.succeededFuture(AgentTaskResult.interrupt(prompt, metadata));
    }

    return skillRuntime
        .activateSkill(skillId, context)
        .map(
            nextContext ->
                AgentTaskResult.success(
                    "Skill successfully activated: " + skill.name(), nextContext));
  }
}
