package work.ganglia.trading.data.vendor.yfinance;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;

import work.ganglia.trading.data.vendor.VendorRateLimitException;

/**
 * Shared HTTP client for Yahoo Finance API calls. Provides retry with exponential backoff and rate
 * limit detection.
 */
public class YFinanceClient {
  private static final Logger logger = LoggerFactory.getLogger(YFinanceClient.class);

  static final String CHART_HOST = "query1.finance.yahoo.com";
  static final String QUOTE_HOST = "query2.finance.yahoo.com";
  static final String SEARCH_HOST = "query2.finance.yahoo.com";

  private static final int REQUEST_TIMEOUT_MS = 30_000;
  private static final int MAX_RETRIES = 3;
  private static final long BASE_DELAY_MS = 2000;
  private static final int MAX_RESPONSE_BYTES = 5 * 1024 * 1024;

  private final Vertx vertx;
  private final WebClient webClient;

  public YFinanceClient(Vertx vertx) {
    this.vertx = vertx;
    this.webClient =
        WebClient.create(
            vertx,
            new WebClientOptions()
                .setConnectTimeout(5000)
                .setIdleTimeout(30)
                .setUserAgent("Mozilla/5.0"));
  }

  /** For testing — inject a custom WebClient. */
  YFinanceClient(Vertx vertx, WebClient webClient) {
    this.vertx = vertx;
    this.webClient = webClient;
  }

  /**
   * Execute a GET request with retry and rate limit detection.
   *
   * @return Future with the response body as string
   */
  public Future<String> get(String host, String uri) {
    return doGet(host, uri, 0);
  }

  private Future<String> doGet(String host, String uri, int attempt) {
    return webClient
        .get(443, host, uri)
        .ssl(true)
        .timeout(REQUEST_TIMEOUT_MS)
        .send()
        .compose(
            resp -> {
              if (resp.statusCode() == 429) {
                if (attempt < MAX_RETRIES) {
                  long delay = BASE_DELAY_MS * (1L << attempt);
                  logger.warn(
                      "Yahoo Finance rate limited (429), retry {}/{} after {}ms",
                      attempt + 1,
                      MAX_RETRIES,
                      delay);
                  return delay(delay).compose(v -> doGet(host, uri, attempt + 1));
                }
                return Future.failedFuture(
                    new VendorRateLimitException(
                        "Yahoo Finance rate limited after " + MAX_RETRIES + " retries"));
              }
              if (resp.statusCode() >= 400) {
                return Future.failedFuture(
                    new RuntimeException(
                        "Yahoo Finance HTTP " + resp.statusCode() + " for " + uri));
              }
              return Future.succeededFuture(extractBody(resp));
            })
        .recover(
            err -> {
              if (err instanceof VendorRateLimitException) {
                return Future.failedFuture(err);
              }
              if (attempt < MAX_RETRIES) {
                long delay = BASE_DELAY_MS * (1L << attempt);
                logger.warn(
                    "Yahoo Finance request failed, retry {}/{}: {}",
                    attempt + 1,
                    MAX_RETRIES,
                    err.getMessage());
                return delay(delay).compose(v -> doGet(host, uri, attempt + 1));
              }
              return Future.failedFuture(err);
            });
  }

  private String extractBody(HttpResponse<Buffer> resp) {
    Buffer body = resp.body();
    if (body == null) return "";
    if (body.length() > MAX_RESPONSE_BYTES) {
      return body.getString(0, MAX_RESPONSE_BYTES);
    }
    return body.toString();
  }

  private Future<Void> delay(long ms) {
    return vertx.timer(ms).mapEmpty();
  }

  WebClient webClient() {
    return webClient;
  }
}
