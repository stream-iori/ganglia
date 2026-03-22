package work.ganglia.infrastructure.external.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import work.ganglia.infrastructure.external.tool.model.ToolInvokeResult;
import work.ganglia.port.external.tool.ToolCall;
import work.ganglia.stubs.StubExecutionContext;

@ExtendWith(VertxExtension.class)
public class InteractionToolsTest {

  private InteractionTools tools;

  @BeforeEach
  void setUp(Vertx vertx) {
    tools = new InteractionTools(vertx);
  }

  static Stream<Arguments> interactionProvider() {
    return Stream.of(
        arguments(
            Map.of(
                "questions",
                List.of(Map.of("question", "Details please?", "type", "text", "header", "Test"))),
            List.of("Details please?"),
            "Text input request"),
        arguments(
            Map.of(
                "questions",
                List.of(
                    Map.of(
                        "question",
                        "Pick one?",
                        "type",
                        "choice",
                        "header",
                        "Test",
                        "options",
                        List.of(
                            Map.of("label", "A", "description", "Desc A"),
                            Map.of("label", "B", "description", "Desc B"))))),
            List.of("Pick one?", "1. A", "2. B"),
            "Choice selection request"));
  }

  @ParameterizedTest(name = "{2}")
  @MethodSource("interactionProvider")
  @DisplayName("Interaction Tools Tests")
  void testInteraction(
      Map<String, Object> args,
      List<String> expectedSubstrings,
      String description,
      VertxTestContext testContext) {
    tools
        .execute(new ToolCall("id", "ask_selection", args), null, new StubExecutionContext())
        .onComplete(
            testContext.succeeding(
                res -> {
                  testContext.verify(
                      () -> {
                        assertEquals(
                            ToolInvokeResult.Status.INTERRUPT,
                            res.status(),
                            () ->
                                "Expected INTERRUPT but got " + res.status() + ": " + res.output());
                        for (String expected : expectedSubstrings) {
                          assertTrue(
                              res.output().contains(expected),
                              () ->
                                  String.format(
                                      "Output for '%s' should contain '%s'. Actual: %s",
                                      description, expected, res.output()));
                        }
                        testContext.completeNow();
                      });
                }));
  }
}
