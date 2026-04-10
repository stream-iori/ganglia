package work.ganglia.infrastructure.external.tool;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

import work.ganglia.infrastructure.internal.skill.DefaultSkillService;
import work.ganglia.infrastructure.internal.skill.FileSystemSkillLoader;
import work.ganglia.port.external.tool.ToolDefinition;
import work.ganglia.port.external.tool.model.ToolInvokeResult;
import work.ganglia.port.internal.skill.SkillService;
import work.ganglia.stubs.StubExecutionContext;
import work.ganglia.util.Constants;

@ExtendWith(VertxExtension.class)
class SkillManageToolsTest {

  private SkillManageTools tools;
  private SkillService skillService;
  private Path projectRoot;
  private StubExecutionContext execCtx;

  @BeforeEach
  void setUp(Vertx vertx, @TempDir Path tempDir, VertxTestContext ctx) {
    this.projectRoot = tempDir;
    this.execCtx = new StubExecutionContext();

    Path skillsDir = tempDir.resolve(Constants.DIR_SKILLS);

    FileSystemSkillLoader loader = new FileSystemSkillLoader(vertx, List.of(skillsDir));
    this.skillService = new DefaultSkillService(loader);
    this.tools = new SkillManageTools(vertx, skillService, tempDir.toString());

    // Initialize skill service
    skillService.init().onComplete(ctx.succeeding(v -> ctx.completeNow()));
  }

  @Test
  void getDefinitions_returnsThreeTools() {
    List<ToolDefinition> defs = tools.getDefinitions();
    assertEquals(3, defs.size());

    List<String> names = defs.stream().map(ToolDefinition::name).toList();
    assertTrue(names.contains("create_skill"));
    assertTrue(names.contains("list_skills"));
    assertTrue(names.contains("update_skill"));
  }

  @Test
  void createSkill_createsSkillFile(Vertx vertx, VertxTestContext ctx) {
    Map<String, Object> args = new HashMap<>();
    args.put("name", "deploy-check");
    args.put("description", "Pre-deploy checklist");
    args.put("category", "ops");
    args.put("instructions", "## Steps\n1. Run tests\n2. Check logs");

    tools
        .execute("create_skill", args, null, execCtx)
        .compose(
            result -> {
              assertEquals(ToolInvokeResult.Status.SUCCESS, result.status());
              assertTrue(result.output().contains("deploy-check"));

              // Verify file exists
              String expectedPath =
                  projectRoot
                      .resolve(Constants.DIR_SKILLS)
                      .resolve("deploy-check/SKILL.md")
                      .toString();
              return vertx.fileSystem().readFile(expectedPath);
            })
        .onComplete(
            ctx.succeeding(
                buffer ->
                    ctx.verify(
                        () -> {
                          String content = buffer.toString();
                          assertTrue(content.contains("id: deploy-check"));
                          assertTrue(content.contains("description: Pre-deploy checklist"));
                          assertTrue(content.contains("## Steps"));
                          assertTrue(content.contains("1. Run tests"));
                          ctx.completeNow();
                        })));
  }

  @Test
  void createSkill_withKeywords(Vertx vertx, VertxTestContext ctx) {
    Map<String, Object> args = new HashMap<>();
    args.put("name", "debug-flow");
    args.put("description", "Debug workflow");
    args.put("category", "debug");
    args.put("instructions", "Check logs first");
    args.put("keywords", List.of("debug", "error", "logs"));

    tools
        .execute("create_skill", args, null, execCtx)
        .compose(
            result -> {
              assertEquals(ToolInvokeResult.Status.SUCCESS, result.status());
              String path =
                  projectRoot
                      .resolve(Constants.DIR_SKILLS)
                      .resolve("debug-flow/SKILL.md")
                      .toString();
              return vertx.fileSystem().readFile(path);
            })
        .onComplete(
            ctx.succeeding(
                buffer ->
                    ctx.verify(
                        () -> {
                          String content = buffer.toString();
                          assertTrue(content.contains("keywords:"));
                          assertTrue(content.contains("debug"));
                          ctx.completeNow();
                        })));
  }

  @Test
  void createSkill_defaultCategory(Vertx vertx, VertxTestContext ctx) {
    Map<String, Object> args = new HashMap<>();
    args.put("name", "simple-skill");
    args.put("description", "A simple skill");
    args.put("instructions", "Do something");

    tools
        .execute("create_skill", args, null, execCtx)
        .compose(
            result -> {
              assertEquals(ToolInvokeResult.Status.SUCCESS, result.status());
              String path =
                  projectRoot
                      .resolve(Constants.DIR_SKILLS)
                      .resolve("simple-skill/SKILL.md")
                      .toString();
              return vertx.fileSystem().exists(path);
            })
        .onComplete(
            ctx.succeeding(
                exists ->
                    ctx.verify(
                        () -> {
                          assertTrue(exists, "Should use 'general' as default category");
                          ctx.completeNow();
                        })));
  }

