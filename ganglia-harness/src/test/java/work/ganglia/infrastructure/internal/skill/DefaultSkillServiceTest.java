package work.ganglia.infrastructure.internal.skill;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.core.Future;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

import work.ganglia.port.internal.skill.SkillLoader;
import work.ganglia.port.internal.skill.SkillManifest;

@ExtendWith(VertxExtension.class)
class DefaultSkillServiceTest {

  private SkillManifest skill(String id, String name) {
    return new SkillManifest(
        id, "1.0", name, "desc", null, null, null, null, null, null, null, null);
  }

  @Test
  void testInitLoadsSkills(VertxTestContext testContext) {
    SkillLoader loader =
        new SkillLoader() {
          @Override
          public Future<List<SkillManifest>> load() {
            return Future.succeededFuture(
                List.of(skill("s1", "Skill One"), skill("s2", "Skill Two")));
          }
        };

    DefaultSkillService service = new DefaultSkillService(loader);
    service
        .init()
        .onComplete(
            testContext.succeeding(
                v -> {
                  testContext.verify(
                      () -> {
                        assertEquals(2, service.getAvailableSkills().size());
                        assertTrue(service.getSkill("s1").isPresent());
                        assertTrue(service.getSkill("s2").isPresent());
                        testContext.completeNow();
                      });
                }));
  }

  @Test
  void testGetSkillNotFound() {
    DefaultSkillService service = new DefaultSkillService(List.of());
    Optional<SkillManifest> result = service.getSkill("nonexistent");
    assertFalse(result.isPresent());
  }

  @Test
  void testReloadClearsAndReloads(VertxTestContext testContext) {
    SkillLoader loader =
        new SkillLoader() {
          @Override
          public Future<List<SkillManifest>> load() {
            return Future.succeededFuture(List.of(skill("s1", "Skill One")));
          }
        };

    DefaultSkillService service = new DefaultSkillService(loader);
    service
        .init()
        .compose(v -> service.reload())
        .onComplete(
            testContext.succeeding(
                v -> {
                  testContext.verify(
                      () -> {
                        assertEquals(1, service.getAvailableSkills().size());
                        testContext.completeNow();
                      });
                }));
  }

  @Test
  void testMultipleLoaders(VertxTestContext testContext) {
    SkillLoader loader1 = () -> Future.succeededFuture(List.of(skill("s1", "Skill 1")));
    SkillLoader loader2 =
        () -> Future.succeededFuture(List.of(skill("s2", "Skill 2"), skill("s3", "Skill 3")));

    DefaultSkillService service = new DefaultSkillService(List.of(loader1, loader2));
    service
        .init()
        .onComplete(
            testContext.succeeding(
                v -> {
                  testContext.verify(
                      () -> {
                        assertEquals(3, service.getAvailableSkills().size());
                        testContext.completeNow();
                      });
                }));
  }

  @Test
  void testSkillManifestFromMarkdownBasic() {
    String content =
        """
        ---
        id: test-skill
        version: 1.1
        name: Test Skill
        description: A test skill
        ---
        ## Instructions

        Do something useful.
        """;

    SkillManifest manifest = SkillManifest.fromMarkdown("test-skill", content);
    assertEquals("test-skill", manifest.id());
    assertEquals("1.1", manifest.version());
    assertEquals("Test Skill", manifest.name());
    assertTrue(manifest.instructions().contains("Instructions"));
  }

  @Test
  void testSkillManifestFromMarkdownNoFrontmatter() {
    SkillManifest manifest = SkillManifest.fromMarkdown("my-skill", "Just some instructions");
    assertEquals("my-skill", manifest.id());
    assertEquals("Just some instructions", manifest.instructions());
  }

  @Test
  void testSkillManifestFromMarkdownWithFilePatterns() {
    String content =
        """
        ---
        id: maven-skill
        name: Maven
        filePatterns:
          - pom.xml
          - "*.mvn"
        ---
        Maven instructions here.
        """;

    SkillManifest manifest = SkillManifest.fromMarkdown("maven-skill", content);
    assertEquals("maven-skill", manifest.id());
    assertFalse(manifest.activationTriggers().filePatterns().isEmpty());
    assertTrue(manifest.activationTriggers().filePatterns().contains("pom.xml"));
  }

