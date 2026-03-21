package work.ganglia.infrastructure.internal.prompt.context;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import work.ganglia.port.internal.prompt.ContextFragment;

@ExtendWith(VertxExtension.class)
class MarkdownContextResolverTest {

  private MarkdownContextResolver resolver;

  @BeforeEach
  void setUp(Vertx vertx) {
    resolver = new MarkdownContextResolver(vertx);
  }

  static Stream<Arguments> markdownProvider() {
    return Stream.of(
        arguments(
            """
                # Project
                ## [Mandates] (Priority: 2)
                - Do not delete
                ## [Context] (Priority: 3, Mandatory)
                - Use Java 17
                ## Simple Header
                Some content for simple header.
                """,
            3,
            "Normal content with various headers"),
        arguments("", 0, "Empty content"),
        arguments(
            """
                # Title
                ### H3 Header
                Some content
                """,
            0,
            "No H2 headers"));
  }

  @ParameterizedTest(name = "{2}")
  @MethodSource("markdownProvider")
  @DisplayName("MarkdownContextResolver Parameterized Test")
  void testParse(
      String content, int expectedSize, String description, VertxTestContext testContext) {
    resolver
        .parse("test.md", content)
        .onComplete(
            testContext.succeeding(
                fragments -> {
                  testContext.verify(
                      () -> {
                        assertEquals(expectedSize, fragments.size());
                        if (expectedSize > 0) {
                          ContextFragment f1 = fragments.get(0);
                          assertNotNull(f1.name());
                          assertNotNull(f1.content());
                        }
                        testContext.completeNow();
                      });
                }));
  }
}
