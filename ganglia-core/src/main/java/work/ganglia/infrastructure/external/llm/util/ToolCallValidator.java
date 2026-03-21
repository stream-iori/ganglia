package work.ganglia.infrastructure.external.llm.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
      JsonSchema schema = schemaCache.computeIfAbsent(schemaJson, s -> factory.getSchema(s));
      JsonNode node = mapper.valueToTree(arguments);

      Set<ValidationMessage> errors = schema.validate(node);
      if (errors.isEmpty()) {
        return null;
      }

      String details =
          errors.stream().map(ValidationMessage::getMessage).collect(Collectors.joining("; "));

      return "Schema validation failed for tool '" + toolName + "': " + details;
    } catch (Exception e) {
      logger.warn("Failed to validate tool call for {}: {}", toolName, e.getMessage());
      return "Internal validation error: " + e.getMessage();
    }
  }
}
