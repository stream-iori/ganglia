package work.ganglia.trading.data.vendor;

/**
 * Thrown when a data vendor returns a rate-limit response. Only this exception triggers fallback.
 */
public class VendorRateLimitException extends RuntimeException {
  public VendorRateLimitException(String message) {
    super(message);
  }

  public VendorRateLimitException(String message, Throwable cause) {
    super(message, cause);
  }
}
