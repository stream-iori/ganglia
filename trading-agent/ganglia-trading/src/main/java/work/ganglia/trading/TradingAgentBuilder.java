package work.ganglia.trading;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import io.vertx.core.Future;
import io.vertx.core.Vertx;

import work.ganglia.BootstrapOptions;
import work.ganglia.Ganglia;
import work.ganglia.port.external.tool.ToolSet;
import work.ganglia.port.internal.prompt.ContextSource;
import work.ganglia.port.internal.prompt.DefaultContextSource;
import work.ganglia.trading.config.TradingConfig;
import work.ganglia.trading.data.cache.OhlcvCache;
import work.ganglia.trading.data.tool.FundamentalsTools;
import work.ganglia.trading.data.tool.MarketDataTools;
import work.ganglia.trading.data.tool.NewsTools;
import work.ganglia.trading.data.vendor.VendorFactory;
import work.ganglia.trading.data.vendor.VendorRouter;
import work.ganglia.trading.filter.RoleAwareToolFilter;
import work.ganglia.trading.prompt.TradingMandatesContextSource;
import work.ganglia.trading.prompt.TradingPersonaContextSource;
import work.ganglia.trading.prompt.TradingWorkflowSource;

/**
 * Specialized builder for Trading Agents (multi-agent adversarial investment system).
 *
 * <p>Wraps the core bootstrap process and injects trading-domain tools, context sources, and
 * configuration for the multi-layer perception→debate→execution→evolution pipeline.
 */
public class TradingAgentBuilder {

  private final Vertx vertx;
  private BootstrapOptions baseOptions;
  private TradingConfig tradingConfig = TradingConfig.defaults();
  private Predicate<ContextSource> contextFilter = s -> false;
  private final List<ContextSource> contextOverrides = new ArrayList<>();

  public TradingAgentBuilder(Vertx vertx) {
    this.vertx = vertx;
    this.baseOptions = BootstrapOptions.defaultOptions();
    this.contextFilter = s -> s instanceof DefaultContextSource;
  }

  public static TradingAgentBuilder create(Vertx vertx) {
    return new TradingAgentBuilder(vertx);
  }

  public TradingAgentBuilder withOptions(BootstrapOptions options) {
    this.baseOptions = options;
    return this;
  }

  public TradingAgentBuilder withTradingConfig(TradingConfig config) {
    this.tradingConfig = config;
    return this;
  }

  public TradingAgentBuilder filterContextSources(Predicate<ContextSource> filter) {
    this.contextFilter = filter;
    return this;
  }

  public TradingAgentBuilder addContextSource(ContextSource source) {
    this.contextOverrides.add(source);
    return this;
  }

  public Future<Ganglia> bootstrap() {
    // 1. Prepare Tools
    List<ToolSet> tradingToolSets = new ArrayList<>(baseOptions.extraToolSets());
    VendorRouter vendorRouter = buildVendorRouter(vertx, tradingConfig);
    tradingToolSets.add(
        new RoleAwareToolFilter(new MarketDataTools(vendorRouter), Set.of("MARKET_ANALYST")));
    tradingToolSets.add(
        new RoleAwareToolFilter(
            new FundamentalsTools(vendorRouter), Set.of("FUNDAMENTALS_ANALYST")));
    tradingToolSets.add(
        new RoleAwareToolFilter(
            new NewsTools(vendorRouter), Set.of("NEWS_ANALYST", "SOCIAL_ANALYST")));

    // 2. Prepare Context Sources
    List<ContextSource> tradingSources = new ArrayList<>(baseOptions.extraContextSources());
    tradingSources.add(new TradingWorkflowSource(tradingConfig));
    tradingSources.add(new TradingPersonaContextSource(tradingConfig));
    tradingSources.add(new TradingMandatesContextSource());
    tradingSources.removeIf(contextFilter);
    tradingSources.addAll(contextOverrides);

    // 3. Perform Bootstrap
    BootstrapOptions options =
        baseOptions.toBuilder()
            .extraToolSets(tradingToolSets)
            .extraContextSources(tradingSources)
            .build();

    return Ganglia.bootstrap(vertx, options);
  }

  public static Future<Ganglia> bootstrap(Vertx vertx, BootstrapOptions baseOptions) {
    return create(vertx).withOptions(baseOptions).bootstrap();
  }

  private static VendorRouter buildVendorRouter(Vertx vertx, TradingConfig config) {
    VendorRouter router = new VendorRouter(config);
    OhlcvCache cache = new OhlcvCache(vertx, config.dataCacheDir());

    VendorFactory.registerYFinanceVendors(router, vertx, cache);

    String avApiKey = System.getenv("ALPHA_VANTAGE_API_KEY");
    if (avApiKey != null && !avApiKey.isBlank()) {
      VendorFactory.registerAlphaVantageVendors(router, vertx, avApiKey, cache);
    }

    return router;
  }
}
