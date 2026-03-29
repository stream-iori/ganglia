package work.ganglia.infrastructure.internal.prompt.context;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

import work.ganglia.port.internal.prompt.ContextBudget;
import work.ganglia.port.internal.prompt.ContextFragment;
import work.ganglia.util.TokenCounter;

@ExtendWith(VertxExtension.class)
class ContextComposerTest {

  private ContextComposer composer;
  private TokenCounter tokenCounter;

  @BeforeEach
  void setUp() {
    tokenCounter = new TokenCounter();
    composer = new ContextComposer(tokenCounter);
  }

  @Test
  void testComposeAndPrune(VertxTestContext testContext) {
    List<ContextFragment> fragments =
        List.of(
            new ContextFragment("Persona", "You are AI", 1, true),
            new ContextFragment("Memory", "Long ago...", 10, false),
            new ContextFragment("Plan", "Step 1", 6, false));

    // Limit to very few tokens to trigger pruning of priority 10
    String prompt = composer.compose(fragments, 10);

    assertTrue(prompt.contains("Persona"));
    assertFalse(prompt.contains("Memory")); // Should be pruned

    testContext.completeNow();
  }

  @ParameterizedTest
  @ValueSource(ints = {8000, 32000, 128000, 200000})
  void composePrunesWithDynamicBudget(int contextLimit) {
    ContextBudget budget = ContextBudget.from(contextLimit, 4096);

    List<ContextFragment> fragments =
        List.of(
            new ContextFragment("Persona", "You are AI", 1, true),
            new ContextFragment(
                "Memory",
                "A".repeat(budget.systemPrompt() * 5), // 5× over budget → must be pruned
                10,
                false),
            new ContextFragment("Plan", "Step 1", 6, false));

    String prompt = composer.compose(fragments, budget.systemPrompt());

    assertTrue(prompt.contains("Persona"), "Mandatory persona fragment must survive");
    int tokenCount = tokenCounter.count(prompt);
    assertTrue(
        tokenCount <= budget.systemPrompt() + 50, // small tolerance for formatting overhead
        "Composed prompt (%d tokens) should be within system budget (%d) for contextLimit=%d"
            .formatted(tokenCount, budget.systemPrompt(), contextLimit));
  }
}