  @Test
  void testSkillManifestFromMarkdownWithActivationTriggers() {
    String content =
        """
        ---
        id: node-skill
        name: Node.js
        activationTriggers:
          filePatterns:
            - package.json
          keywords:
            - nodejs
        ---
        Node instructions.
        """;

    SkillManifest manifest = SkillManifest.fromMarkdown("node-skill", content);
    assertEquals("node-skill", manifest.id());
    assertTrue(manifest.activationTriggers().filePatterns().contains("package.json"));
    assertTrue(manifest.activationTriggers().keywords().contains("nodejs"));
  }

  @Test
  void testSkillManifestFromMarkdownWithSkillDirAndJarPath() {
    String content = "---\nid: x\n---\nInstructions.";
    SkillManifest manifest =
        SkillManifest.fromMarkdown("x", content, "/skills/x", "/skills/x/x.jar");
    assertEquals("/skills/x", manifest.skillDir());
    assertEquals("/skills/x/x.jar", manifest.jarPath());
  }

  @Test
  void testSkillManifestSkillTriggerFromJsonNull() {
    SkillManifest.SkillTrigger trigger = SkillManifest.SkillTrigger.fromJson(null);
    assertTrue(trigger.filePatterns().isEmpty());
    assertTrue(trigger.keywords().isEmpty());
  }

  @Test
  void testSkillManifestFromJson() {
    io.vertx.core.json.JsonObject json =
        new io.vertx.core.json.JsonObject()
            .put("id", "json-skill")
            .put("name", "JSON Skill")
            .put("version", "2.0")
            .put("description", "A JSON skill")
            .put("author", "Tester");

    SkillManifest manifest = SkillManifest.fromJson(json);
    assertEquals("json-skill", manifest.id());
    assertEquals("JSON Skill", manifest.name());
    assertEquals("2.0", manifest.version());
    assertEquals("A JSON skill", manifest.description());
  }

  @Test
  void testSkillManifestFromJsonWithTools() {
    io.vertx.core.json.JsonArray tools =
        new io.vertx.core.json.JsonArray().add("bash").add("python");
    io.vertx.core.json.JsonObject json =
        new io.vertx.core.json.JsonObject()
            .put("id", "tool-skill")
            .put("name", "Tool Skill")
            .put("tools", tools);

    SkillManifest manifest = SkillManifest.fromJson(json);
    assertEquals(2, manifest.tools().size());
    assertTrue(manifest.tools().contains("bash"));
  }

  @Test
  void testSkillManifestFromJsonWithTriggers() {
    io.vertx.core.json.JsonObject triggers =
        new io.vertx.core.json.JsonObject()
            .put("filePatterns", new io.vertx.core.json.JsonArray().add("pom.xml"))
            .put("keywords", new io.vertx.core.json.JsonArray().add("maven"));
    io.vertx.core.json.JsonObject json =
        new io.vertx.core.json.JsonObject()
            .put("id", "maven-skill")
            .put("name", "Maven")
            .put("activationTriggers", triggers);

    SkillManifest manifest = SkillManifest.fromJson(json);
    assertFalse(manifest.activationTriggers().filePatterns().isEmpty());
    assertTrue(manifest.activationTriggers().filePatterns().contains("pom.xml"));
  }

  @Test
  void testSkillManifestFromJsonWithPrompts() {
    io.vertx.core.json.JsonObject prompt =
        new io.vertx.core.json.JsonObject()
            .put("id", "p1")
            .put("path", "prompts/main.md")
            .put("priority", 5);
    io.vertx.core.json.JsonObject json =
        new io.vertx.core.json.JsonObject()
            .put("id", "prompt-skill")
            .put("name", "Prompt Skill")
            .put("prompts", new io.vertx.core.json.JsonArray().add(prompt));

    SkillManifest manifest = SkillManifest.fromJson(json);
    assertEquals(1, manifest.prompts().size());
    assertEquals("p1", manifest.prompts().get(0).id());
    assertEquals("prompts/main.md", manifest.prompts().get(0).path());
    assertEquals(5, manifest.prompts().get(0).priority());
  }

  @Test
  void testSkillManifestFromMarkdownWithListKeywords() {
    // Test parseList with a comma-separated string
    String content =
        """
        ---
        id: cs-skill
        name: CS Skill
        filePatterns: "*.cs,*.csproj"
        ---
        C# instructions.
        """;
    SkillManifest manifest = SkillManifest.fromMarkdown("cs-skill", content);
    assertFalse(manifest.activationTriggers().filePatterns().isEmpty());
  }
}
