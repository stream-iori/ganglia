package work.ganglia.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.params.provider.Arguments.arguments;

public class MarkdownRendererTest {

    private final MarkdownRenderer renderer = new MarkdownRenderer();

    static Stream<Arguments> markdownProvider() {
        return Stream.of(
            arguments("# Title", List.of("\u001B[4;36;1mTitle"), "Headers should have ANSI colors and underline"),
            arguments("**bold**", List.of("\u001B[1mbold"), "Bold should have ANSI bold code"),
            arguments("*italic*", List.of("\u001B[3mitalic"), "Italic should have ANSI italic code"),
            arguments("```java\nint x = 1;\n```", List.of("--- Code Block ---", "int x = 1;"), "Code blocks should have delimiters")
        );
    }

    @ParameterizedTest(name = "{2}")
    @MethodSource("markdownProvider")
    @DisplayName("Markdown Rendering Tests")
    void testRendering(String input, List<String> expectedSubstrings, String description) {
        String result = renderer.render(input);
        for (String expected : expectedSubstrings) {
            assertTrue(result.contains(expected), 
                () -> String.format("Result for '%s' should contain '%s'. Actual result: %s", description, expected, result));
        }
    }
}
