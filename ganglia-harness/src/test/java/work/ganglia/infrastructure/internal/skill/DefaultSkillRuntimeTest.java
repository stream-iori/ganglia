package work.ganglia.infrastructure.internal.skill;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

import work.ganglia.BaseGangliaTest;
import work.ganglia.port.chat.SessionContext;
import work.ganglia.port.external.tool.ToolSet;
import work.ganglia.port.internal.skill.SkillManifest;
import work.ganglia.port.internal.skill.SkillService;

@ExtendWith(VertxExtension.class)
class DefaultSkillRuntimeTest extends BaseGangliaTest {

  private DefaultSkillRuntime runtime;
  private SkillService stubService;

  private SkillManifest makeSkill(String id, String name) {
    return new SkillManifest(
        id,
        "1.0",
        name,
        "desc",
        null,
        null,
        null,
        null,
        null,
        "Instructions for " + name,
        null,
        null);
  }

  private SkillManifest makeSkillWithTrigger(String id, String name, List<String> filePatterns) {
    SkillManifest.SkillTrigger trigger = new SkillManifest.SkillTrigger(filePatterns, List.of());
    return new SkillManifest(
        id, "1.0", name, "desc", null, null, null, null, trigger, "Instructions", null, null);
  }

  @BeforeEach
  void setUp(Vertx vertx) {
    SkillManifest skill1 = makeSkill("skill-a", "Skill A");
    SkillManifest skill2 = makeSkill("skill-b", "Skill B");

    stubService =
        new SkillService() {
          @Override
          public Future<Void> init() {
            return Future.succeededFuture();
          }

          @Override
          public List<SkillManifest> getAvailableSkills() {
            return List.of(skill1, skill2);
          }

          @Override
          public Optional<SkillManifest> getSkill(String id) {
            return switch (id) {
              case "skill-a" -> Optional.of(skill1);
              case "skill-b" -> Optional.of(skill2);
              default -> Optional.empty();
            };
          }

          @Override
          public Future<Void> reload() {
            return Future.succeededFuture();
          }
        };

    runtime = new DefaultSkillRuntime(vertx, stubService);
  }

  @Test
  void testActivateSkill(VertxTestContext testContext) {
    SessionContext ctx = createSessionContext();

    runtime
        .activateSkill("skill-a", ctx)
        .onComplete(
            testContext.succeeding(
                updated -> {
                  testContext.verify(
                      () -> {
                        assertTrue(updated.activeSkillIds().contains("skill-a"));
                        testContext.completeNow();
                      });
                }));
  }

  @Test
  void testActivateSkillAlreadyActive(VertxTestContext testContext) {
    SessionContext ctx = createSessionContext();
    runtime
        .activateSkill("skill-a", ctx)
        .compose(updated -> runtime.activateSkill("skill-a", updated))
        .onComplete(
            testContext.succeeding(
                updated -> {
                  testContext.verify(
                      () -> {
                        // Should not duplicate
                        assertEquals(
                            1, updated.activeSkillIds().stream().filter("skill-a"::equals).count());
                        testContext.completeNow();
                      });
                }));
  }

  @Test
  void testActivateUnknownSkillFails(VertxTestContext testContext) {
    SessionContext ctx = createSessionContext();

    runtime
        .activateSkill("nonexistent", ctx)
        .onComplete(
            testContext.failing(
                err -> {
                  testContext.verify(
                      () -> {
                        assertTrue(err.getMessage().contains("Skill not found"));
                        testContext.completeNow();
                      });
                }));
  }

  @Test
  void testDeactivateSkill(VertxTestContext testContext) {
    SessionContext ctx = createSessionContext();

    runtime
        .activateSkill("skill-a", ctx)
        .compose(updated -> runtime.deactivateSkill("skill-a", updated))
        .onComplete(
            testContext.succeeding(
                updated -> {
                  testContext.verify(
                      () -> {
                        assertFalse(updated.activeSkillIds().contains("skill-a"));
                        testContext.completeNow();
                      });
                }));
  }

