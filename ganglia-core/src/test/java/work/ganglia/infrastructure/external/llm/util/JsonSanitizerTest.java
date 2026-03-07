package work.ganglia.infrastructure.external.llm.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.params.provider.Arguments.arguments;

class JsonSanitizerTest {

    static Stream<Arguments> jsonSanitizeProvider() {
        return Stream.of(
            arguments(
                """
                Here is the result:
                ```json
                {"key": "value"}
                ```
                Hope it helps!
                """, 
                "{\"key\": \"value\"}",
                "Should strip markdown formatting"
            ),
            arguments(
                "{\"list\": [1, 2, 3, ], \"obj\": {\"a\": 1,}}",
                "{\"list\": [1, 2, 3], \"obj\": {\"a\": 1}}",
                "Should fix trailing commas"
            ),
            arguments(
                "Invalid text before {\"a\": 1} and after",
                "{\"a\": 1}",
                "Should extract JSON from surrounding text"
            )
        );
    }

    @ParameterizedTest(name = "{2}")
    @MethodSource("jsonSanitizeProvider")
    @DisplayName("JsonSanitizer Parameterized Test")
    void testSanitize(String input, String expected, String description) {
        assertEquals(expected, JsonSanitizer.sanitize(input));
    }
}
