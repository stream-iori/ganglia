package work.ganglia.it.support.harness;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

import work.ganglia.port.external.llm.ModelResponse;
import work.ganglia.port.internal.state.TokenUsage;

@ExtendWith(VertxExtension.class)
public class E2EHarnessTest {

  private E2ETestHarness harness;

  @BeforeEach
  void setUp(Vertx vertx, VertxTestContext testContext) {
    harness = new E2ETestHarness(vertx);
    harness.setup().onComplete(testContext.succeedingThenComplete());
  }

  @Test
  void testBasicScenario(Vertx vertx, VertxTestContext testContext) {
    TestScenario scenario =
        new TestScenario(
            "hello",
            "Basic Hello",
            "Hello",
            List.of(new ModelResponse("Hi there!", Collections.emptyList(), new TokenUsage(1, 1))),
            Collections.emptyList(),
            List.of(new TestScenario.Expectation("OUTPUT_CONTAINS", "Hi there!")));

    harness
        .runScenario(scenario)
        .onComplete(
            testContext.succeeding(
                result -> {
                  testContext.completeNow();
                }));
  }
}
