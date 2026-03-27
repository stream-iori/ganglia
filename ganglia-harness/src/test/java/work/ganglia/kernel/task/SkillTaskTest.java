package work.ganglia.kernel.task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.core.Future;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

import work.ganglia.BaseGangliaTest;
import work.ganglia.infrastructure.external.tool.model.ToolInvokeResult;
import work.ganglia.port.chat.SessionContext;
import work.ganglia.port.external.tool.ToolCall;
import work.ganglia.port.external.tool.ToolDefinition;
import work.ganglia.port.external.tool.ToolSet;
import work.ganglia.port.internal.skill.SkillManifest;
import work.ganglia.port.internal.skill.SkillRuntime;
import work.ganglia.port.internal.skill.SkillService;
import work.ganglia.stubs.StubExecutionContext;

@ExtendWith(VertxExtension.class)
class SkillTaskTest extends BaseGangliaTest {

  private SkillManifest manifest(String id, String name, String description) {
    return new SkillManifest(
        id,
        "1.0",
        name,
        description,
        null,
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
      public List<ToolSet> getActiveSkillsTools(SessionContext context) {
        return List.of();
      }

      @Override
      public Future<String> suggestSkills(SessionContext context) {
        return Future.succeededFuture("");
      }
    };
  }

  private SkillRuntime skillRuntimeWithTool(String toolName) {
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
      public List<ToolSet> getActiveSkillsTools(SessionContext context) {
        return List.of(
            new ToolSet() {
              @Override
              public List<ToolDefinition> getDefinitions() {
                return List.of(new ToolDefinition(toolName, "Active skill tool", "{}"));
              }

              @Override
              public Future<ToolInvokeResult> execute(
                  String tn,
                  Map<String, Object> args,
                  SessionContext ctx,
                  work.ganglia.port.internal.state.ExecutionContext ec) {
                return Future.succeededFuture(ToolInvokeResult.success("skill tool output"));
              }

              @Override
              public Future<ToolInvokeResult> execute(
                  work.ganglia.port.external.tool.ToolCall call,
                  SessionContext ctx,
                  work.ganglia.port.internal.state.ExecutionContext ec) {
                return Future.succeededFuture(ToolInvokeResult.success("skill tool output"));
              }
            });
      }

      @Override
      public Future<String> suggestSkills(SessionContext context) {
        return Future.succeededFuture("");
      }
    };
  }

  @Test
  void testListSkillsEmpty(VertxTestContext testContext) {
    ToolCall call = new ToolCall("c1", "list_available_skills", Map.of());
    SkillTask task = new SkillTask(call, skillService(List.of()), skillRuntime());

    task.execute(createSessionContext(), new StubExecutionContext())
        .onComplete(
            testContext.succeeding(
                result -> {
                  testContext.verify(
                      () -> {
                        assertEquals(AgentTaskResult.Status.SUCCESS, result.status());
                        assertTrue(result.output().contains("No skills available"));
                        testContext.completeNow();
                      });
                }));
  }

  @Test
  void testListSkillsWithItems(VertxTestContext testContext) {
    List<SkillManifest> skills =
        List.of(
            manifest("coding", "Coding", "Code assistant"),
            manifest("search", "Search", "Web search"));
    ToolCall call = new ToolCall("c2", "list_available_skills", Map.of());
    SkillTask task = new SkillTask(call, skillService(skills), skillRuntime());

    task.execute(createSessionContext(), new StubExecutionContext())
        .onComplete(
            testContext.succeeding(
                result -> {
                  testContext.verify(
                      () -> {
                        assertEquals(AgentTaskResult.Status.SUCCESS, result.status());
                        assertTrue(result.output().contains("coding"));
                        testContext.completeNow();
                      });
                }));
  }

  @Test
  void testActivateSkillNotFound(VertxTestContext testContext) {
    ToolCall call = new ToolCall("c3", "activate_skill", Map.of("skillId", "unknown"));
    SkillTask task = new SkillTask(call, skillService(List.of()), skillRuntime());

    task.execute(createSessionContext(), new StubExecutionContext())
        .onComplete(
            testContext.succeeding(
                result -> {
                  testContext.verify(
                      () -> {
                        assertEquals(AgentTaskResult.Status.ERROR, result.status());
                        assertTrue(result.output().contains("Skill not found"));
                        testContext.completeNow();
                      });
                }));
  }

  @Test
  void testActivateSkillRequiresConfirmation(VertxTestContext testContext) {
    SkillManifest skill = manifest("coding", "Coding Assistant", "Helps with code");
    ToolCall call = new ToolCall("c4", "activate_skill", Map.of("skillId", "coding"));
    SkillTask task = new SkillTask(call, skillService(List.of(skill)), skillRuntime());

    task.execute(createSessionContext(), new StubExecutionContext())
        .onComplete(
            testContext.succeeding(
                result -> {
                  testContext.verify(
                      () -> {
                        assertEquals(AgentTaskResult.Status.INTERRUPT, result.status());
                        assertTrue(result.output().contains("Coding Assistant"));
                        testContext.completeNow();
                      });
                }));
  }

  @Test
  void testActivateSkillWithConfirmation(VertxTestContext testContext) {
    SkillManifest skill = manifest("coding", "Coding", "Code assistant");
    ToolCall call =
        new ToolCall("c5", "activate_skill", Map.of("skillId", "coding", "confirmed", true));
    SkillTask task = new SkillTask(call, skillService(List.of(skill)), skillRuntime());

    task.execute(createSessionContext(), new StubExecutionContext())
        .onComplete(
            testContext.succeeding(
                result -> {
                  testContext.verify(
                      () -> {
                        assertEquals(AgentTaskResult.Status.SUCCESS, result.status());
                        assertTrue(result.output().contains("Coding"));
                        testContext.completeNow();
                      });
                }));
  }

  @Test
  void testActivateSkillAlreadyActive(VertxTestContext testContext) {
    SkillManifest skill = manifest("coding", "Coding", "Code assistant");
    ToolCall call = new ToolCall("c6", "activate_skill", Map.of("skillId", "coding"));

    SessionContext base = createSessionContext();
    SessionContext ctx =
        new SessionContext(
            base.sessionId(),
            base.previousTurns(),
            base.currentTurn(),
            base.metadata(),
            List.of("coding"),
            base.modelOptions());

    SkillTask task = new SkillTask(call, skillService(List.of(skill)), skillRuntime());

    task.execute(ctx, new StubExecutionContext())
        .onComplete(
            testContext.succeeding(
                result -> {
                  testContext.verify(
                      () -> {
                        assertEquals(AgentTaskResult.Status.SUCCESS, result.status());
                        assertTrue(result.output().contains("already active"));
                        testContext.completeNow();
                      });
                }));
  }

  @Test
  void testExecuteActiveSkillTool(VertxTestContext testContext) {
    ToolCall call = new ToolCall("c7", "my_active_tool", Map.of());
    SkillTask task =
        new SkillTask(call, skillService(List.of()), skillRuntimeWithTool("my_active_tool"));

    task.execute(createSessionContext(), new StubExecutionContext())
        .onComplete(
            testContext.succeeding(
                result -> {
                  testContext.verify(
                      () -> {
                        assertEquals(AgentTaskResult.Status.SUCCESS, result.status());
                        assertTrue(result.output().contains("skill tool output"));
                        testContext.completeNow();
                      });
                }));
  }

  @Test
  void testUnknownSkillTool(VertxTestContext testContext) {
    ToolCall call = new ToolCall("c8", "nonexistent_tool", Map.of());
    SkillTask task = new SkillTask(call, skillService(List.of()), skillRuntime());

    task.execute(createSessionContext(), new StubExecutionContext())
        .onComplete(
            testContext.succeeding(
                result -> {
                  testContext.verify(
                      () -> {
                        assertEquals(AgentTaskResult.Status.ERROR, result.status());
                        assertTrue(result.output().contains("Unknown skill tool"));
                        testContext.completeNow();
                      });
                }));
  }

  @Test
  void testIdAndName() {
    ToolCall call = new ToolCall("c9", "list_available_skills", Collections.emptyMap());
    SkillTask task = new SkillTask(call, skillService(List.of()), skillRuntime());
    assertEquals("c9", task.id());
    assertEquals("list_available_skills", task.name());
    assertEquals(call, task.getToolCall());
  }
}
