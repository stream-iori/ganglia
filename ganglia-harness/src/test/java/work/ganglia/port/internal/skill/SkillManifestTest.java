package work.ganglia.port.internal.skill;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

class SkillManifestTest {

  // ── fromJson ───────────────────────────────────────────────────────────────

  @Test
  void fromJson_fullManifest_deserializesAllFields() {
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
  void fromJson_basicFields_parsesIdNameVersion() {
    JsonObject json =
        new JsonObject()
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
  void fromJson_withTools_parsesToolList() {
    JsonArray tools = new JsonArray().add("bash").add("python");
    JsonObject json =
        new JsonObject().put("id", "tool-skill").put("name", "Tool Skill").put("tools", tools);

    SkillManifest manifest = SkillManifest.fromJson(json);
    assertEquals(2, manifest.tools().size());
    assertTrue(manifest.tools().contains("bash"));
  }

  @Test
  void fromJson_withTriggers_parsesFilePatternsAndKeywords() {
    JsonObject triggers =
        new JsonObject()
            .put("filePatterns", new JsonArray().add("pom.xml"))
            .put("keywords", new JsonArray().add("maven"));
    JsonObject json =
        new JsonObject()
            .put("id", "maven-skill")
            .put("name", "Maven")
            .put("activationTriggers", triggers);

    SkillManifest manifest = SkillManifest.fromJson(json);
    assertFalse(manifest.activationTriggers().filePatterns().isEmpty());
    assertTrue(manifest.activationTriggers().filePatterns().contains("pom.xml"));
  }

  @Test
  void fromJson_withPrompts_parsesPromptEntries() {
    JsonObject prompt =
        new JsonObject().put("id", "p1").put("path", "prompts/main.md").put("priority", 5);
    JsonObject json =
        new JsonObject()
            .put("id", "prompt-skill")
            .put("name", "Prompt Skill")
            .put("prompts", new JsonArray().add(prompt));

    SkillManifest manifest = SkillManifest.fromJson(json);
    assertEquals(1, manifest.prompts().size());
    assertEquals("p1", manifest.prompts().get(0).id());
    assertEquals("prompts/main.md", manifest.prompts().get(0).path());
    assertEquals(5, manifest.prompts().get(0).priority());
  }

  // ── fromMarkdown ───────────────────────────────────────────────────────────

  @Test
  void fromMarkdown_fullFrontmatter_parsesAllFields() {
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
  void fromMarkdown_implicitId_usesProvidedId() {
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

  @Test
  void fromMarkdown_basicFrontmatter_parsesIdVersionName() {
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
  void fromMarkdown_noFrontmatter_usesBodyAsInstructions() {
    SkillManifest manifest = SkillManifest.fromMarkdown("my-skill", "Just some instructions");
    assertEquals("my-skill", manifest.id());
    assertEquals("Just some instructions", manifest.instructions());
  }

  @Test
  void fromMarkdown_withFilePatterns_parsesPatterns() {
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
  void fromMarkdown_withActivationTriggers_parsesPatternsAndKeywords() {
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
  void fromMarkdown_withSkillDirAndJarPath_setsFields() {
    String content = "---\nid: x\n---\nInstructions.";
    SkillManifest manifest =
        SkillManifest.fromMarkdown("x", content, "/skills/x", "/skills/x/x.jar");
    assertEquals("/skills/x", manifest.skillDir());
    assertEquals("/skills/x/x.jar", manifest.jarPath());
  }

  @Test
  void fromMarkdown_commaSeparatedFilePatterns_parsesList() {
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

  // ── SkillTrigger ───────────────────────────────────────────────────────────

  @Test
  void skillTrigger_fromJsonNull_returnsEmptyTrigger() {
    SkillManifest.SkillTrigger trigger = SkillManifest.SkillTrigger.fromJson(null);
    assertTrue(trigger.filePatterns().isEmpty());
    assertTrue(trigger.keywords().isEmpty());
  }
}
