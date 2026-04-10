package work.ganglia.it.component.skill;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

import io.vertx.core.Vertx;
import io.vertx.junit5.VertxTestContext;

import work.ganglia.it.support.MockModelIT;

public class SkillBootstrapIT extends MockModelIT {

  @Test
  void dynamicSkillLoading_bootstrapsSuccessfully(Vertx vertx, VertxTestContext testContext) {
    assertNotNull(ganglia.agentLoop());
    testContext.completeNow();
  }
}
