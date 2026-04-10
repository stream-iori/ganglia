package work.ganglia.infrastructure.internal.skill;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.core.Future;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

import work.ganglia.BaseGangliaTest;
import work.ganglia.port.chat.SessionContext;
import work.ganglia.port.external.tool.model.ToolInvokeResult;
import work.ganglia.port.internal.skill.SkillManifest;
import work.ganglia.port.internal.skill.SkillRuntime;
import work.ganglia.port.internal.skill.SkillService;
import work.ganglia.stubs.StubExecutionContext;

@ExtendWith(VertxExtension.class)
class SkillToolsTest extends BaseGangliaTest {

  private SkillManifest manifest(String id, String name, String description) {
    return new SkillManifest(
        id,
        "1.0",
        name,
        description,
        "author",
        List.of(),
        List.of(),
        List.of(),
        null,
        null,
        null,
        null);
  }

  private SkillService skillService(List<SkillManifest> skills) {
    return new SkillService() {
      @Override
      public Future<Void> init() {
        return Future.succeededFuture();
      }

      @Override
      public List<SkillManifest> getAvailableSkills() {
        return skills;
      }

      @Override
      public Optional<SkillManifest> getSkill(String id) {
        return skills.stream().filter(s -> s.id().equals(id)).findFirst();
      }

      @Override
      public Future<Void> reload() {
        return Future.succeededFuture();
      }
    };
  }

  private SkillRuntime skillRuntime() {
    return new SkillRuntime() {
      @Override
      public Future<SessionContext> activateSkill(String skillId, SessionContext context) {
        return Future.succeededFuture(context);
      }

      @Override
      public Future<SessionContext> deactivateSkill(String skillId, SessionContext context) {
        return Future.succeededFuture(context);
      }

      @Override
      public Future<String> getActiveSkillsPrompt(SessionContext context) {
        return Future.succeededFuture("");
      }

      @Override
      public List<work.ganglia.port.external.tool.ToolSet> getActiveSkillsTools(
          SessionContext context) {
        return List.of();
      }

      @Override
      public Future<String> suggestSkills(SessionContext context) {
        return Future.succeededFuture("");
      }
    };
  }

  @Test
  void testGetDefinitionsReturnsTwoTools() {
    SkillTools tools = new SkillTools(skillService(List.of()), skillRuntime());
    List<work.ganglia.port.external.tool.ToolDefinition> defs = tools.getDefinitions();
    assertEquals(2, defs.size());
    assertTrue(defs.stream().anyMatch(d -> d.name().equals("list_available_skills")));
    assertTrue(defs.stream().anyMatch(d -> d.name().equals("activate_skill")));
  }

  @Test
  void testListSkillsEmpty(VertxTestContext testContext) {
    SkillTools tools = new SkillTools(skillService(List.of()), skillRuntime());

    tools
        .execute(
            "list_available_skills", Map.of(), createSessionContext(), new StubExecutionContext())
        .onComplete(
            testContext.succeeding(
                result -> {
                  testContext.verify(
                      () -> {
                        assertTrue(result.output().contains("No skills available"));
                        testContext.completeNow();
                      });
                }));
  }

  @Test
  void testListSkillsWithSkills(VertxTestContext testContext) {
    List<SkillManifest> skills =
        List.of(
            manifest("coding", "Coding", "Code assistant"),
            manifest("search", "Search", "Web search"));
    SkillTools tools = new SkillTools(skillService(skills), skillRuntime());

    tools
        .execute(
            "list_available_skills", Map.of(), createSessionContext(), new StubExecutionContext())
        .onComplete(
            testContext.succeeding(
                result -> {
                  testContext.verify(
                      () -> {
                        assertTrue(result.output().contains("coding"));
                        assertTrue(result.output().contains("search"));
                        testContext.completeNow();
                      });
                }));
  }

