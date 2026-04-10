package work.ganglia.infrastructure.internal.skill;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import io.vertx.core.Future;

import work.ganglia.port.chat.SessionContext;
import work.ganglia.port.external.tool.ToolDefinition;
import work.ganglia.port.external.tool.ToolSet;
import work.ganglia.port.external.tool.model.ToolInvokeResult;
import work.ganglia.port.internal.skill.SkillManifest;
import work.ganglia.port.internal.skill.SkillRuntime;
import work.ganglia.port.internal.skill.SkillService;

public class SkillTools implements ToolSet {
  private final SkillService skillService;
  private final SkillRuntime skillRuntime;

  public SkillTools(SkillService skillService, SkillRuntime skillRuntime) {
    this.skillService = skillService;
    this.skillRuntime = skillRuntime;
  }

  @Override
  public List<ToolDefinition> getDefinitions() {
    return List.of(
        new ToolDefinition(
            "list_available_skills", "List all skills available to be activated", "{}"),
        new ToolDefinition(
            "activate_skill",
            "Activate a specific skill by ID to gain its specialized capabilities. This REQUIRES user confirmation unless already confirmed.",
            """
                {
                  "type": "object",
                  "properties": {
                    "skillId": { "type": "string", "description": "The ID of the skill to activate" },
                    "confirmed": { "type": "boolean", "description": "Set to true if the user has already explicitly agreed to activate this skill in the preceding conversation." }
                  },
                  "required": ["skillId"]
                }
                """,
            true));
  }

  @Override
  public Future<ToolInvokeResult> execute(
      String toolName,
      Map<String, Object> args,
      SessionContext context,
      work.ganglia.port.internal.state.ExecutionContext executionContext) {
    return switch (toolName) {
      case "list_available_skills" -> listSkills();
      case "activate_skill" -> activateSkill(args, context);
      default -> Future.succeededFuture(ToolInvokeResult.error("Unknown tool: " + toolName));
    };
  }

  private Future<ToolInvokeResult> listSkills() {
    List<SkillManifest> skills = skillService.getAvailableSkills();
    if (skills.isEmpty()) {
      return Future.succeededFuture(ToolInvokeResult.success("No skills available."));
    }
    String result =
        skills.stream()
            .map(s -> "- " + s.id() + ": " + s.name() + " - " + s.description())
            .collect(Collectors.joining("\n"));
    return Future.succeededFuture(ToolInvokeResult.success("Available skills:\n" + result));
  }

  private Future<ToolInvokeResult> activateSkill(Map<String, Object> args, SessionContext context) {
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
      return Future.succeededFuture(ToolInvokeResult.error("Skill not found: " + skillId));
    }
    SkillManifest skill = skillOpt.get();

    if (context.activeSkillIds().contains(skillId)) {
      return Future.succeededFuture(ToolInvokeResult.success("Skill already active: " + skillId));
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

      return Future.succeededFuture(ToolInvokeResult.interrupt(prompt, metadata));
    }

    return skillRuntime
        .activateSkill(skillId, context)
        .map(
            nextContext ->
                ToolInvokeResult.success(
                    "Skill successfully activated: " + skill.name(), nextContext));
  }
}