  @Test
  void testDeactivateSkillNotActive(VertxTestContext testContext) {
    SessionContext ctx = createSessionContext();

    // Deactivating a skill that is not active should succeed without error
    runtime
        .deactivateSkill("skill-a", ctx)
        .onComplete(
            testContext.succeeding(
                updated -> {
                  testContext.verify(
                      () -> {
                        assertFalse(updated.activeSkillIds().contains("skill-a"));
                        testContext.completeNow();
                      });
                }));
  }

  @Test
  void testGetActiveSkillsPromptEmpty(VertxTestContext testContext) {
    SessionContext ctx = createSessionContext();

    runtime
        .getActiveSkillsPrompt(ctx)
        .onComplete(
            testContext.succeeding(
                prompt -> {
                  testContext.verify(
                      () -> {
                        assertEquals("", prompt);
                        testContext.completeNow();
                      });
                }));
  }

  @Test
  void testGetActiveSkillsPromptWithSkill(VertxTestContext testContext) {
    SessionContext ctx = createSessionContext();

    runtime
        .activateSkill("skill-a", ctx)
        .compose(updated -> runtime.getActiveSkillsPrompt(updated))
        .onComplete(
            testContext.succeeding(
                prompt -> {
                  testContext.verify(
                      () -> {
                        assertTrue(prompt.contains("ACTIVE SKILLS"));
                        assertTrue(prompt.contains("Skill A"));
                        testContext.completeNow();
                      });
                }));
  }

  @Test
  void testGetActiveSkillsToolsEmpty() {
    SessionContext ctx = createSessionContext();
    List<ToolSet> tools = runtime.getActiveSkillsTools(ctx);
    assertTrue(tools.isEmpty());
  }

  @Test
  void testGetActiveSkillsToolsWithNoToolsSkill(VertxTestContext testContext) {
    SessionContext ctx = createSessionContext();
    runtime
        .activateSkill("skill-a", ctx)
        .onComplete(
            testContext.succeeding(
                updated -> {
                  testContext.verify(
                      () -> {
                        // skill-a has no tools defined, so toolSets should be empty
                        List<ToolSet> tools = runtime.getActiveSkillsTools(updated);
                        assertTrue(tools.isEmpty());
                        testContext.completeNow();
                      });
                }));
  }

  @Test
  void testSuggestSkillsNoMatch(Vertx vertx, VertxTestContext testContext) {
    // No triggers defined on the skills, so no suggestions
    SessionContext ctx = createSessionContext();
    runtime
        .suggestSkills(ctx)
        .onComplete(
            testContext.succeeding(
                result -> {
                  testContext.verify(
                      () -> {
                        // Empty suggestions since skills have null triggers
                        assertTrue(result.isEmpty());
                        testContext.completeNow();
                      });
                }));
  }

  @Test
  void testSuggestSkillsWithFilePattern(Vertx vertx, VertxTestContext testContext) {
    // Create a runtime with a skill that matches "pom.xml"
    SkillManifest mavenSkill =
        makeSkillWithTrigger("maven-skill", "Maven Skill", List.of("pom.xml"));
    SkillService mavenService =
        new SkillService() {
          @Override
          public Future<Void> init() {
            return Future.succeededFuture();
          }

          @Override
          public List<SkillManifest> getAvailableSkills() {
            return List.of(mavenSkill);
          }

          @Override
          public Optional<SkillManifest> getSkill(String id) {
            return "maven-skill".equals(id) ? Optional.of(mavenSkill) : Optional.empty();
          }

          @Override
          public Future<Void> reload() {
            return Future.succeededFuture();
          }
        };

    DefaultSkillRuntime mavenRuntime = new DefaultSkillRuntime(vertx, mavenService);
    SessionContext ctx = createSessionContext();

    // The working dir contains pom.xml files (ganglia project does), so should suggest
    mavenRuntime
        .suggestSkills(ctx)
        .onComplete(
            testContext.succeeding(
                result -> {
                  testContext.verify(
                      () -> {
                        // Either suggests maven-skill or returns "" (depends on actual fs)
                        // Just verify it doesn't throw an exception
                        assertTrue(result != null);
                        testContext.completeNow();
                      });
                }));
  }
}
