package work.ganglia.infrastructure.internal.skill;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

import work.ganglia.BaseGangliaTest;
import work.ganglia.port.internal.skill.SkillManifest;

@ExtendWith(VertxExtension.class)
class JarSkillLoaderTest extends BaseGangliaTest {

  private File createSkillJar(File dir, String jarName, String skillMdContent) throws Exception {
    File jar = new File(dir, jarName);
    try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(jar))) {
      JarEntry entry = new JarEntry("SKILL.md");
      jos.putNextEntry(entry);
      jos.write(skillMdContent.getBytes());
      jos.closeEntry();
    }
    return jar;
  }

  private static final String SKILL_MD =
      """
      ---
      id: test-skill
      name: Test Skill
      version: 1.0
      description: A test skill
      ---
      # Test Skill
      Instructions here.
      """;

  @Test
  void testLoadFromNonExistentDir(Vertx vertx, VertxTestContext testContext) {
    JarSkillLoader loader =
        new JarSkillLoader(vertx, List.of(Path.of("/nonexistent/path/that/does/not/exist")));

    loader
        .load()
        .onComplete(
            testContext.succeeding(
                skills -> {
                  testContext.verify(
                      () -> {
                        assertTrue(skills.isEmpty(), "Non-existent dir should return empty list");
                        testContext.completeNow();
                      });
                }));
  }

  @Test
  void testLoadFromEmptyDir(Vertx vertx, VertxTestContext testContext, @TempDir File tmp) {
    JarSkillLoader loader = new JarSkillLoader(vertx, List.of(tmp.toPath()));

    loader
        .load()
        .onComplete(
            testContext.succeeding(
                skills -> {
                  testContext.verify(
                      () -> {
                        assertTrue(skills.isEmpty(), "Empty dir returns empty list");
                        testContext.completeNow();
                      });
                }));
  }

  @Test
  void testLoadSkillFromJar(Vertx vertx, VertxTestContext testContext, @TempDir File tmp)
      throws Exception {
    createSkillJar(tmp, "my-skill.jar", SKILL_MD);

    JarSkillLoader loader = new JarSkillLoader(vertx, List.of(tmp.toPath()));

    loader
        .load()
        .onComplete(
            testContext.succeeding(
                skills -> {
                  testContext.verify(
                      () -> {
                        assertNotNull(skills);
                        assertTrue(skills.size() >= 1, "Should load skill from JAR");
                        SkillManifest skill = skills.get(0);
                        assertNotNull(skill.id());
                        testContext.completeNow();
                      });
                }));
  }

  @Test
  void testLoadJarMissingSkillMd(Vertx vertx, VertxTestContext testContext, @TempDir File tmp)
      throws Exception {
    // Create JAR without SKILL.md entry
    File jar = new File(tmp, "no-skill-md.jar");
    try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(jar))) {
      JarEntry entry = new JarEntry("SomeClass.class");
      jos.putNextEntry(entry);
      jos.write(new byte[10]);
      jos.closeEntry();
    }

    JarSkillLoader loader = new JarSkillLoader(vertx, List.of(tmp.toPath()));

    loader
        .load()
        .onComplete(
            testContext.succeeding(
                skills -> {
                  testContext.verify(
                      () -> {
                        // JAR without SKILL.md returns null, which is filtered out
                        assertTrue(skills.isEmpty(), "JAR without SKILL.md ignored");
                        testContext.completeNow();
                      });
                }));
  }

  @Test
  void testLoadMultipleDirs(Vertx vertx, VertxTestContext testContext, @TempDir File tmp)
      throws Exception {
    File dir1 = new File(tmp, "dir1");
    File dir2 = new File(tmp, "dir2");
    dir1.mkdirs();
    dir2.mkdirs();

    createSkillJar(dir1, "skill-a.jar", SKILL_MD);

    JarSkillLoader loader = new JarSkillLoader(vertx, List.of(dir1.toPath(), dir2.toPath()));

    loader
        .load()
        .onComplete(
            testContext.succeeding(
                skills -> {
                  testContext.verify(
                      () -> {
                        assertTrue(skills.size() >= 1);
                        testContext.completeNow();
                      });
                }));
  }
}
