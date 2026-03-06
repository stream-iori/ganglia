package work.ganglia.core.webui;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.CorsHandler;
import io.vertx.ext.web.handler.StaticHandler;
import io.vertx.ext.web.handler.sockjs.SockJSBridgeOptions;
import io.vertx.ext.web.handler.sockjs.SockJSHandler;
import io.vertx.ext.bridge.PermittedOptions;
import io.vertx.ext.bridge.BridgeEventType;
import work.ganglia.core.loop.StandardAgentLoop;
import work.ganglia.core.session.SessionManager;
import work.ganglia.core.model.SessionContext;
import work.ganglia.core.model.AgentSignal;
import work.ganglia.core.webui.model.ClientRequest;
import work.ganglia.core.webui.model.ServerEvent;
import work.ganglia.core.webui.model.EventType;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Verticle for handling WebUI connections and EventBus bridging.
 */
public class WebUIVerticle extends AbstractVerticle {
    private static final Logger logger = LoggerFactory.getLogger(WebUIVerticle.class);

    private final int port;
    private final String webroot;
    private final StandardAgentLoop agentLoop;
    private final SessionManager sessionManager;
    private final Map<String, String> lastPrompts = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<String, List<JsonObject>> sessionHistories = new java.util.concurrent.ConcurrentHashMap<>();

    public WebUIVerticle(int port, StandardAgentLoop agentLoop, SessionManager sessionManager) {
        this(port, "webroot", agentLoop, sessionManager);
    }

    public WebUIVerticle(int port, String webroot, StandardAgentLoop agentLoop, SessionManager sessionManager) {
        this.port = port;
        this.webroot = webroot != null ? webroot : "webroot";
        this.agentLoop = agentLoop;
        this.sessionManager = sessionManager;
    }

