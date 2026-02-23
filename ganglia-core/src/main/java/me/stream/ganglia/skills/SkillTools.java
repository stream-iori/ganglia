package me.stream.ganglia.skills;

import io.vertx.core.Future;
import me.stream.ganglia.core.model.SessionContext;
import me.stream.ganglia.tools.ToolSet;
import me.stream.ganglia.tools.model.ToolCall;
import me.stream.ganglia.tools.model.ToolDefinition;
import me.stream.ganglia.tools.model.ToolInvokeResult;

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
                "{}"),
            new ToolDefinition("activate_skill", "Activate a specific skill by ID to gain its specialized capabilities. This REQUIRES user confirmation unless already confirmed.",
                """
                {
                  "type": "object",
                  "properties": {
                    "skillId": { "type": "string", "description": "The ID of the skill to activate" },
                    "confirmed": { "type": "boolean", "description": "Set to true if the user has already explicitly agreed to activate this skill in the preceding conversation." }
                  },
                  "required": ["skillId"]
                }
                """, true)
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
        Object confirmedObj = args.getOrDefault("confirmed", false);
        boolean confirmed = false;
        if (confirmedObj instanceof Boolean) confirmed = (Boolean) confirmedObj;
        else if (confirmedObj instanceof String) confirmed = Boolean.parseBoolean((String) confirmedObj);
        
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

        if (!confirmed) {
            return Future.succeededFuture(ToolInvokeResult.interrupt(
                "Requesting activation of skill: " + skill.name() + " (" + skill.id() + ")\n" +
                "Description: " + skill.description() + "\n" +
                "Proceed with activation? (yes/no)"
            ));
        }

        activeSkills.add(skillId);

        // Create new context with updated skill IDs
        SessionContext nextContext = new SessionContext(
            context.sessionId(),
            context.previousTurns(),
            context.currentTurn(),
            context.metadata(),
            activeSkills,
            context.modelOptions(),
            context.toDoList()
        );

        return Future.succeededFuture(ToolInvokeResult.success("Skill successfully activated: " + skill.name(), nextContext));
    }
}
