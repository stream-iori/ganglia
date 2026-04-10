package work.ganglia.infrastructure.external.llm.util;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ToolCallValidatorTest {

  private final ToolCallValidator validator = new ToolCallValidator();

  static Stream<Arguments> validationProvider() {
    String baseSchema =
        "{\"type\":\"object\",\"properties\":{\"count\":{\"type\":\"number\"}},\"required\":[\"count\"]}";
    return Stream.of(
        arguments(baseSchema, Map.of("count", 10), null, null, "Should validate correct arguments"),
        arguments(
            baseSchema,
            Map.of("other", 10),
            "missing required field",
            "count",
            "Should detect missing required field"),
        arguments(
            baseSchema,
            Map.of("count", "not a number"),
            "wrong type",
            null,
            "Should detect wrong type"),
        arguments(null, Map.of("x", 1), null, null, "null schema should pass"),
        arguments("{}", Map.of("x", 1), null, null, "empty schema should pass"),
        arguments("  ", Map.of("x", 1), null, null, "blank schema should pass"));
  }

  @ParameterizedTest(name = "{4}")
  @MethodSource("validationProvider")
  @DisplayName("ToolCallValidator Parameterized Test")
  void testValidate(
      String schema,
      Map<String, Object> args,
      String expectedPhrase,
      String expectedField,
      String description) {
    String error = validator.validate("test_tool", args, schema);
    if (expectedPhrase == null) {
      assertNull(error);
    } else {
      assertNotNull(error, "Expected a validation error but got null");
      assertTrue(
          error.contains(expectedPhrase),
          "Error should contain '" + expectedPhrase + "', actual: " + error);
      if (expectedField != null) {
        assertTrue(
            error.contains(expectedField),
            "Error should mention field '" + expectedField + "', actual: " + error);
      }
    }
  }
}
