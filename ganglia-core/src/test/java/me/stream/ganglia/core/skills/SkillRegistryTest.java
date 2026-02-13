package me.stream.ganglia.core.skills;

import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import me.stream.ganglia.skills.SkillManifest;
import me.stream.ganglia.skills.SkillRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(VertxExtension.class)
class SkillRegistryTest {

    @Test
    void testLoadFromDirectory(Vertx vertx, VertxTestContext testContext) {
        Path skillsDir = Paths.get("src/test/resources/skills");
        SkillRegistry registry = new SkillRegistry(vertx, java.util.List.of(skillsDir));

        registry.init().onComplete(testContext.succeeding(v -> {
            var skills = registry.listAvailableSkills();
            assertFalse(skills.isEmpty());
            assertTrue(skills.stream().anyMatch(s -> s.id().equals("test-skill")));

            SkillManifest testSkill = registry.getSkill("test-skill");
            assertNotNull(testSkill);
            assertEquals("Test Skill", testSkill.name());
            testContext.completeNow();
        }));
    }
}
