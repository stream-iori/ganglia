package work.ganglia.config;

/**
 * Common configuration keys used in ganglia.
 */
public final class ConfigKeys {
    private ConfigKeys() {}

    public static final String AGENT = "agent";
    public static final String MAX_ITERATIONS = "maxIterations";
    public static final String COMPRESSION_THRESHOLD = "compressionThreshold";
    public static final String INSTRUCTION_FILE = "instructionFile";
    
    public static final String MODELS = "models";
    public static final String PRIMARY = "primary";
    public static final String UTILITY = "utility";
    public static final String CONTEXT_LIMIT = "contextLimit";
    public static final String TYPE = "type";
    public static final String API_KEY = "apiKey";
    public static final String BASE_URL = "baseUrl";
    public static final String NAME = "name";
    public static final String TEMPERATURE = "temperature";
    public static final String MAX_TOKENS = "maxTokens";
    
    public static final String OBSERVABILITY = "observability";
    public static final String ENABLED = "enabled";
    public static final String TRACE_PATH = "tracePath";
    
    public static final String WEBUI = "webui";
    public static final String PORT = "port";
    public static final String WEBROOT = "webroot";
}
