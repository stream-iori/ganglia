package work.ganglia.infrastructure.internal.prompt.context;

import static org.junit.jupiter.api.Assertions.*;

import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import work.ganglia.infrastructure.internal.memory.TokenCounter;
import work.ganglia.port.internal.prompt.ContextFragment;

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
}
