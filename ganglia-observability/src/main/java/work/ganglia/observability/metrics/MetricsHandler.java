package work.ganglia.observability.metrics;

import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;

/** HTTP handler that returns a JSON snapshot of all aggregated metrics. */
public class MetricsHandler implements Handler<RoutingContext> {
  private final MetricAggregator aggregator;

  public MetricsHandler(MetricAggregator aggregator) {
    this.aggregator = aggregator;
  }

  @Override
  public void handle(RoutingContext ctx) {
    ctx.response()
        .putHeader("content-type", "application/json")
        .end(aggregator.snapshot().encode());
  }
}
