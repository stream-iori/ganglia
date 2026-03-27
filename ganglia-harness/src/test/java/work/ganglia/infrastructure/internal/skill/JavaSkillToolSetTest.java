package work.ganglia.infrastructure.internal.skill;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

import work.ganglia.BaseGangliaTest;
import work.ganglia.infrastructure.external.tool.model.ToolInvokeResult;
import work.ganglia.port.external.tool.ToolDefinition;
import work.ganglia.port.internal.skill.SkillToolDefinition;
import work.ganglia.stubs.StubExecutionContext;

@ExtendWith(VertxExtension.class)
class JavaSkillToolSetTest extends BaseGangliaTest {

  // A simple ToolSet that can be loaded by name — use the current classloader
  private SkillToolDefinition javaToolDef(String name, String className) {
    return new SkillToolDefinition(
        name, "Java tool", "JAVA", null, new SkillToolDefinition.JavaInfo(className), "{}");
  }

  @Test
  void testGetDefinitionsWithValidClass(VertxTestContext testContext) {
    // Use a real class that implements ToolSet and is on the classpath
    String className = "work.ganglia.infrastructure.internal.skill.StubJavaToolSet";
    SkillToolDefinition def = javaToolDef("stub_java_tool", className);

    JavaSkillToolSet toolSet =
        new JavaSkillToolSet(
            "test-skill", Thread.currentThread().getContextClassLoader(), List.of(def));

    List<ToolDefinition> defs = toolSet.getDefinitions();
    // StubJavaToolSet defines "stub_java_tool"
    assertFalse(defs.isEmpty());
    testContext.completeNow();
  }

  @Test
  void testGetDefinitionsWithUnknownClass() {
    SkillToolDefinition def = javaToolDef("ghost_tool", "com.nonexistent.GhostToolSet");
    JavaSkillToolSet toolSet =
        new JavaSkillToolSet(
            "test-skill", Thread.currentThread().getContextClassLoader(), List.of(def));

    // Unknown class can't be instantiated — getOrInstantiate returns null
    List<ToolDefinition> defs = toolSet.getDefinitions();
    assertTrue(defs.isEmpty(), "Unknown class should yield empty definitions");
  }

  @Test
  void testExecuteWithValidClass(VertxTestContext testContext) {
    String className = "work.ganglia.infrastructure.internal.skill.StubJavaToolSet";
    SkillToolDefinition def = javaToolDef("stub_java_tool", className);

    JavaSkillToolSet toolSet =
        new JavaSkillToolSet(
            "test-skill", Thread.currentThread().getContextClassLoader(), List.of(def));

    toolSet
        .execute("stub_java_tool", Map.of(), createSessionContext(), new StubExecutionContext())
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
  void testExecuteWithUnknownClass(VertxTestContext testContext) {
    SkillToolDefinition def = javaToolDef("ghost_tool", "com.nonexistent.GhostToolSet");
    JavaSkillToolSet toolSet =
        new JavaSkillToolSet(
            "test-skill", Thread.currentThread().getContextClassLoader(), List.of(def));

    toolSet
        .execute("ghost_tool", Map.of(), createSessionContext(), new StubExecutionContext())
        .onComplete(
            testContext.failing(
                err -> {
                  testContext.verify(
                      () -> {
                        assertTrue(err.getMessage().contains("Failed to instantiate"));
                        testContext.completeNow();
                      });
                }));
  }

  @Test
  void testExecuteUnknownToolName(VertxTestContext testContext) {
    JavaSkillToolSet toolSet =
        new JavaSkillToolSet(
            "test-skill", Thread.currentThread().getContextClassLoader(), List.of());

    toolSet
        .execute("nonexistent_tool", Map.of(), createSessionContext(), new StubExecutionContext())
        .onComplete(
            testContext.failing(
                err -> {
                  testContext.verify(
                      () -> {
                        assertTrue(err.getMessage().contains("Tool not found in Java skill"));
                        testContext.completeNow();
                      });
                }));
  }
}
