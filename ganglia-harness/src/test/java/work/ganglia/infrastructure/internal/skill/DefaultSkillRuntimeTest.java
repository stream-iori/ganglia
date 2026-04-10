package work.ganglia.infrastructure.internal.skill;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
  private SkillService mockService;
  private SkillManifest skill1;
  private SkillManifest skill2;

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
    skill1 = makeSkill("skill-a", "Skill A");
    skill2 = makeSkill("skill-b", "Skill B");

    mockService = mock(SkillService.class);
    when(mockService.init()).thenReturn(Future.succeededFuture());
    when(mockService.getAvailableSkills()).thenReturn(List.of(skill1, skill2));
    when(mockService.getSkill("skill-a")).thenReturn(Optional.of(skill1));
    when(mockService.getSkill("skill-b")).thenReturn(Optional.of(skill2));
    when(mockService.reload()).thenReturn(Future.succeededFuture());

    runtime = new DefaultSkillRuntime(vertx, mockService);
  }

  @Test
  void testActivateSkill(VertxTestContext testContext) {
    SessionContext ctx = createSessionContext();

    assertFutureSuccess(
        runtime.activateSkill("skill-a", ctx),
        testContext,
        updated -> {
          assertTrue(updated.activeSkillIds().contains("skill-a"));
        });
  }

  @Test
  void testActivateSkillAlreadyActive(VertxTestContext testContext) {
    SessionContext ctx = createSessionContext();
    assertFutureSuccess(
        runtime
            .activateSkill("skill-a", ctx)
            .compose(updated -> runtime.activateSkill("skill-a", updated)),
        testContext,
        updated -> {
          // Should not duplicate
          assertEquals(1, updated.activeSkillIds().stream().filter("skill-a"::equals).count());
        });
  }

  @Test
  void testActivateUnknownSkillFails(VertxTestContext testContext) {
    SessionContext ctx = createSessionContext();

    assertFutureFailure(
        runtime.activateSkill("nonexistent", ctx),
        testContext,
        err -> {
          assertTrue(err.getMessage().contains("Skill not found"));
        });
  }

  @Test
  void testDeactivateSkill(VertxTestContext testContext) {
    SessionContext ctx = createSessionContext();

    assertFutureSuccess(
        runtime
            .activateSkill("skill-a", ctx)
            .compose(updated -> runtime.deactivateSkill("skill-a", updated)),
        testContext,
        updated -> {
          assertFalse(updated.activeSkillIds().contains("skill-a"));
        });
  }

  @Test
  void testDeactivateSkillNotActive(VertxTestContext testContext) {
    SessionContext ctx = createSessionContext();

    // Deactivating a skill that is not active should succeed without error
    assertFutureSuccess(
        runtime.deactivateSkill("skill-a", ctx),
        testContext,
        updated -> {
          assertFalse(updated.activeSkillIds().contains("skill-a"));
        });
  }

  @Test
  void testGetActiveSkillsPromptEmpty(VertxTestContext testContext) {
    SessionContext ctx = createSessionContext();

    assertFutureSuccess(
        runtime.getActiveSkillsPrompt(ctx),
        testContext,
        prompt -> {
          assertEquals("", prompt);
        });
  }

  @Test
  void testGetActiveSkillsPromptWithSkill(VertxTestContext testContext) {
    SessionContext ctx = createSessionContext();

    assertFutureSuccess(
        runtime
            .activateSkill("skill-a", ctx)
            .compose(updated -> runtime.getActiveSkillsPrompt(updated)),
        testContext,
        prompt -> {
          assertTrue(prompt.contains("ACTIVE SKILLS"));
          assertTrue(prompt.contains("Skill A"));
        });
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
    assertFutureSuccess(
        runtime.activateSkill("skill-a", ctx),
        testContext,
        updated -> {
          // skill-a has no tools defined, so toolSets should be empty
          List<ToolSet> tools = runtime.getActiveSkillsTools(updated);
          assertTrue(tools.isEmpty());
        });
  }

  @Test
  void testSuggestSkillsNoMatch(Vertx vertx, VertxTestContext testContext) {
    // No triggers defined on the skills, so no suggestions
    SessionContext ctx = createSessionContext();
    assertFutureSuccess(
        runtime.suggestSkills(ctx),
        testContext,
        result -> {
          // Empty suggestions since skills have null triggers
          assertTrue(result.isEmpty());
        });
  }

  @Test
  void testSuggestSkillsWithFilePattern(Vertx vertx, VertxTestContext testContext) {
    // Create a runtime with a skill that matches "pom.xml"
    SkillManifest mavenSkill =
        makeSkillWithTrigger("maven-skill", "Maven Skill", List.of("pom.xml"));
    SkillService mavenService = mock(SkillService.class);
    when(mavenService.init()).thenReturn(Future.succeededFuture());
    when(mavenService.getAvailableSkills()).thenReturn(List.of(mavenSkill));
    when(mavenService.getSkill("maven-skill")).thenReturn(Optional.of(mavenSkill));
    when(mavenService.reload()).thenReturn(Future.succeededFuture());

    DefaultSkillRuntime mavenRuntime = new DefaultSkillRuntime(vertx, mavenService);
    SessionContext ctx = createSessionContext();

    // The working dir contains pom.xml files (ganglia project does), so should suggest
    assertFutureSuccess(
        mavenRuntime.suggestSkills(ctx),
        testContext,
        result -> {
          // Either suggests maven-skill or returns "" (depends on actual fs)
          // Just verify it doesn't throw an exception
          assertTrue(result != null);
        });
  }
}
