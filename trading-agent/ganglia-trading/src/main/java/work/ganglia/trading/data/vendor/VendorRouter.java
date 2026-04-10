package work.ganglia.trading.data.vendor;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Future;

import work.ganglia.trading.config.TradingConfig;
import work.ganglia.trading.config.TradingConfig.DataVendor;

/**
 * Routes data requests to the configured primary vendor with automatic fallback on rate limits.
 *
 * <p>Primary vendor is tried first. If it fails with {@link VendorRateLimitException}, the fallback
 * vendor is attempted. All other errors propagate immediately.
 */
public class VendorRouter {
  private static final Logger logger = LoggerFactory.getLogger(VendorRouter.class);

  private final DataVendor primaryVendor;
  private final DataVendor fallbackVendor;

  private final Map<Class<? extends DataVendorSpi>, Map<DataVendor, DataVendorSpi>> registry =
      new HashMap<>();

  public VendorRouter(TradingConfig config) {
    this.primaryVendor = config.dataVendor();
    this.fallbackVendor = config.fallbackVendor();
  }

  /** Register a vendor implementation for a given SPI type. */
  public void register(
      Class<? extends DataVendorSpi> spiType, DataVendor vendor, DataVendorSpi impl) {
    registry.computeIfAbsent(spiType, k -> new EnumMap<>(DataVendor.class)).put(vendor, impl);
  }

  /**
   * Route a call to the primary vendor, falling back on rate limit.
   *
   * @param spiType the SPI interface class
   * @param call function that invokes the desired method on the SPI
   * @return the result from the first successful vendor
   */
  @SuppressWarnings("unchecked")
  public <T extends DataVendorSpi> Future<String> route(
      Class<T> spiType, Function<T, Future<String>> call) {

    Map<DataVendor, DataVendorSpi> vendors = registry.get(spiType);
    if (vendors == null || vendors.isEmpty()) {
      return Future.failedFuture("No vendor registered for " + spiType.getSimpleName());
    }

    T primary = (T) vendors.get(primaryVendor);
    T fallback = (T) vendors.get(fallbackVendor);

    if (primary == null) {
      if (fallback != null) {
        return call.apply(fallback);
      }
      return Future.failedFuture("No vendor available for " + spiType.getSimpleName());
    }

    return call.apply(primary)
        .recover(
            err -> {
              if (isRateLimitError(err) && fallback != null) {
                logger.warn(
                    "Primary vendor {} rate-limited for {}, falling back to {}",
                    primaryVendor,
                    spiType.getSimpleName(),
                    fallbackVendor);
                return call.apply(fallback);
              }
              return Future.failedFuture(err);
            });
  }

  private static boolean isRateLimitError(Throwable err) {
    Throwable cause = err;
    while (cause != null) {
      if (cause instanceof VendorRateLimitException) {
        return true;
      }
      cause = cause.getCause();
    }
    return false;
  }
}
