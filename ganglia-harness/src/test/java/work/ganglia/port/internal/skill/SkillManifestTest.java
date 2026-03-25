package work.ganglia.port.internal.skill;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import io.vertx.core.json.JsonObject;

class SkillManifestTest {

  @Test
  void testDeserialization() {
    String json =
        """
        {
          "id": "java-expert",
          "version": "1.0.0",
          "name": "Java Specialist",
          "description": "Expert in Java 17",
          "author": "Ganglia Team",
          "prompts": [
            {
              "id": "java-style",
              "path": "prompts/style.md",
              "priority": 10
            }
          ],
          "tools": [
            "me.stream.ganglia.tools.JavaTool"
          ],
          "activationTriggers": {
            "filePatterns": ["pom.xml"],
            "keywords": ["java"]
          }
        }
        """;

    JsonObject jsonObject = new JsonObject(json);
    SkillManifest manifest = SkillManifest.fromJson(jsonObject);

    assertEquals("java-expert", manifest.id());
    assertEquals("1.0.0", manifest.version());
    assertEquals("Java Specialist", manifest.name());
    assertEquals(1, manifest.prompts().size());
    assertEquals("java-style", manifest.prompts().get(0).id());
    assertEquals(1, manifest.tools().size());
    assertEquals("me.stream.ganglia.tools.JavaTool", manifest.tools().get(0));
    assertNotNull(manifest.activationTriggers());
    assertTrue(manifest.activationTriggers().filePatterns().contains("pom.xml"));
  }

  @Test
  void testMarkdownParsing() {
    String md =
        """
        ---
        id: git-smart-commit
        name: Git Smart Commit
        description: Analyzes staged changes
        version: 1.1.0
        ---
        # Instructions
        Run git status...
        """;

    SkillManifest manifest = SkillManifest.fromMarkdown("git-folder", md);

    assertEquals("git-smart-commit", manifest.id());
    assertEquals("Git Smart Commit", manifest.name());
    assertEquals("Analyzes staged changes", manifest.description());
    assertEquals("1.1.0", manifest.version());
    assertTrue(manifest.instructions().contains("# Instructions"));
    assertTrue(manifest.instructions().contains("Run git status..."));
  }

  @Test
  void testMarkdownParsingWithImplicitId() {
    String md =
        """
        ---
        name: Git Smart Commit
        description: Analyzes staged changes
        ---
        # Instructions
        """;

    SkillManifest manifest = SkillManifest.fromMarkdown("git-folder", md);

    assertEquals("git-folder", manifest.id());
    assertEquals("Git Smart Commit", manifest.name());
    assertTrue(manifest.instructions().contains("# Instructions"));
  }
}
