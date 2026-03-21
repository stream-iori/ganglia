package work.ganglia.port.internal.skill;

import static org.junit.jupiter.api.Assertions.*;

import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import work.ganglia.infrastructure.internal.skill.DefaultSkillService;
import work.ganglia.infrastructure.internal.skill.FileSystemSkillLoader;

@ExtendWith(VertxExtension.class)
class SkillServiceTest {

  @Test
  void testLoadSkillsFromMarkdown(
      Vertx vertx, VertxTestContext testContext, @TempDir Path skillsDir) {
    Path mySkillDir = skillsDir.resolve("my-skill");
    mySkillDir.toFile().mkdirs();

    String content =
        """
                ---
                id: test-skill
                name: Test Skill
                description: A test skill
                ---
                Instructions here.
                """;
    vertx
        .fileSystem()
        .writeFileBlocking(
            mySkillDir.resolve("SKILL.md").toString(), io.vertx.core.buffer.Buffer.buffer(content));

    SkillLoader loader = new FileSystemSkillLoader(vertx, List.of(skillsDir));
    SkillService service = new DefaultSkillService(loader);

    service
        .init()
        .onComplete(
            testContext.succeeding(
                v -> {
                  testContext.verify(
                      () -> {
                        List<SkillManifest> skills = service.getAvailableSkills();
                        assertEquals(1, skills.size());
                        assertEquals("test-skill", skills.get(0).id());
                        assertEquals("Test Skill", skills.get(0).name());
                        assertEquals("Instructions here.", skills.get(0).instructions());
                        testContext.completeNow();
                      });
                }));
  }
}
