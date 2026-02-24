package me.stream.ganglia.core.llm.util;

import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class ToolCallValidatorTest {

    private final ToolCallValidator validator = new ToolCallValidator();

    @Test
    void testValidArguments() {
        String schema = "{\"type\": \"object\", \"properties\": {\"count\": {\"type\": \"number\"}}, \"required\": [\"count\"]}";
        Map<String, Object> args = Map.of("count", 10);
        assertNull(validator.validate("test_tool", args, schema));
    }

    @Test
    void testMissingRequired() {
        String schema = "{\"type\": \"object\", \"properties\": {\"count\": {\"type\": \"number\"}}, \"required\": [\"count\"]}";
        Map<String, Object> args = Map.of("other", 10);
        String error = validator.validate("test_tool", args, schema);
        assertNotNull(error);
        assertTrue(error.contains("required"));
    }

    @Test
    void testWrongType() {
        String schema = "{\"type\": \"object\", \"properties\": {\"count\": {\"type\": \"number\"}}}";
        Map<String, Object> args = Map.of("count", "not a number");
        String error = validator.validate("test_tool", args, schema);
        assertNotNull(error);
        assertTrue(error.contains("string"));
    }
}
