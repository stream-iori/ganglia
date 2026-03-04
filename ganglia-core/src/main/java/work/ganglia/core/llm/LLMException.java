package work.ganglia.core.llm;

import java.util.Optional;

/**
 * A structured exception thrown when an error occurs during LLM interaction.
 */
public class LLMException extends RuntimeException {

    private final String errorCode;
    private final Integer httpStatusCode;
    private final String requestId;

    public LLMException(String message) {
        this(message, null, null, null, null);
    }

    public LLMException(String message, String errorCode) {
        this(message, errorCode, null, null, null);
    }

    public LLMException(String message, String errorCode, Integer httpStatusCode, String requestId, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.httpStatusCode = httpStatusCode;
        this.requestId = requestId;
    }

    /**
     * @return The specific error code from the provider (e.g., "insufficient_quota", "rate_limit_exceeded").
     */
    public Optional<String> errorCode() {
        return Optional.ofNullable(errorCode);
    }

    /**
     * @return The HTTP status code returned by the API, if available.
     */
    public Optional<Integer> httpStatusCode() {
        return Optional.ofNullable(httpStatusCode);
    }

    /**
     * @return The unique request ID from the provider for tracing purposes.
     */
    public Optional<String> requestId() {
        return Optional.ofNullable(requestId);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(super.toString());
        errorCode().ifPresent(c -> sb.append(" [Code: ").append(c).append("]"));
        httpStatusCode().ifPresent(s -> sb.append(" [HTTP: ").append(s).append("]"));
        requestId().ifPresent(r -> sb.append(" [ReqID: ").append(r).append("]"));
        return sb.toString();
    }
}
