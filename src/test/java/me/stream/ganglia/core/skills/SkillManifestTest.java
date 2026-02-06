package me.stream.ganglia.core.skills;

import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SkillManifestTest {

    @Test
    void testDeserialization() {
        String json = """
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
}
