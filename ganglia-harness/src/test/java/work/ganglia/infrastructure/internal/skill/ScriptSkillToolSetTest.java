package work.ganglia.infrastructure.internal.skill;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

import work.ganglia.BaseGangliaTest;
import work.ganglia.port.external.tool.model.ToolInvokeResult;
import work.ganglia.port.internal.skill.SkillToolDefinition;
import work.ganglia.stubs.StubExecutionContext;

@ExtendWith(VertxExtension.class)
class ScriptSkillToolSetTest extends BaseGangliaTest {

  private SkillToolDefinition echoTool(String toolName, String command) {
    return new SkillToolDefinition(
        toolName,
        "An echo tool",
        "SCRIPT",
        new SkillToolDefinition.ScriptInfo(command),
        null,
        "{}");
  }

  @Test
  void testGetDefinitions() {
    SkillToolDefinition tool = echoTool("my_tool", "echo hello");
    ScriptSkillToolSet toolSet = new ScriptSkillToolSet(vertx, "my-skill", "/tmp", List.of(tool));

    List<work.ganglia.port.external.tool.ToolDefinition> defs = toolSet.getDefinitions();
    assertEquals(1, defs.size());
    assertEquals("my_tool", defs.get(0).name());
    assertEquals("An echo tool", defs.get(0).description());
  }

  @Test
  void testExecuteSuccessfulScript(Vertx vertx, VertxTestContext testContext) {
    SkillToolDefinition tool = echoTool("echo_tool", "echo 'hello from script'");
    ScriptSkillToolSet toolSet = new ScriptSkillToolSet(vertx, "test-skill", "/tmp", List.of(tool));

    toolSet
        .execute("echo_tool", Map.of(), createSessionContext(), new StubExecutionContext())
        .onComplete(
            testContext.succeeding(
                result -> {
                  testContext.verify(
                      () -> {
                        assertEquals(ToolInvokeResult.Status.SUCCESS, result.status());
                        assertTrue(result.output().contains("hello from script"));
                        testContext.completeNow();
                      });
                }));
  }

  @Test
  void testExecuteWithVariableSubstitution(Vertx vertx, VertxTestContext testContext) {
    SkillToolDefinition tool = echoTool("greet_tool", "echo 'Hello ${name}'");
    ScriptSkillToolSet toolSet = new ScriptSkillToolSet(vertx, "test-skill", "/tmp", List.of(tool));

    toolSet
        .execute(
            "greet_tool",
            Map.of("name", "World"),
            createSessionContext(),
            new StubExecutionContext())
        .onComplete(
            testContext.succeeding(
                result -> {
                  testContext.verify(
                      () -> {
                        assertEquals(ToolInvokeResult.Status.SUCCESS, result.status());
                        assertTrue(result.output().contains("Hello World"));
                        testContext.completeNow();
                      });
                }));
  }

  @Test
  void testExecuteSkillDirSubstitution(Vertx vertx, VertxTestContext testContext) {
    SkillToolDefinition tool = echoTool("dir_tool", "echo '${skillDir}'");
    ScriptSkillToolSet toolSet =
        new ScriptSkillToolSet(vertx, "test-skill", "/my/skill/dir", List.of(tool));

    toolSet
        .execute("dir_tool", Map.of(), createSessionContext(), new StubExecutionContext())
        .onComplete(
            testContext.succeeding(
                result -> {
                  testContext.verify(
                      () -> {
                        assertEquals(ToolInvokeResult.Status.SUCCESS, result.status());
                        assertTrue(result.output().contains("/my/skill/dir"));
                        testContext.completeNow();
                      });
                }));
  }

  @Test
  void testExecuteScriptNonZeroExitCode(Vertx vertx, VertxTestContext testContext) {
    SkillToolDefinition tool = echoTool("fail_tool", "exit 1");
    ScriptSkillToolSet toolSet = new ScriptSkillToolSet(vertx, "test-skill", "/tmp", List.of(tool));

    toolSet
        .execute("fail_tool", Map.of(), createSessionContext(), new StubExecutionContext())
        .onComplete(
            testContext.succeeding(
                result -> {
                  testContext.verify(
                      () -> {
                        assertEquals(ToolInvokeResult.Status.ERROR, result.status());
                        assertTrue(result.output().contains("exit code 1"));
                        testContext.completeNow();
                      });
                }));
  }

  @Test
  void testExecuteUnknownToolReturnsFailure(Vertx vertx, VertxTestContext testContext) {
    SkillToolDefinition tool = echoTool("known_tool", "echo ok");
    ScriptSkillToolSet toolSet = new ScriptSkillToolSet(vertx, "test-skill", "/tmp", List.of(tool));

    toolSet
        .execute("unknown_tool", Map.of(), createSessionContext(), new StubExecutionContext())
        .onComplete(
            testContext.failing(
                err -> {
                  testContext.verify(
                      () -> {
                        assertTrue(err.getMessage().contains("Tool not found"));
                        testContext.completeNow();
                      });
                }));
  }

  @Test
  void testExecuteToolMissingScript(Vertx vertx, VertxTestContext testContext) {
    SkillToolDefinition tool =
        new SkillToolDefinition("no_script", "No script", "SCRIPT", null, null, "{}");
    ScriptSkillToolSet toolSet = new ScriptSkillToolSet(vertx, "test-skill", "/tmp", List.of(tool));

    toolSet
        .execute("no_script", Map.of(), createSessionContext(), new StubExecutionContext())
        .onComplete(
            testContext.failing(
                err -> {
                  testContext.verify(
                      () -> {
                        assertTrue(err.getMessage().contains("missing script info"));
                        testContext.completeNow();
                      });
                }));
  }
}