  @Test
  void createSkill_invalidName_returnsError(Vertx vertx, VertxTestContext ctx) {
    Map<String, Object> args = new HashMap<>();
    args.put("name", "Invalid Name!");
    args.put("description", "desc");
    args.put("instructions", "content");

    tools
        .execute("create_skill", args, null, execCtx)
        .onComplete(
            ctx.succeeding(
                result ->
                    ctx.verify(
                        () -> {
                          assertEquals(ToolInvokeResult.Status.ERROR, result.status());
                          assertTrue(result.output().contains("lowercase"));
                          ctx.completeNow();
                        })));
  }

  @Test
  void createSkill_missingRequired_returnsError(Vertx vertx, VertxTestContext ctx) {
    Map<String, Object> args = new HashMap<>();
    args.put("name", "");
    args.put("description", "desc");
    args.put("instructions", "");

    tools
        .execute("create_skill", args, null, execCtx)
        .onComplete(
            ctx.succeeding(
                result ->
                    ctx.verify(
                        () -> {
                          assertEquals(ToolInvokeResult.Status.ERROR, result.status());
                          ctx.completeNow();
                        })));
  }

  @Test
  void listSkills_empty(Vertx vertx, VertxTestContext ctx) {
    tools
        .execute("list_skills", Map.of(), null, execCtx)
        .onComplete(
            ctx.succeeding(
                result ->
                    ctx.verify(
                        () -> {
                          assertEquals(ToolInvokeResult.Status.SUCCESS, result.status());
                          assertTrue(result.output().contains("No skills"));
                          ctx.completeNow();
                        })));
  }

  @Test
  void listSkills_afterCreate(Vertx vertx, VertxTestContext ctx) {
    Map<String, Object> createArgs = new HashMap<>();
    createArgs.put("name", "my-skill");
    createArgs.put("description", "My skill description");
    createArgs.put("instructions", "Do this");

    tools
        .execute("create_skill", createArgs, null, execCtx)
        .compose(v -> tools.execute("list_skills", Map.of(), null, execCtx))
        .onComplete(
            ctx.succeeding(
                result ->
                    ctx.verify(
                        () -> {
                          assertEquals(ToolInvokeResult.Status.SUCCESS, result.status());
                          assertTrue(result.output().contains("my-skill"));
                          assertTrue(result.output().contains("My skill description"));
                          ctx.completeNow();
                        })));
  }

  @Test
  void updateSkill_nonExistent_returnsError(Vertx vertx, VertxTestContext ctx) {
    Map<String, Object> args = new HashMap<>();
    args.put("name", "non-existent");
    args.put("instructions", "new content");

    tools
        .execute("update_skill", args, null, execCtx)
        .onComplete(
            ctx.succeeding(
                result ->
                    ctx.verify(
                        () -> {
                          assertEquals(ToolInvokeResult.Status.ERROR, result.status());
                          assertTrue(result.output().contains("not found"));
                          ctx.completeNow();
                        })));
  }

  @Test
  void updateSkill_existingSkill(Vertx vertx, VertxTestContext ctx) {
    Map<String, Object> createArgs = new HashMap<>();
    createArgs.put("name", "updatable-skill");
    createArgs.put("description", "Original description");
    createArgs.put("instructions", "Original instructions");

    Map<String, Object> updateArgs = new HashMap<>();
    updateArgs.put("name", "updatable-skill");
    updateArgs.put("instructions", "Updated instructions with new steps");

    tools
        .execute("create_skill", createArgs, null, execCtx)
        .compose(v -> tools.execute("update_skill", updateArgs, null, execCtx))
        .compose(
            result -> {
              assertEquals(ToolInvokeResult.Status.SUCCESS, result.status());
              // Read the file to verify update
              String path =
                  projectRoot
                      .resolve(Constants.DIR_SKILLS)
                      .resolve("updatable-skill/SKILL.md")
                      .toString();
              return vertx.fileSystem().readFile(path);
            })
        .onComplete(
            ctx.succeeding(
                buffer ->
                    ctx.verify(
                        () -> {
                          String content = buffer.toString();
                          assertTrue(content.contains("Updated instructions"));
                          assertFalse(content.contains("Original instructions"));
                          // Metadata should be preserved
                          assertTrue(content.contains("id: updatable-skill"));
                          assertTrue(content.contains("description: Original description"));
                          ctx.completeNow();
                        })));
  }

  @Test
  void unknownTool_returnsError(Vertx vertx, VertxTestContext ctx) {
    tools
        .execute("unknown_tool", Map.of(), null, execCtx)
        .onComplete(
            ctx.succeeding(
                result ->
                    ctx.verify(
                        () -> {
                          assertEquals(ToolInvokeResult.Status.ERROR, result.status());
                          ctx.completeNow();
                        })));
  }
}
