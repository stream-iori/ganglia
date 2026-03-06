package work.ganglia.kernel.task;

import io.vertx.core.Vertx;
import work.ganglia.config.ConfigManager;
import work.ganglia.port.external.llm.ModelGateway;
import work.ganglia.port.chat.SessionContext;
import work.ganglia.port.internal.prompt.PromptEngine;
import work.ganglia.kernel.task.SkillTask;
import work.ganglia.kernel.task.StandardToolTask;
import work.ganglia.kernel.task.SubAgentTask;
import work.ganglia.kernel.task.TaskGraphTask;
import work.ganglia.port.internal.state.SessionManager;
import work.ganglia.port.internal.memory.ContextCompressor;
import work.ganglia.infrastructure.internal.memory.DefaultContextCompressor;
import work.ganglia.port.internal.skill.SkillRuntime;
import work.ganglia.port.internal.skill.SkillService;
import work.ganglia.port.external.tool.ToolExecutor;
import work.ganglia.port.external.tool.ToolCall;
import work.ganglia.port.external.tool.ToolDefinition;
import work.ganglia.kernel.subagent.GraphExecutor;

import java.util.ArrayList;
import java.util.List;

public class DefaultSchedulableFactory implements SchedulableFactory {

    private final Vertx vertx;
    private final ModelGateway modelGateway;
    private final SessionManager sessionManager;
    private final PromptEngine promptEngine;
    private final ConfigManager configManager;
    private final ContextCompressor compressor;
    private final ToolExecutor standardToolExecutor;
    private final GraphExecutor graphExecutor; // Nullable
    private final SkillService skillService;   // Nullable
    private final SkillRuntime skillRuntime;   // Nullable

    public DefaultSchedulableFactory(Vertx vertx, ModelGateway modelGateway, SessionManager sessionManager,
                                      PromptEngine promptEngine, ConfigManager configManager, ContextCompressor compressor,
                                      ToolExecutor standardToolExecutor, GraphExecutor graphExecutor,
                                      SkillService skillService, SkillRuntime skillRuntime) {
        this.vertx = vertx;
        this.modelGateway = modelGateway;
        this.sessionManager = sessionManager;
        this.promptEngine = promptEngine;
        this.configManager = configManager;
        this.compressor = compressor;
        this.standardToolExecutor = standardToolExecutor;
        this.graphExecutor = graphExecutor;
        this.skillService = skillService;
        this.skillRuntime = skillRuntime;
    }

    @Override
    public Schedulable create(ToolCall call, SessionContext context) {
        String toolName = call.toolName();

        if ("call_sub_agent".equals(toolName)) {
            return new SubAgentTask(call, vertx, modelGateway, sessionManager, promptEngine, configManager, compressor, this);
        } else if ("propose_task_graph".equals(toolName) && graphExecutor != null) {
            return new TaskGraphTask(call, graphExecutor);
        } else if (isSkillTool(toolName, context)) {
            return new SkillTask(call, skillService, skillRuntime);
        } else {
            return new StandardToolTask(call, standardToolExecutor);
        }
    }

    @Override
    public List<ToolDefinition> getAvailableDefinitions(SessionContext context) {
        List<ToolDefinition> definitions = new ArrayList<>();

        // 1. Standard tools (Bash, FileSystem, Interact, etc.)
        definitions.addAll(standardToolExecutor.getAvailableTools(context));

        // 2. SubAgent tools
        definitions.add(new ToolDefinition(
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
            false
        ));

        if (graphExecutor != null) {
            definitions.add(new ToolDefinition(
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
                true
            ));
        }

        // 3. Skill tools (List/Activate + active skill tools)
        if (skillRuntime != null && skillService != null) {
            definitions.add(new ToolDefinition("list_available_skills", "List all skills available to be activated", "{}"));
            definitions.add(new ToolDefinition("activate_skill", "Activate a specific skill by ID to gain its specialized capabilities. This REQUIRES user confirmation unless already confirmed.",
                """
                {
                  "type": "object",
                  "properties": {
                    "skillId": { "type": "string", "description": "The ID of the skill to activate" },
                    "confirmed": { "type": "boolean", "description": "Set to true if the user has already explicitly agreed to activate this skill in the preceding conversation." }
                  },
                  "required": ["skillId"]
                }
                """, true));

            skillRuntime.getActiveSkillsTools(context).forEach(ts -> definitions.addAll(ts.getDefinitions()));
        }

        return definitions;
    }

    private boolean isSkillTool(String toolName, SessionContext context) {
        if (skillRuntime == null || skillService == null) return false;

        if ("list_available_skills".equals(toolName) || "activate_skill".equals(toolName)) {
            return true;
        }
        return skillRuntime.getActiveSkillsTools(context).stream()
                .anyMatch(ts -> ts.getDefinitions().stream().anyMatch(d -> d.name().equals(toolName)));
    }
}
