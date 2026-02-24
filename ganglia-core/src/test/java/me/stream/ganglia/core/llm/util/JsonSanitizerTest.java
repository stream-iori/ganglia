package me.stream.ganglia.core.llm.util;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class JsonSanitizerTest {

    @Test
    void testMarkdownStrip() {
        String input = """
                Here is the result:
                ```json
                {"key": "value"}
                ```
                Hope it helps!
                """;
        assertEquals("{\"key\": \"value\"}", JsonSanitizer.sanitize(input));
    }

    @Test
    void testTrailingCommaFix() {
        String input = "{\"list\": [1, 2, 3, ], \"obj\": {\"a\": 1,}}";
        assertEquals("{\"list\": [1, 2, 3], \"obj\": {\"a\": 1}}", JsonSanitizer.sanitize(input));
    }

    @Test
    void testExtractionFromText() {
        String input = "Invalid text before {\"a\": 1} and after";
        assertEquals("{\"a\": 1}", JsonSanitizer.sanitize(input));
    }
}
