package work.ganglia.config;

import java.util.HashMap;
import java.util.Map;

import io.vertx.core.json.JsonObject;

import work.ganglia.util.Constants;

/** Single Responsibility: Responsible for creating the default configuration structure. */
public class DefaultConfigFactory {

  public static JsonObject create() {
    // Create structured default JSON
    JsonObject primaryModel =
        new JsonObject()
            .put(ConfigKeys.NAME, "kimi-k2-0905-preview")
            .put(ConfigKeys.TEMPERATURE, 0.0)
            .put(ConfigKeys.MAX_TOKENS, 4096)
            .put(ConfigKeys.CONTEXT_LIMIT, 128000)
            .put(ConfigKeys.TYPE, "openai")
            .put(ConfigKeys.API_KEY, "")
            .put(ConfigKeys.BASE_URL, "https://api.moonshot.cn/v1")
            .put("timeout", 60000)
            .put("maxRetries", 5);

    JsonObject utilityModel =
        new JsonObject()
            .put(ConfigKeys.NAME, "moonshot-v1-8k")
            .put(ConfigKeys.TEMPERATURE, 0.0)
            .put(ConfigKeys.MAX_TOKENS, 2048)
            .put(ConfigKeys.CONTEXT_LIMIT, 128000)
            .put(ConfigKeys.TYPE, "openai")
            .put(ConfigKeys.API_KEY, "")
            .put(ConfigKeys.BASE_URL, "https://api.moonshot.cn/v1")
            .put("timeout", 60000)
            .put("maxRetries", 5);

    Map<String, Object> models = new HashMap<>();
    models.put(ConfigKeys.PRIMARY, primaryModel);
    models.put(ConfigKeys.UTILITY, utilityModel);

    return new JsonObject()
        .put(
            ConfigKeys.AGENT,
            new JsonObject()
                .put(ConfigKeys.MAX_ITERATIONS, 10)
                .put(ConfigKeys.COMPRESSION_THRESHOLD, 0.7)
                .put(ConfigKeys.INSTRUCTION_FILE, Constants.FILE_GANGLIA_MD)
                .put(ConfigKeys.TOOL_TIMEOUT, 120000))
        .put(ConfigKeys.MODELS, models)
        .put(
            ConfigKeys.OBSERVABILITY,
            new JsonObject()
                .put(ConfigKeys.ENABLED, false)
                .put(ConfigKeys.TRACE_PATH, Constants.DIR_TRACE))
        .put(
            ConfigKeys.WEBUI,
            new JsonObject()
                .put(ConfigKeys.ENABLED, true)
                .put(ConfigKeys.PORT, 8080)
                .put(ConfigKeys.WEBROOT, "webroot"));
  }
}
