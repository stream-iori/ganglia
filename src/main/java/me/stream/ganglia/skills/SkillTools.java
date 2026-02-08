package me.stream.ganglia.skills;

import io.vertx.core.Future;
import me.stream.ganglia.core.model.SessionContext;
import me.stream.ganglia.tools.ToolSet;
import me.stream.ganglia.tools.model.ToolDefinition;
import me.stream.ganglia.tools.model.ToolInvokeResult;
import me.stream.ganglia.tools.model.ToolType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class SkillTools implements ToolSet {
    private final SkillRegistry registry;

    public SkillTools(SkillRegistry registry) {
        this.registry = registry;
    }

    @Override
    public List<ToolDefinition> getDefinitions() {
        return List.of(
            new ToolDefinition("list_available_skills", "List all skills available to be activated",
                "{}",
                ToolType.BUILTIN),
            new ToolDefinition("activate_skill", "Activate a specific skill by ID",
                """
                {
                  "type": "object",
                  "properties": {
                    "skillId": { "type": "string", "description": "The ID of the skill to activate" }
                  },
                  "required": ["skillId"]
                }
                """,
                ToolType.BUILTIN)
        );
    }

    @Override
    public Future<ToolInvokeResult> execute(String toolName, Map<String, Object> args, SessionContext context) {
        return switch (toolName) {
            case "list_available_skills" -> listSkills();
            case "activate_skill" -> activateSkill(args, context);
            default -> Future.succeededFuture(ToolInvokeResult.error("Unknown tool: " + toolName));
        };
    }

    private Future<ToolInvokeResult> listSkills() {
        List<SkillManifest> skills = registry.listAvailableSkills();
        if (skills.isEmpty()) {
            return Future.succeededFuture(ToolInvokeResult.success("No skills available."));
        }
        String result = skills.stream()
            .map(s -> "- " + s.id() + ": " + s.name() + " - " + s.description())
            .collect(Collectors.joining("\n"));
        return Future.succeededFuture(ToolInvokeResult.success("Available skills:\n" + result));
    }

    private Future<ToolInvokeResult> activateSkill(Map<String, Object> args, SessionContext context) {
        String skillId = (String) args.get("skillId");
        SkillManifest skill = registry.getSkill(skillId);
        if (skill == null) {
            return Future.succeededFuture(ToolInvokeResult.error("Skill not found: " + skillId));
        }

        List<String> activeSkills = context.activeSkillIds();
        if (activeSkills == null) activeSkills = new ArrayList<>();
        else activeSkills = new ArrayList<>(activeSkills);

        if (activeSkills.contains(skillId)) {
            return Future.succeededFuture(ToolInvokeResult.success("Skill already active: " + skillId));
        }

        activeSkills.add(skillId);

        // We need to create a new context with the new skill ID
        SessionContext nextContext = new SessionContext(
            context.sessionId(),
            context.previousTurns(),
            context.currentTurn(),
            context.metadata(),
            activeSkills,
            context.modelOptions(),
            context.toDoList()
        );

        return Future.succeededFuture(ToolInvokeResult.success("Skill activated: " + skill.name(), nextContext));
    }
}
