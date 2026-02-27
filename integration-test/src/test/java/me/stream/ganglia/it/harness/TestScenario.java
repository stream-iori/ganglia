package me.stream.ganglia.it.harness;

import me.stream.ganglia.core.model.ModelResponse;
import java.util.List;

/**
 * Represents an E2E test case for the agent.
 */
public record TestScenario(
    String id,
    String name,
    String userInput,
    List<ModelResponse> mockResponses,
    List<InteractiveStep> interactiveSteps,
    List<Expectation> expectations
) {
    public record InteractiveStep(
        String userInput,
        String expectedInterruptPrompt
    ) {}

    public record Expectation(
        String type, // "FILE_EXISTS", "MEMORY_CONTAINS", "OUTPUT_CONTAINS"
        String value
    ) {}
}
