package work.ganglia.trading.data.tool;

import java.util.Map;
import java.util.function.Function;

import io.vertx.core.Future;

import work.ganglia.port.external.tool.ToolSet;
import work.ganglia.port.external.tool.model.ToolInvokeResult;
import work.ganglia.trading.data.vendor.DataVendorSpi;
import work.ganglia.trading.data.vendor.VendorRouter;

/**
 * Base class for vendor-backed ToolSets. Provides common routing, argument extraction, and error
 * recovery logic.
 */
public abstract class AbstractVendorToolSet implements ToolSet {

  protected final VendorRouter vendorRouter;

  protected AbstractVendorToolSet(VendorRouter vendorRouter) {
    this.vendorRouter = vendorRouter;
  }

  /**
   * Route a call through the vendor router, wrapping the result as a ToolInvokeResult. On success,
   * maps to {@link ToolInvokeResult#success}. On failure, recovers to {@link
   * ToolInvokeResult#error}.
   */
  protected <T extends DataVendorSpi> Future<ToolInvokeResult> routeToVendor(
      Class<T> spiType, Function<T, Future<String>> call) {
    return vendorRouter
        .route(spiType, call)
        .map(ToolInvokeResult::success)
        .recover(err -> Future.succeededFuture(ToolInvokeResult.error(err.getMessage())));
  }

  /** Extract a required string argument, returning null if missing. */
  public static String requireString(Map<String, Object> args, String key) {
    return (String) args.get(key);
  }

  /** Extract an optional integer argument with a default value. */
  public static int optionalInt(Map<String, Object> args, String key, int defaultValue) {
    return args.containsKey(key) ? ((Number) args.get(key)).intValue() : defaultValue;
  }

  /** Return a missing-arguments error result. */
  protected static Future<ToolInvokeResult> missingArgs(String... required) {
    return Future.succeededFuture(
        ToolInvokeResult.error("Missing required arguments: " + String.join(", ", required)));
  }
}