    @Override
    public void start(Promise<Void> startPromise) {
        Router router = Router.router(vertx);

        // CORS
        router.route().handler(CorsHandler.create()
            .addOrigin("*")
            .allowedMethod(HttpMethod.GET)
            .allowedMethod(HttpMethod.POST)
            .allowedMethod(HttpMethod.OPTIONS)
            .allowedHeader("Content-Type"));

        router.route().handler(BodyHandler.create());

        // EventBus Bridge
        SockJSBridgeOptions options = new SockJSBridgeOptions()
            .addInboundPermitted(new PermittedOptions().setAddress("ganglia.ui.req"))
            .addOutboundPermitted(new PermittedOptions().setAddressRegex("ganglia\\.ui\\.stream\\..*"));

        SockJSHandler sockJSHandler = SockJSHandler.create(vertx);
        Router sockJSRouter = sockJSHandler.bridge(options, event -> {
            if (event.type() == BridgeEventType.SOCKET_CREATED) {
                logger.info("WebUI Socket created");
            }
            event.complete(true);
        });
        
        router.route("/eventbus/*").subRouter(sockJSRouter);

        // Outbound interceptor for caching history
        vertx.eventBus().<JsonObject>consumer("ganglia.ui.stream.outbound.cache", msg -> {
            JsonObject event = msg.body();
            String sessionId = msg.headers().get("sessionId");
            if (sessionId != null) {
                sessionHistories.computeIfAbsent(sessionId, k -> new java.util.ArrayList<>()).add(event);
            }
        });

        // Request Handler
        vertx.eventBus().<JsonObject>consumer("ganglia.ui.req", msg -> {
            ClientRequest request = msg.body().mapTo(ClientRequest.class);
            String sessionId = request.sessionId();
            
            switch (request.action()) {
                case "SYNC" -> {
                    List<JsonObject> history = sessionHistories.getOrDefault(sessionId, java.util.Collections.emptyList());
                    JsonObject response = new JsonObject().put("history", new io.vertx.core.json.JsonArray(history));
                    msg.reply(response);
                }
                case "LIST_FILES" -> {
                    vertx.executeBlocking(() -> {
                        try {
                            JsonObject tree = buildFileTree(Path.of("."));
                            ServerEvent event = new ServerEvent(
                                UUID.randomUUID().toString(),
                                System.currentTimeMillis(),
                                EventType.FILE_TREE,
                                tree
                            );
                            vertx.eventBus().publish("ganglia.ui.stream." + sessionId, JsonObject.mapFrom(event));
                        } catch (Exception e) {
                            logger.error("Failed to list files", e);
                        }
                        return null;
                    });
                }
                case "START" -> {
                    JsonObject startPayload = JsonObject.mapFrom(request.payload());
                    String prompt = startPayload.getString("prompt");
                    lastPrompts.put(sessionId, prompt);

                    // Publish user message to UI immediately
                    ServerEvent userEvent = new ServerEvent(
                        UUID.randomUUID().toString(),
                        System.currentTimeMillis(),
                        EventType.USER_MESSAGE,
                        new ServerEvent.UserMessageData(prompt)
                    );
                    JsonObject userJson = JsonObject.mapFrom(userEvent);
                    vertx.eventBus().publish("ganglia.ui.stream." + sessionId, userJson);
                    sessionHistories.computeIfAbsent(sessionId, k -> new java.util.ArrayList<>()).add(userJson);
                    
                    sessionManager.getSession(sessionId).onComplete(res -> {
                        if (res.succeeded()) {
                            agentLoop.run(prompt, res.result(), new AgentSignal());
                        }
                    });
                }
                case "RETRY" -> {
                    String lastPrompt = lastPrompts.get(sessionId);
                    if (lastPrompt != null) {
                        sessionManager.getSession(sessionId).onComplete(res -> {
                            if (res.succeeded() && res.result() != null) {
                                agentLoop.run(lastPrompt, res.result(), new AgentSignal());
                            }
                        });
                    }
                }
                case "RESPOND_ASK" -> {
                    JsonObject askPayload = JsonObject.mapFrom(request.payload());
                    String selectedOption = askPayload.getString("selectedOption");
                    
                    sessionManager.getSession(sessionId).onComplete(res -> {
                        if (res.succeeded() && res.result() != null) {
                            agentLoop.resume(selectedOption, res.result(), new AgentSignal());
                        }
                    });
                }
                case "CANCEL" -> {
                    agentLoop.stop(sessionId);
                }
                case "READ_FILE" -> {
                    JsonObject readPayload = JsonObject.mapFrom(request.payload());
                    String filePath = readPayload.getString("path");
                    
                    vertx.executeBlocking(() -> {
                        try {
                            Path path = Path.of(filePath);
                            if (Files.exists(path) && !Files.isDirectory(path)) {
                                String content = Files.readString(path);
                                String ext = "";
                                int i = filePath.lastIndexOf('.');
                                if (i > 0) ext = filePath.substring(i + 1);
                                
                                ServerEvent event = new ServerEvent(
                                    UUID.randomUUID().toString(),
                                    System.currentTimeMillis(),
                                    EventType.FILE_CONTENT,
                                    new ServerEvent.FileContentData(filePath, content, ext)
                                );
                                vertx.eventBus().publish("ganglia.ui.stream." + sessionId, JsonObject.mapFrom(event));
                            }
                        } catch (Exception e) {
                            logger.error("Failed to read file for WebUI: {}", filePath, e);
                        }
                        return null;
                    });
                }
            }
        });

        // Static files (for the WebUI build)
        // Check if dynamic webroot exists, otherwise fall back to classpath
        if (Files.isDirectory(Path.of(webroot))) {
            logger.info("Serving WebUI from disk: {}", Path.of(webroot).toAbsolutePath());
            router.route().handler(StaticHandler.create(webroot).setIndexPage("index.html"));
        } else {
            logger.info("Serving WebUI from classpath: {}", webroot);
            router.route().handler(StaticHandler.create(webroot).setIndexPage("index.html"));
        }

        vertx.createHttpServer()
            .requestHandler(router)
            .listen(port)
            .onComplete(res -> {
                if (res.succeeded()) {
                    logger.info("WebUI Server started on port {}", port);
                    startPromise.complete();
                } else {
                    logger.error("Failed to start WebUI Server", res.cause());
                    startPromise.fail(res.cause());
                }
            });
    }

    private JsonObject buildFileTree(Path root) throws Exception {
        JsonObject node = new JsonObject();
        String name = root.getFileName() != null ? root.getFileName().toString() : ".";
        node.put("name", name);
        node.put("path", root.toString().replace("\\", "/"));
        
        if (Files.isDirectory(root)) {
            node.put("type", "directory");
            io.vertx.core.json.JsonArray children = new io.vertx.core.json.JsonArray();
            try (var stream = Files.list(root)) {
                stream.filter(p -> !p.getFileName().toString().startsWith(".") || p.getFileName().toString().equals(".ganglia"))
                      .filter(p -> !p.getFileName().toString().equals("target"))
                      .sorted((p1, p2) -> {
                          boolean d1 = Files.isDirectory(p1);
                          boolean d2 = Files.isDirectory(p2);
                          if (d1 != d2) return d1 ? -1 : 1;
                          return p1.getFileName().compareTo(p2.getFileName());
                      })
                      .forEach(p -> {
                          try {
                              children.add(buildFileTree(p));
                          } catch (Exception e) {}
                      });
            }
            node.put("children", children);
        } else {
            node.put("type", "file");
        }
        return node;
    }
}
