package work.ganglia.infrastructure.external.llm.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.params.provider.Arguments.arguments;

class ToolCallValidatorTest {

    private final ToolCallValidator validator = new ToolCallValidator();

    static Stream<Arguments> validationProvider() {
        String baseSchema = "{\"type\": \"object\", \"properties\": {\"count\": {\"type\": \"number\"}}, \"required\": [\"count\"]}";
        return Stream.of(
            arguments(baseSchema, Map.of("count", 10), null, "Should validate correct arguments"),
            arguments(baseSchema, Map.of("other", 10), "required", "Should detect missing required field"),
            arguments(baseSchema, Map.of("count", "not a number"), "string", "Should detect wrong type")
        );
    }

    @ParameterizedTest(name = "{3}")
    @MethodSource("validationProvider")
    @DisplayName("ToolCallValidator Parameterized Test")
    void testValidate(String schema, Map<String, Object> args, String expectedErrorSub, String description) {
        String error = validator.validate("test_tool", args, schema);
        if (expectedErrorSub == null) {
            assertNull(error);
        } else {
            assertNotNull(error);
            assertTrue(error.contains(expectedErrorSub), "Error should contain: " + expectedErrorSub);
        }
    }
}