  @Test
  void testActivateSkillNotFound(VertxTestContext testContext) {
    SkillTools tools = new SkillTools(skillService(List.of()), skillRuntime());

    tools
        .execute(
            "activate_skill",
            Map.of("skillId", "nonexistent"),
            createSessionContext(),
            new StubExecutionContext())
        .onComplete(
            testContext.succeeding(
                result -> {
                  testContext.verify(
                      () -> {
                        assertEquals(ToolInvokeResult.Status.ERROR, result.status());
                        assertTrue(result.output().contains("Skill not found"));
                        testContext.completeNow();
                      });
                }));
  }

  @Test
  void testActivateSkillRequiresConfirmation(VertxTestContext testContext) {
    SkillManifest skill = manifest("coding", "Coding Assistant", "Helps with code");
    SkillTools tools = new SkillTools(skillService(List.of(skill)), skillRuntime());

    tools
        .execute(
            "activate_skill",
            Map.of("skillId", "coding"),
            createSessionContext(),
            new StubExecutionContext())
        .onComplete(
            testContext.succeeding(
                result -> {
                  testContext.verify(
                      () -> {
                        assertEquals(ToolInvokeResult.Status.INTERRUPT, result.status());
                        assertTrue(result.output().contains("Coding Assistant"));
                        testContext.completeNow();
                      });
                }));
  }

  @Test
  void testActivateSkillWithConfirmation(VertxTestContext testContext) {
    SkillManifest skill = manifest("coding", "Coding Assistant", "Helps with code");
    SkillTools tools = new SkillTools(skillService(List.of(skill)), skillRuntime());

    tools
        .execute(
            "activate_skill",
            Map.of("skillId", "coding", "confirmed", true),
            createSessionContext(),
            new StubExecutionContext())
        .onComplete(
            testContext.succeeding(
                result -> {
                  testContext.verify(
                      () -> {
                        assertEquals(ToolInvokeResult.Status.SUCCESS, result.status());
                        assertTrue(result.output().contains("Coding Assistant"));
                        testContext.completeNow();
                      });
                }));
  }

  @Test
  void testActivateSkillAlreadyActive(VertxTestContext testContext) {
    SkillManifest skill = manifest("coding", "Coding", "Code assistant");
    SkillTools tools = new SkillTools(skillService(List.of(skill)), skillRuntime());

    SessionContext base = createSessionContext();
    SessionContext ctx =
        new SessionContext(
            base.sessionId(),
            base.previousTurns(),
            base.currentTurn(),
            base.metadata(),
            List.of("coding"),
            base.modelOptions(),
            base.compressionState());

    tools
        .execute("activate_skill", Map.of("skillId", "coding"), ctx, new StubExecutionContext())
        .onComplete(
            testContext.succeeding(
                result -> {
                  testContext.verify(
                      () -> {
                        assertEquals(ToolInvokeResult.Status.SUCCESS, result.status());
                        assertTrue(result.output().contains("already active"));
                        testContext.completeNow();
                      });
                }));
  }

  @Test
  void testActivateSkillConfirmedAsString(VertxTestContext testContext) {
    SkillManifest skill = manifest("search", "Search", "Web search");
    SkillTools tools = new SkillTools(skillService(List.of(skill)), skillRuntime());

    tools
        .execute(
            "activate_skill",
            Map.of("skillId", "search", "confirmed", "true"),
            createSessionContext(),
            new StubExecutionContext())
        .onComplete(
            testContext.succeeding(
                result -> {
                  testContext.verify(
                      () -> {
                        assertEquals(ToolInvokeResult.Status.SUCCESS, result.status());
                        testContext.completeNow();
                      });
                }));
  }

  @Test
  void testUnknownTool(VertxTestContext testContext) {
    SkillTools tools = new SkillTools(skillService(List.of()), skillRuntime());

    tools
        .execute("unknown_skill_tool", Map.of(), createSessionContext(), new StubExecutionContext())
        .onComplete(
            testContext.succeeding(
                result -> {
                  testContext.verify(
                      () -> {
                        assertEquals(ToolInvokeResult.Status.ERROR, result.status());
                        testContext.completeNow();
                      });
                }));
  }
}
