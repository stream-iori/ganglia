package work.ganglia.trading.data.vendor.alphavantage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;

import work.ganglia.trading.data.vendor.VendorRateLimitException;

/** Shared HTTP client for Alpha Vantage API. Handles API key injection and rate limit detection. */
public class AlphaVantageClient {
  private static final Logger logger = LoggerFactory.getLogger(AlphaVantageClient.class);

  static final String HOST = "www.alphavantage.co";
  private static final int REQUEST_TIMEOUT_MS = 30_000;
  private static final int MAX_RETRIES = 2;
  private static final long BASE_DELAY_MS = 2000;

  private final Vertx vertx;
  private final WebClient webClient;
  private final String apiKey;

  public AlphaVantageClient(Vertx vertx, String apiKey) {
    this.vertx = vertx;
    this.apiKey = apiKey;
    this.webClient =
        WebClient.create(vertx, new WebClientOptions().setConnectTimeout(5000).setIdleTimeout(30));
  }

  AlphaVantageClient(Vertx vertx, WebClient webClient, String apiKey) {
    this.vertx = vertx;
    this.webClient = webClient;
    this.apiKey = apiKey;
  }

  /**
   * Execute a GET request to Alpha Vantage.
   *
   * @param function the API function (e.g. "TIME_SERIES_DAILY_ADJUSTED")
   * @param extraParams additional query params (without apikey)
   */
  public Future<String> query(String function, String extraParams) {
    String uri =
        "/query?function="
            + function
            + (extraParams.isEmpty() ? "" : "&" + extraParams)
            + "&apikey="
            + apiKey;
    return doGet(uri, 0);
  }

  private Future<String> doGet(String uri, int attempt) {
    return webClient
        .get(443, HOST, uri)
        .ssl(true)
        .timeout(REQUEST_TIMEOUT_MS)
        .send()
        .compose(
            resp -> {
              if (resp.statusCode() >= 400) {
                return Future.failedFuture(
                    new RuntimeException("Alpha Vantage HTTP " + resp.statusCode()));
              }
              String body = extractBody(resp);
              // Alpha Vantage signals rate limit via JSON "Information" or "Note" field
              if (isRateLimited(body)) {
                if (attempt < MAX_RETRIES) {
                  long delay = BASE_DELAY_MS * (1L << attempt);
                  logger.warn("Alpha Vantage rate limited, retry {}/{}", attempt + 1, MAX_RETRIES);
                  return vertx.timer(delay).compose(v -> doGet(uri, attempt + 1));
                }
                return Future.failedFuture(
                    new VendorRateLimitException("Alpha Vantage rate limited"));
              }
              return Future.succeededFuture(body);
            })
        .recover(
            err -> {
              if (err instanceof VendorRateLimitException) return Future.failedFuture(err);
              if (attempt < MAX_RETRIES) {
                long delay = BASE_DELAY_MS * (1L << attempt);
                logger.warn("Alpha Vantage request failed, retry: {}", err.getMessage());
                return vertx.timer(delay).compose(v -> doGet(uri, attempt + 1));
              }
              return Future.failedFuture(err);
            });
  }

  static boolean isRateLimited(String body) {
    if (body == null) return false;
    // Alpha Vantage returns rate limit info as JSON with "Information" or "Note" key
    return body.contains("\"Information\"") || body.contains("\"Note\"");
  }

  private static String extractBody(HttpResponse<Buffer> resp) {
    Buffer body = resp.body();
    return body != null ? body.toString() : "";
  }

  String apiKey() {
    return apiKey;
  }
}
