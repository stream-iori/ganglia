package work.ganglia.infrastructure.external.tool;

import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;

import work.ganglia.port.chat.SessionContext;
import work.ganglia.port.external.tool.ToolDefinition;
import work.ganglia.port.external.tool.ToolSet;
import work.ganglia.port.external.tool.model.ToolInvokeResult;
import work.ganglia.port.internal.skill.SkillManifest;
import work.ganglia.port.internal.skill.SkillService;
import work.ganglia.port.internal.state.ExecutionContext;
import work.ganglia.util.Constants;

/**
 * Tools for the agent to create, list, and update skills. Enables the Nudge→Skill closed loop where
 * the agent can persist reusable operation patterns as skills.
 */
public class SkillManageTools implements ToolSet {
  private final Vertx vertx;
  private final SkillService skillService;
  private final String projectRoot;

  public SkillManageTools(Vertx vertx, SkillService skillService, String projectRoot) {
    this.vertx = vertx;
    this.skillService = skillService;
    this.projectRoot = projectRoot;
  }

  @Override
  public List<ToolDefinition> getDefinitions() {
    return List.of(
        new ToolDefinition(
            "create_skill",
            "Create a reusable skill from an operation pattern. The skill will be saved as a SKILL.md file and available in future sessions.",
            """
                {
                  "type": "object",
                  "properties": {
                    "name": {
                      "type": "string",
                      "description": "Skill identifier (lowercase, hyphens allowed, e.g. 'deploy-check')"
                    },
                    "description": {
                      "type": "string",
                      "description": "Brief description of what the skill does"
                    },
                    "category": {
                      "type": "string",
                      "description": "Category folder (e.g. 'ops', 'coding', 'debug', 'workflow')",
                      "default": "general"
                    },
                    "instructions": {
                      "type": "string",
                      "description": "The skill instructions in Markdown format"
                    },
                    "keywords": {
                      "type": "array",
                      "items": { "type": "string" },
                      "description": "Keywords for auto-activation triggers"
                    }
                  },
                  "required": ["name", "description", "instructions"]
                }
                """),
        new ToolDefinition(
            "list_skills",
            "List all available skills with their IDs and descriptions",
            """
                {
                  "type": "object",
                  "properties": {}
                }
                """),
        new ToolDefinition(
            "update_skill",
            "Update the instructions of an existing skill",
            """
                {
                  "type": "object",
                  "properties": {
                    "name": {
                      "type": "string",
                      "description": "The skill ID to update"
                    },
                    "instructions": {
                      "type": "string",
                      "description": "The new skill instructions in Markdown format"
                    }
                  },
                  "required": ["name", "instructions"]
                }
                """));
  }

  @Override
  public Future<ToolInvokeResult> execute(
      String toolName, Map<String, Object> args, SessionContext context, ExecutionContext ec) {
    return switch (toolName) {
      case "create_skill" -> createSkill(args);
      case "list_skills" -> listSkills();
      case "update_skill" -> updateSkill(args);
      default -> Future.succeededFuture(ToolInvokeResult.error("Unknown tool: " + toolName));
    };
  }

  private Future<ToolInvokeResult> createSkill(Map<String, Object> args) {
    String name = (String) args.get("name");
    String description = (String) args.get("description");
    String category = (String) args.getOrDefault("category", "general");
    String instructions = (String) args.get("instructions");
    @SuppressWarnings("unchecked")
    List<String> keywords =
        args.containsKey("keywords") ? (List<String>) args.get("keywords") : List.of();

    if (name == null || name.isBlank() || instructions == null || instructions.isBlank()) {
      return Future.succeededFuture(
          ToolInvokeResult.error("'name' and 'instructions' are required"));
    }

    // Validate name format
    if (!name.matches("[a-z0-9][a-z0-9-]*")) {
      return Future.succeededFuture(
          ToolInvokeResult.error(
              "Skill name must be lowercase alphanumeric with hyphens (e.g. 'deploy-check')"));
    }

    String skillDir = Paths.get(projectRoot, Constants.DIR_SKILLS, name).toString();
    String skillFilePath = skillDir + "/" + Constants.FILE_SKILL_MD;

    String keywordsYaml =
        keywords.isEmpty()
            ? ""
            : "\nkeywords: [" + keywords.stream().collect(Collectors.joining(", ")) + "]";

    String content =
        "---\n"
            + "id: "
            + name
            + "\n"
            + "name: "
            + name
            + "\n"
            + "description: "
            + description
            + "\n"
            + "category: "
            + category
            + "\n"
            + "version: 1.0.0\n"
            + "author: agent"
            + keywordsYaml
            + "\n---\n\n"
            + instructions
            + "\n";

    return vertx
        .fileSystem()
        .mkdirs(skillDir)
        .compose(v -> vertx.fileSystem().writeFile(skillFilePath, Buffer.buffer(content)))
        .compose(v -> skillService.reload())
        .map(v -> ToolInvokeResult.success("Skill '" + name + "' created at " + skillFilePath))
        .recover(
            err ->
                Future.succeededFuture(
                    ToolInvokeResult.error("Failed to create skill: " + err.getMessage())));
  }

  private Future<ToolInvokeResult> listSkills() {
    List<SkillManifest> skills = skillService.getAvailableSkills();
    if (skills.isEmpty()) {
      return Future.succeededFuture(ToolInvokeResult.success("No skills available."));
    }

    String listing =
        skills.stream()
            .map(
                s ->
                    "- **"
                        + s.id()
                        + "**: "
                        + (s.description() != null ? s.description() : "(no description)"))
            .collect(Collectors.joining("\n"));

    return Future.succeededFuture(
        ToolInvokeResult.success("Available skills (" + skills.size() + "):\n" + listing));
  }

  private Future<ToolInvokeResult> updateSkill(Map<String, Object> args) {
    String name = (String) args.get("name");
    String instructions = (String) args.get("instructions");

    if (name == null || instructions == null) {
      return Future.succeededFuture(
          ToolInvokeResult.error("'name' and 'instructions' are required"));
    }

    return skillService
        .getSkill(name)
        .map(
            manifest -> {
              String skillDir = manifest.skillDir();
              if (skillDir == null) {
                return Future.succeededFuture(
                    ToolInvokeResult.error(
                        "Skill '" + name + "' has no writable directory (may be from a JAR)"));
              }
              String skillFilePath = skillDir + "/" + Constants.FILE_SKILL_MD;

              // Rebuild SKILL.md preserving metadata
              String content =
                  "---\n"
                      + "id: "
                      + manifest.id()
                      + "\n"
                      + "name: "
                      + manifest.name()
                      + "\n"
                      + "description: "
                      + manifest.description()
                      + "\n"
                      + "version: "
                      + manifest.version()
                      + "\n"
                      + "author: "
                      + manifest.author()
                      + "\n---\n\n"
                      + instructions
                      + "\n";

              return vertx
                  .fileSystem()
                  .writeFile(skillFilePath, Buffer.buffer(content))
                  .compose(v -> skillService.reload())
                  .map(
                      v ->
                          ToolInvokeResult.success(
                              "Skill '" + name + "' updated at " + skillFilePath));
            })
        .orElseGet(
            () ->
                Future.succeededFuture(
                    ToolInvokeResult.error(
                        "Skill '"
                            + name
                            + "' not found. Use 'create_skill' to create a new skill.")));
  }
}
