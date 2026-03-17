package work.ganglia.port.mcp;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

public record McpCallToolResult(
    List<Content> content,
    Boolean isError
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Content(
        String type, // "text" or "image"
        String text,
        String data, // base64 encoded image data
        String mimeType
    ) {
        public static Content text(String text) {
            return new Content("text", text, null, null);
        }

        public static Content image(String data, String mimeType) {
            return new Content("image", null, data, mimeType);
        }
    }
}
