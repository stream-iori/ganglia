package work.ganglia.kernel.task;

import io.vertx.core.Future;
import work.ganglia.port.chat.SessionContext;
import work.ganglia.port.internal.skill.SkillManifest;
import work.ganglia.port.internal.skill.SkillRuntime;
import work.ganglia.port.internal.skill.SkillService;
import work.ganglia.port.external.tool.ToolSet;
import work.ganglia.port.external.tool.ToolCall;
import work.ganglia.port.internal.state.ExecutionContext;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class SkillTask implements AgentTask {
    private final ToolCall call;
    private final SkillService skillService;
    private final SkillRuntime skillRuntime;

    public SkillTask(ToolCall call, SkillService skillService, SkillRuntime skillRuntime) {
        this.call = call;
        this.skillService = skillService;
        this.skillRuntime = skillRuntime;
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
    public Future<AgentTaskResult> execute(SessionContext context, ExecutionContext executionContext) {
        String toolName = call.toolName();

        if ("list_available_skills".equals(toolName)) {
            return listSkills();
        } else if ("activate_skill".equals(toolName)) {
            return activateSkill(call.arguments(), context);
        }

        // Try tools from active skills
        List<ToolSet> activeSkillTools = skillRuntime.getActiveSkillsTools(context);
        for (ToolSet ts : activeSkillTools) {
            if (ts.getDefinitions().stream().anyMatch(d -> d.name().equals(toolName))) {
                return ts.execute(call, context, executionContext).map(invokeResult -> {
                    AgentTaskResult.Status status = switch (invokeResult.status()) {
                        case SUCCESS -> AgentTaskResult.Status.SUCCESS;
                        case ERROR -> AgentTaskResult.Status.ERROR;
                        case EXCEPTION -> AgentTaskResult.Status.EXCEPTION;
                        case INTERRUPT -> AgentTaskResult.Status.INTERRUPT;
                    };
                    return new AgentTaskResult(status, invokeResult.output(), invokeResult.modifiedContext());
                });
            }
        }

        return Future.succeededFuture(AgentTaskResult.error("Unknown skill tool: " + toolName));
    }

    private Future<AgentTaskResult> listSkills() {
        List<SkillManifest> skills = skillService.getAvailableSkills();
        if (skills.isEmpty()) {
            return Future.succeededFuture(AgentTaskResult.success("No skills available."));
        }
        String result = skills.stream()
            .map(s -> "- " + s.id() + ": " + s.name() + " - " + s.description())
            .collect(Collectors.joining("\\n"));
        return Future.succeededFuture(AgentTaskResult.success("Available skills:\\n" + result));
    }

    private Future<AgentTaskResult> activateSkill(Map<String, Object> args, SessionContext context) {
        String skillId = (String) args.get("skillId");
        Object confirmedObj = args.getOrDefault("confirmed", false);
        boolean confirmed = false;
        if (confirmedObj instanceof Boolean) confirmed = (Boolean) confirmedObj;
        else if (confirmedObj instanceof String) confirmed = Boolean.parseBoolean((String) confirmedObj);

        Optional<SkillManifest> skillOpt = skillService.getSkill(skillId);
        if (skillOpt.isEmpty()) {
            return Future.succeededFuture(AgentTaskResult.error("Skill not found: " + skillId));
        }
        SkillManifest skill = skillOpt.get();

        if (context.activeSkillIds().contains(skillId)) {
            return Future.succeededFuture(AgentTaskResult.success("Skill already active: " + skillId));
        }

        if (!confirmed) {
            return Future.succeededFuture(AgentTaskResult.interrupt(
                "Requesting activation of skill: " + skill.name() + " (" + skill.id() + ")\\n" +
                "Description: " + skill.description() + "\\n" +
                "Proceed with activation? (yes/no)"
            ));
        }

        return skillRuntime.activateSkill(skillId, context)
            .map(nextContext -> AgentTaskResult.success("Skill successfully activated: " + skill.name(), nextContext));
    }
}
