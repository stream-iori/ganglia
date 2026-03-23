package work.ganglia.infrastructure.internal.prompt.context;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import work.ganglia.port.internal.prompt.ContextFragment;
import work.ganglia.util.TokenCounter;

class ContextLayeringIntegrationTest {

  private ContextComposer composer;
  private TokenCounter tokenCounter;

  @BeforeEach
  void setUp() {
    tokenCounter = new TokenCounter();
    composer = new ContextComposer(tokenCounter);
  }

  @Test
  void testLayeringAndPruningLogic() {
    List<ContextFragment> fragments = new ArrayList<>();

    // Layer 1-3: Soul (Mandatory)
    fragments.add(
        ContextFragment.mandatory(
            "Persona",
            "I am a senior software engineer helper.",
            ContextFragment.PRIORITY_PERSONA));
    fragments.add(
        ContextFragment.mandatory(
            "Mandates",
            "1. Security First. 2. No unprompted commits.",
            ContextFragment.PRIORITY_MANDATES));
    fragments.add(
        ContextFragment.mandatory(
            "Workflow", "Research -> Strategy -> Execution.", ContextFragment.PRIORITY_WORKFLOW));

    // Layer 5: Context (Prunable)
    // Memory is Priority 60 (Pruned First)
    String longMemory =
        "Long term memory about project history and architectural decisions made last year. "
            .repeat(10);
    fragments.add(ContextFragment.prunable("Memory", longMemory, ContextFragment.PRIORITY_MEMORY));

    // Environment is Priority 50 (Pruned Second)
    String longEnv =
        "OS: MacOS, Path: /Users/stream/codes/ganglia, Arch: aarch64, Java: 17. ".repeat(10);
    fragments.add(
        ContextFragment.prunable("Environment", longEnv, ContextFragment.PRIORITY_ENVIRONMENT));

    // 1. Verify standard composition (all included)
    String fullPrompt = composer.compose(fragments, 5000);
    assertTrue(fullPrompt.contains("Memory"), "Memory should be present when budget is huge");
    assertTrue(
        fullPrompt.contains("Environment"), "Environment should be present when budget is huge");

    // 2. Verify Pruning: Memory (Priority 60) should go first
    // Total tokens without Memory is around 150-200. Let's set budget to 300.
    // Memory itself is very large now.
    String prunedMemory = composer.compose(fragments, 300);
    assertFalse(
        prunedMemory.contains("Memory"),
        "Memory (60) should be pruned first because it's non-mandatory and has highest priority");
    assertTrue(
        prunedMemory.contains("Environment"),
        "Environment (50) should still be present because it's lower priority than Memory");
    assertTrue(prunedMemory.contains("Persona"), "Mandatory Persona must be present");

    // 3. Verify Pruning: Environment (Priority 50) goes next
    // Mandatory layers are around 50 tokens. Let's set budget to 60.
    String onlyMandatory = composer.compose(fragments, 60);
    assertFalse(onlyMandatory.contains("Memory"), "Memory should be gone");
    assertFalse(onlyMandatory.contains("Environment"), "Environment should be gone");
    assertTrue(onlyMandatory.contains("Workflow"), "Mandatory Workflow must stay");

    // 4. Verify Mandatory Protection: Even if budget is tight (but enough for mandatory), they stay
    // Setting budget to 100 which is enough for the mandatory strings but not for long prunable
    // ones.
    String strictPruning = composer.compose(fragments, 100);
    assertTrue(strictPruning.contains("Persona"), "Persona must persist");
    assertTrue(strictPruning.contains("Workflow"), "Workflow must persist");
    assertFalse(strictPruning.contains("Memory"), "Memory must be gone");
  }
}
