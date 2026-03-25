package work.ganglia.infrastructure.internal.prompt.context;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
class EnvironmentSourceTest {

  private EnvironmentSource source;

  @BeforeEach
  void setUp(Vertx vertx) {
    source = new EnvironmentSource(vertx);
  }

  @Test
  void testGetFragments(VertxTestContext testContext) {
    source
        .getFragments(null)
        .onComplete(
            testContext.succeeding(
                fragments -> {
                  assertFalse(fragments.isEmpty());
                  assertTrue(fragments.stream().anyMatch(f -> f.name().equals("OS")));
                  assertTrue(
                      fragments.stream().anyMatch(f -> f.name().equals("Working Directory")));
                  testContext.completeNow();
                }));
  }
}
