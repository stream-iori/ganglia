package work.ganglia.coding.tool;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import work.ganglia.infrastructure.external.tool.model.ToolInvokeResult;
import work.ganglia.port.chat.SessionContext;
import work.ganglia.port.external.tool.ToolDefinition;
import work.ganglia.port.external.tool.ToolSet;
import work.ganglia.port.internal.state.ExecutionContext;

public class WebFetchTools implements ToolSet {

  private static final int CONNECT_TIMEOUT_MS = 5_000;
  private static final int IDLE_TIMEOUT_S = 30;
  private static final long REQUEST_TIMEOUT_MS = 30_000;
  private static final int MAX_RESPONSE_SIZE = 5 * 1024 * 1024; // 5MB

  private final WebClient webClient;

  public WebFetchTools(Vertx vertx) {
    WebClientOptions options =
        new WebClientOptions().setConnectTimeout(CONNECT_TIMEOUT_MS).setIdleTimeout(IDLE_TIMEOUT_S);
    this.webClient = WebClient.create(vertx, options);
  }

  @Override
  public List<ToolDefinition> getDefinitions() {
    return List.of(
        new ToolDefinition(
            "web_fetch",
            "Fetch content from a URL",
            """
                {
                  "type": "object",
                  "properties": {
                    "url": { "type": "string", "description": "The URL to fetch" }
                  },
                  "required": ["url"]
                }
                """));
  }

  @Override
  public Future<ToolInvokeResult> execute(
      String toolName,
      Map<String, Object> args,
      SessionContext context,
      ExecutionContext executionContext) {
    if ("web_fetch".equals(toolName)) {
      String url = (String) args.get("url");
      if (url == null) {
        return Future.succeededFuture(ToolInvokeResult.error("Missing required argument: url"));
      }

      String validationError = UrlValidator.validate(url);
      if (validationError != null) {
        return Future.succeededFuture(
            ToolInvokeResult.error("URL validation failed: " + validationError));
      }

      return webClient
          .getAbs(url)
          .timeout(REQUEST_TIMEOUT_MS)
          .send()
          .map(
              response -> {
                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                  String body = response.bodyAsString();
                  if (body != null && body.length() > MAX_RESPONSE_SIZE) {
                    return ToolInvokeResult.success(
                        body.substring(0, MAX_RESPONSE_SIZE)
                            + "\n\n[Response truncated: exceeded "
                            + MAX_RESPONSE_SIZE / (1024 * 1024)
                            + "MB limit]");
                  }
                  return ToolInvokeResult.success(body != null ? body : "");
                } else {
                  return ToolInvokeResult.error(
                      "Failed to fetch URL: " + url + ". Status: " + response.statusCode());
                }
              })
          .recover(
              err -> {
                if (err instanceof TimeoutException) {
                  return Future.succeededFuture(
                      ToolInvokeResult.error(
                          "Request timed out after "
                              + REQUEST_TIMEOUT_MS / 1000
                              + "s while fetching: "
                              + url));
                }
                return Future.succeededFuture(
                    ToolInvokeResult.error("Error fetching URL: " + err.getMessage()));
              });
    }
    return Future.succeededFuture(ToolInvokeResult.error("Unknown tool: " + toolName));
  }
}
