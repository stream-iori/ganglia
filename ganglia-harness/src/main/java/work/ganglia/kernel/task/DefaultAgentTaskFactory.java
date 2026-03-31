package work.ganglia.kernel.task;

import java.util.ArrayList;
import java.util.List;

import work.ganglia.kernel.loop.AgentLoopFactory;
import work.ganglia.kernel.subagent.GraphExecutor;
import work.ganglia.port.chat.SessionContext;
import work.ganglia.port.external.tool.ToolCall;
import work.ganglia.port.external.tool.ToolDefinition;
import work.ganglia.port.external.tool.ToolExecutor;
import work.ganglia.port.internal.skill.SkillRuntime;
import work.ganglia.port.internal.skill.SkillService;
import work.ganglia.port.internal.state.ObservationDispatcher;

/**
 * SRP: Factory for creating AgentTask tasks from tool calls. Uses explicit dependencies and
 * AgentLoopFactory.
 */
public class DefaultAgentTaskFactory implements AgentTaskFactory {

  private static final ToolDefinition CALL_SUB_AGENT_DEF =
      new ToolDefinition(
          "call_sub_agent",
          "Delegate a specific, focused sub-task to a specialized sub-agent. Returns a summary report.",
          """
          {
            "type": "object",
            "properties": {
              "task": { "type": "string", "description": "The task for the sub-agent." },
              "persona": { "type": "string", "enum": ["INVESTIGATOR", "REFACTORER", "GENERAL"], "default": "GENERAL" }
            },
            "required": ["task"]
          }
          """,
          false);

  private static final ToolDefinition PROPOSE_TASK_GRAPH_DEF =
      new ToolDefinition(
          "propose_task_graph",
          "Propose a Directed Acyclic Graph (DAG) of sub-tasks. If 'approved' is false or missing, it will interrupt for user approval. If 'approved' is true, it will execute the graph.",
          """
            {
              "type": "object",
              "properties": {
                "nodes": {
                  "type": "array",
                  "items": {
                    "type": "object",
                    "properties": {
                      "id": { "type": "string", "description": "Unique ID for the task node." },
                      "task": { "type": "string", "description": "Description of the sub-task." },
                      "persona": { "type": "string", "enum": ["INVESTIGATOR", "REFACTORER", "GENERAL"], "default": "GENERAL" },
                      "dependencies": {
                        "type": "array",
                        "items": { "type": "string" },
                        "description": "IDs of tasks that must finish before this one starts."
                      }
                    },
                    "required": ["id", "task"]
                  }
                },
                "approved": { "type": "boolean", "description": "Set to true ONLY after the user has confirmed the plan.", "default": false }
              },
              "required": ["nodes"]
            }
            """,
          true);

  private static final ToolDefinition LIST_SKILLS_DEF =
      new ToolDefinition(
          "list_available_skills", "List all skills available to be activated", "{}");

  private static final ToolDefinition ACTIVATE_SKILL_DEF =
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
          true);

  private final AgentLoopFactory loopFactory;
  private final ToolExecutor standardToolExecutor;
  private final GraphExecutor graphExecutor; // Nullable
  private final SkillService skillService; // Nullable
  private final SkillRuntime skillRuntime; // Nullable
  private final ObservationDispatcher dispatcher; // Nullable

  public DefaultAgentTaskFactory(
      AgentLoopFactory loopFactory,
      ToolExecutor standardToolExecutor,
      GraphExecutor graphExecutor,
      SkillService skillService,
      SkillRuntime skillRuntime) {
    this(loopFactory, standardToolExecutor, graphExecutor, skillService, skillRuntime, null);
  }

  public DefaultAgentTaskFactory(
      AgentLoopFactory loopFactory,
      ToolExecutor standardToolExecutor,
      GraphExecutor graphExecutor,
      SkillService skillService,
      SkillRuntime skillRuntime,
      ObservationDispatcher dispatcher) {
    this.loopFactory = loopFactory;
    this.standardToolExecutor = standardToolExecutor;
    this.graphExecutor = graphExecutor;
    this.skillService = skillService;
    this.skillRuntime = skillRuntime;
    this.dispatcher = dispatcher;
  }

  @Override
  public AgentTask create(ToolCall call, SessionContext context) {
    String toolName = call.toolName();

    if ("call_sub_agent".equals(toolName)) {
      return new SubAgentTask(call, loopFactory);
    } else if ("propose_task_graph".equals(toolName) && graphExecutor != null) {
      return new TaskGraphTask(call, graphExecutor);
    } else if (isSkillTool(toolName, context)) {
      return new SkillTask(call, skillService, skillRuntime, dispatcher);
    } else {
      return new StandardToolTask(call, standardToolExecutor);
    }
  }

  @Override
  public List<ToolDefinition> getAvailableDefinitions(SessionContext context) {
    List<ToolDefinition> definitions = new ArrayList<>();
    definitions.addAll(standardToolExecutor.getAvailableTools(context));

    definitions.add(CALL_SUB_AGENT_DEF);

    if (graphExecutor != null) {
      definitions.add(PROPOSE_TASK_GRAPH_DEF);
    }

    if (skillRuntime != null && skillService != null) {
      definitions.add(LIST_SKILLS_DEF);
      definitions.add(ACTIVATE_SKILL_DEF);

      skillRuntime
          .getActiveSkillsTools(context)
          .forEach(ts -> definitions.addAll(ts.getDefinitions()));
    }

    return definitions;
  }

  private boolean isSkillTool(String toolName, SessionContext context) {
    if (skillRuntime == null || skillService == null) {
      return false;
    }
    if ("list_available_skills".equals(toolName) || "activate_skill".equals(toolName)) {
      return true;
    }
    return skillRuntime.getActiveSkillsTools(context).stream()
        .anyMatch(ts -> ts.getDefinitions().stream().anyMatch(d -> d.name().equals(toolName)));
  }
}
