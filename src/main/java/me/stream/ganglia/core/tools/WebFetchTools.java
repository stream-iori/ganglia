package me.stream.ganglia.core.tools;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.ext.web.client.WebClient;
import me.stream.ganglia.core.model.SessionContext;
import me.stream.ganglia.core.tools.model.ToolDefinition;
import me.stream.ganglia.core.tools.model.ToolInvokeResult;
import me.stream.ganglia.core.tools.model.ToolType;

import java.util.List;
import java.util.Map;

public class WebFetchTools implements ToolSet {
    private final WebClient webClient;

    public WebFetchTools(Vertx vertx) {
        this.webClient = WebClient.create(vertx);
    }

    @Override
    public List<ToolDefinition> getDefinitions() {
        return List.of(
            new ToolDefinition("web_fetch", "Fetch content from a URL",
                """
                {
                  "type": "object",
                  "properties": {
                    "url": { "type": "string", "description": "The URL to fetch" }
                  },
                  "required": ["url"]
                }
                """,
                ToolType.BUILTIN)
        );
    }

    @Override
    public Future<ToolInvokeResult> execute(String toolName, Map<String, Object> args, SessionContext context) {
        if ("web_fetch".equals(toolName)) {
            String url = (String) args.get("url");
            return webClient.getAbs(url)
                .send()
                .map(response -> {
                    if (response.statusCode() >= 200 && response.statusCode() < 300) {
                        return ToolInvokeResult.success(response.bodyAsString());
                    } else {
                        return ToolInvokeResult.error("Failed to fetch URL: " + url + ". Status: " + response.statusCode());
                    }
                })
                .recover(err -> Future.succeededFuture(ToolInvokeResult.error("Error fetching URL: " + err.getMessage())));
        }
        return Future.succeededFuture(ToolInvokeResult.error("Unknown tool: " + toolName));
    }
}
