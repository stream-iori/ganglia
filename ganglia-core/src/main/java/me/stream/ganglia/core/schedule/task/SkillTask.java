package me.stream.ganglia.core.schedule.task;

import io.vertx.core.Future;
import me.stream.ganglia.core.model.SessionContext;
import me.stream.ganglia.core.schedule.ScheduleResult;
import me.stream.ganglia.core.schedule.Scheduleable;
import me.stream.ganglia.skills.SkillManifest;
import me.stream.ganglia.skills.SkillRuntime;
import me.stream.ganglia.skills.SkillService;
import me.stream.ganglia.tools.ToolSet;
import me.stream.ganglia.tools.model.ToolCall;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class SkillTask implements Scheduleable {
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
    public Future<ScheduleResult> execute(SessionContext context) {
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
                return ts.execute(call, context).map(invokeResult -> {
                    ScheduleResult.Status status = switch (invokeResult.status()) {
                        case SUCCESS -> ScheduleResult.Status.SUCCESS;
                        case ERROR -> ScheduleResult.Status.ERROR;
                        case EXCEPTION -> ScheduleResult.Status.EXCEPTION;
                        case INTERRUPT -> ScheduleResult.Status.INTERRUPT;
                    };
                    return new ScheduleResult(status, invokeResult.output(), invokeResult.modifiedContext());
                });
            }
        }

        return Future.succeededFuture(ScheduleResult.error("Unknown skill tool: " + toolName));
    }

    private Future<ScheduleResult> listSkills() {
        List<SkillManifest> skills = skillService.getAvailableSkills();
        if (skills.isEmpty()) {
            return Future.succeededFuture(ScheduleResult.success("No skills available."));
        }
        String result = skills.stream()
            .map(s -> "- " + s.id() + ": " + s.name() + " - " + s.description())
            .collect(Collectors.joining("\\n"));
        return Future.succeededFuture(ScheduleResult.success("Available skills:\\n" + result));
    }

    private Future<ScheduleResult> activateSkill(Map<String, Object> args, SessionContext context) {
        String skillId = (String) args.get("skillId");
        Object confirmedObj = args.getOrDefault("confirmed", false);
        boolean confirmed = false;
        if (confirmedObj instanceof Boolean) confirmed = (Boolean) confirmedObj;
        else if (confirmedObj instanceof String) confirmed = Boolean.parseBoolean((String) confirmedObj);
        
        Optional<SkillManifest> skillOpt = skillService.getSkill(skillId);
        if (skillOpt.isEmpty()) {
            return Future.succeededFuture(ScheduleResult.error("Skill not found: " + skillId));
        }
        SkillManifest skill = skillOpt.get();

        if (context.activeSkillIds().contains(skillId)) {
            return Future.succeededFuture(ScheduleResult.success("Skill already active: " + skillId));
        }

        if (!confirmed) {
            return Future.succeededFuture(ScheduleResult.interrupt(
                "Requesting activation of skill: " + skill.name() + " (" + skill.id() + ")\\n" +
                "Description: " + skill.description() + "\\n" +
                "Proceed with activation? (yes/no)"
            ));
        }

        return skillRuntime.activateSkill(skillId, context)
            .map(nextContext -> ScheduleResult.success("Skill successfully activated: " + skill.name(), nextContext));
    }
}
