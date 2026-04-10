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

  // ── init ───────────────────────────────────────────────────────────────────

  @Test
  void init_loadsSkillsFromLoader(VertxTestContext testContext) {
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
  void init_multipleLoaders_mergesSkills(VertxTestContext testContext) {
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

  // ── getSkill ───────────────────────────────────────────────────────────────

  @Test
  void getSkill_notFound_returnsEmpty() {
    DefaultSkillService service = new DefaultSkillService(List.of());
    Optional<SkillManifest> result = service.getSkill("nonexistent");
    assertFalse(result.isPresent());
  }

  // ── reload ─────────────────────────────────────────────────────────────────

  @Test
  void reload_clearsAndReloadsSkills(VertxTestContext testContext) {
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
}
