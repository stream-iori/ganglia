package work.ganglia.it.support.harness;

import java.util.List;

import work.ganglia.port.external.llm.ModelResponse;

/** Represents an E2E test case for the agent. */
public record TestScenario(
    String id,
    String name,
    String userInput,
    List<ModelResponse> mockResponses,
    List<InteractiveStep> interactiveSteps,
    List<Expectation> expectations) {
  public record InteractiveStep(String userInput, String expectedInterruptPrompt) {}

  public record Expectation(
      String type, // "FILE_EXISTS", "MEMORY_CONTAINS", "OUTPUT_CONTAINS"
      String value) {}
}
