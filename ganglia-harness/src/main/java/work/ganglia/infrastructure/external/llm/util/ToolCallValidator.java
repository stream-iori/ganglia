package work.ganglia.infrastructure.external.llm.util;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;

/** Validates tool call arguments against their JSON Schema. */
public class ToolCallValidator {
  private static final Logger logger = LoggerFactory.getLogger(ToolCallValidator.class);
  private static final ObjectMapper mapper = new ObjectMapper();
  private static final JsonSchemaFactory factory =
      JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7);

  private final Map<String, JsonSchema> schemaCache = new ConcurrentHashMap<>();

  /**
   * Validates arguments against a JSON schema string.
   *
   * @return null if valid, or a descriptive error message if invalid.
   */
  public String validate(String toolName, Map<String, Object> arguments, String schemaJson) {
    if (schemaJson == null || schemaJson.isBlank() || "{}".equals(schemaJson.trim())) {
      return null; // No schema, assume valid
    }

    try {
      JsonSchema schema = schemaCache.computeIfAbsent(schemaJson, factory::getSchema);
      JsonNode node = mapper.valueToTree(arguments);

      Set<ValidationMessage> errors = schema.validate(node);
      if (errors.isEmpty()) {
        return null;
      }

      String details =
          errors.stream().map(this::formatValidationMessage).collect(Collectors.joining("; "));

      return "Schema validation failed for tool '" + toolName + "': " + details;
    } catch (Exception e) {
      logger.warn("Failed to validate tool call for {}: {}", toolName, e.getMessage());
      return "Internal validation error: " + e.getMessage();
    }
  }

  private String formatValidationMessage(ValidationMessage vm) {
    return switch (vm.getType()) {
      case "required" -> "missing required field: " + extractMissingField(vm);
      case "type" ->
          "field '"
              + vm.getInstanceLocation()
              + "' has wrong type (expected: "
              + extractExpectedType(vm)
              + ")";
      default -> vm.getType() + " violation at '" + vm.getInstanceLocation() + "'";
    };
  }

  private String extractMissingField(ValidationMessage vm) {
    Object[] args = vm.getArguments();
    return (args != null && args.length > 0)
        ? String.valueOf(args[0])
        : vm.getInstanceLocation().toString();
  }

  private String extractExpectedType(ValidationMessage vm) {
    Object[] args = vm.getArguments();
    return (args != null && args.length > 0) ? String.valueOf(args[0]) : "unknown";
  }
}
