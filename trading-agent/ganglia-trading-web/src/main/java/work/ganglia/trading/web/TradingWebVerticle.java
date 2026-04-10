package work.ganglia.trading.web;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.ServerWebSocket;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.CorsHandler;
import io.vertx.ext.web.handler.StaticHandler;

import work.ganglia.port.internal.state.Blackboard;
import work.ganglia.trading.config.TradingConfig;
import work.ganglia.trading.pipeline.TradingPipelineOrchestrator;
import work.ganglia.util.Constants;

/** Verticle for the Trading Agent WebUI — HTTP REST + WebSocket endpoints. */
public class TradingWebVerticle extends AbstractVerticle {
  private static final Logger logger = LoggerFactory.getLogger(TradingWebVerticle.class);

  private final int port;
  private final String webroot;
  private final TradingPipelineOrchestrator orchestrator;
  private final Blackboard blackboard;
  private final TradingSessionManager sessions = new TradingSessionManager();

  private TradingRpcHandler rpcHandler;
  private TradingWebSocketHandler wsHandler;
  private int actualPort;

  public TradingWebVerticle(
      int port,
      String webroot,
      TradingPipelineOrchestrator orchestrator,
      TradingConfig tradingConfig,
      Blackboard blackboard) {
    this.port = port;
    this.webroot = webroot != null ? webroot : "webroot";
    this.orchestrator = orchestrator;
    this.blackboard = blackboard;

    this.wsHandler = new TradingWebSocketHandler(sessions);
    this.rpcHandler =
        new TradingRpcHandler(orchestrator, blackboard, sessions, wsHandler, tradingConfig);
    this.wsHandler.setRpcHandler(rpcHandler);
  }

  public int getActualPort() {
    return actualPort;
  }

  @Override
  public void start(Promise<Void> startPromise) {
    Router router = setupRouter();

    vertx
        .createHttpServer()
        .requestHandler(router)
        .webSocketHandshakeHandler(wsHandler::handleHandshake)
        .listen(port)
        .onComplete(
            res -> {
              if (res.succeeded()) {
                actualPort = res.result().actualPort();
                logger.info("Trading WebUI Server started on port {}", actualPort);
                startPromise.complete();
              } else {
                logger.error("Failed to start Trading WebUI Server", res.cause());
                startPromise.fail(res.cause());
              }
            });

    setupEventBusConsumers();
  }

  private Router setupRouter() {
    Router router = Router.router(vertx);

    router
        .route()
        .handler(
            CorsHandler.create()
                .addOriginWithRegex(
                    "^https?://(localhost|127\\.0\\.0\\.1|\\[::1\\]|0\\.0\\.0\\.0)(:\\d+)?$")
                .allowedMethod(HttpMethod.GET)
                .allowedMethod(HttpMethod.POST)
                .allowedMethod(HttpMethod.PUT)
                .allowedMethod(HttpMethod.OPTIONS)
                .allowedHeader("Content-Type")
                .allowCredentials(true));

    router.route().handler(BodyHandler.create());

    // REST API routes
    router.get("/api/config").handler(this::handleGetConfig);
    router.put("/api/config").handler(this::handleUpdateConfig);
    router.post("/api/pipeline/run").handler(this::handleRunPipeline);
    router.get("/api/signals").handler(this::handleGetSignals);
    router.get("/api/memory/:factId").handler(this::handleGetFactDetail);
    router.get("/api/memory").handler(this::handleGetMemory);

    // Static files
    String resolvedWebroot = Files.isDirectory(Path.of(webroot)) ? webroot : "webroot";
    router.route().handler(StaticHandler.create(resolvedWebroot).setIndexPage("index.html"));

    return router;
  }

  // --- REST Handlers ---

  private void handleGetConfig(RoutingContext ctx) {
    ctx.response()
        .putHeader("content-type", "application/json")
        .end(JsonObject.mapFrom(rpcHandler.getConfig()).encode());
  }

  private void handleUpdateConfig(RoutingContext ctx) {
    try {
      TradingConfig newConfig = ctx.body().asJsonObject().mapTo(TradingConfig.class);
      rpcHandler.updateConfig(newConfig);
      ctx.response()
          .putHeader("content-type", "application/json")
          .end(JsonObject.mapFrom(newConfig).encode());
    } catch (Exception e) {
      ctx.response().setStatusCode(400).end(new JsonObject().put("error", e.getMessage()).encode());
    }
  }

  private void handleRunPipeline(RoutingContext ctx) {
    String ticker = ctx.body().asJsonObject().getString("ticker", "AAPL");
    ctx.response()
        .putHeader("content-type", "application/json")
        .end(new JsonObject().put("status", "started").put("ticker", ticker).encode());
  }

  private void handleGetSignals(RoutingContext ctx) {
    JsonArray arr = new JsonArray();
    synchronized (sessions.getSignalHistory()) {
      sessions.getSignalHistory().forEach(s -> arr.add(JsonObject.mapFrom(s)));
    }
    ctx.response().putHeader("content-type", "application/json").end(arr.encode());
  }

  private void handleGetMemory(RoutingContext ctx) {
    String role = ctx.queryParams().get("role");

    Future<? extends List<?>> factsFuture;
    if (role != null && !role.isEmpty()) {
      factsFuture = blackboard.getActiveFacts(Map.of("role", role));
    } else {
      factsFuture = blackboard.getActiveFacts();
    }

    factsFuture
        .onSuccess(
            facts -> {
              JsonArray arr = new JsonArray();
              facts.forEach(f -> arr.add(JsonObject.mapFrom(f)));
              ctx.response().putHeader("content-type", "application/json").end(arr.encode());
            })
        .onFailure(
            err -> {
              ctx.response()
                  .putHeader("content-type", "application/json")
                  .end(new JsonArray().encode());
            });
  }

  private void handleGetFactDetail(RoutingContext ctx) {
    String factId = ctx.pathParam("factId");
    blackboard
        .getFactDetail(factId)
        .onSuccess(
            detail -> {
              if (detail == null) {
                ctx.response().setStatusCode(404).end();
              } else {
                ctx.response()
                    .putHeader("content-type", "application/json")
                    .end(new JsonObject().put("factId", factId).put("detail", detail).encode());
              }
            })
        .onFailure(
            err -> {
              ctx.response().setStatusCode(404).end();
            });
  }

  // --- EventBus Consumers ---

  private void setupEventBusConsumers() {
    vertx
        .eventBus()
        .<JsonObject>consumer(
            TradingEventPublisher.ADDRESS_CACHE,
            msg -> {
              String sessionId = msg.headers().get("sessionId");
              if (sessionId != null) {
                sessions.cacheEvent(sessionId, msg.body());
              }
            });

    vertx
        .eventBus()
        .<JsonObject>consumer(
            TradingEventPublisher.ADDRESS_EVENTS,
            msg ->
                wsHandler.forwardToWebSocket(
                    msg.headers().get("sessionId"), "server_event", msg.body()));

    vertx
        .eventBus()
        .<JsonObject>consumer(
            Constants.ADDRESS_OBSERVATIONS_ALL,
            msg -> {
              Set<ServerWebSocket> clients = sessions.getTraceClients();
              if (clients.isEmpty()) return;
              String payload = msg.body().encode();
              for (ServerWebSocket client : clients) {
                if (!client.writeQueueFull()) {
                  client.writeTextMessage(payload);
                }
              }
            });
  }
}
