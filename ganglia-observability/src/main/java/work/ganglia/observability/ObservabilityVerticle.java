package work.ganglia.observability;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.http.ServerWebSocket;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.CorsHandler;
import io.vertx.ext.web.handler.StaticHandler;

import work.ganglia.util.Constants;

/** Verticle responsible for serving Trace Studio data and UI. */
public class ObservabilityVerticle extends AbstractVerticle {
  private static final Logger logger = LoggerFactory.getLogger(ObservabilityVerticle.class);

  private final int port;
  private final String webroot;
  private final String tracePath;
  private int actualPort;
  private final Set<ServerWebSocket> liveClients = ConcurrentHashMap.newKeySet();

  public ObservabilityVerticle(int port, String webroot) {
    this(port, webroot, ".ganglia/trace");
  }

  public ObservabilityVerticle(int port, String webroot, String tracePath) {
    this.port = port;
    this.webroot = webroot;
    this.tracePath = tracePath != null ? tracePath : ".ganglia/trace";
  }

  @Override
  public void start(Promise<Void> startPromise) {
    Router router = setupRouter();

    vertx
        .createHttpServer()
        .webSocketHandler(this::handleWebSocket)
        .requestHandler(router)
        .listen(port)
        .onSuccess(
            server -> {
              this.actualPort = server.actualPort();
              logger.info("Observability Studio started on port {}", actualPort);
              subscribeToObservations();
              startPromise.complete();
            })
        .onFailure(startPromise::fail);
  }

  private void handleWebSocket(ServerWebSocket ws) {
    if (!"/ws/traces".equals(ws.path())) {
      ws.close();
      return;
    }
    liveClients.add(ws);
    logger.debug("Trace WebSocket client connected, total: {}", liveClients.size());
    ws.closeHandler(
        v -> {
          liveClients.remove(ws);
          logger.debug("Trace WebSocket client disconnected, total: {}", liveClients.size());
        });
    ws.exceptionHandler(err -> liveClients.remove(ws));
  }

  private void subscribeToObservations() {
    vertx
        .eventBus()
        .<JsonObject>consumer(
            Constants.ADDRESS_OBSERVATIONS_ALL,
            msg -> {
              if (liveClients.isEmpty()) return;
              String payload = msg.body().encode();
              for (ServerWebSocket ws : liveClients) {
                if (!ws.writeQueueFull()) {
                  ws.writeTextMessage(payload);
                } else {
                  logger.debug("Dropping trace event for slow WebSocket client");
                }
              }
            });
  }

  private Router setupRouter() {
    Router router = Router.router(vertx);

    router
        .route()
        .handler(
            CorsHandler.create()
                .addOriginWithRegex(
                    "^https?://(localhost|127\\.0\\.0\\.1|\\[::1\\]|0\\.0\\.0\\.0)(:\\d+)?$")
                .allowedMethod(io.vertx.core.http.HttpMethod.GET)
                .allowedMethod(io.vertx.core.http.HttpMethod.POST)
                .allowedMethod(io.vertx.core.http.HttpMethod.OPTIONS)
                .allowedHeader("Content-Type")
                .allowedHeader("Accept"));

    router.route().handler(BodyHandler.create());

    // API Endpoints
    router.get("/api/traces").handler(this::handleListTraceFiles);
    router.get("/api/traces/:filename").handler(this::handleGetTraceFile);

    // Serve Static UI
    String resolvedWebroot = Files.isDirectory(Path.of(webroot)) ? webroot : "webroot";
    logger.info("Serving Observability UI from: {}", resolvedWebroot);

    // We expect trace.html to be the entry point or a dedicated build
    router.route().handler(StaticHandler.create(resolvedWebroot).setIndexPage("trace.html"));

    return router;
  }

  private void handleListTraceFiles(RoutingContext ctx) {
    vertx
        .fileSystem()
        .readDir(tracePath, ".*\\.jsonl")
        .onSuccess(
            files -> {
              JsonArray result = new JsonArray();
              for (String f : files) {
                result.add(Path.of(f).getFileName().toString());
              }
              ctx.response().putHeader("content-type", "application/json").end(result.encode());
            })
        .onFailure(
            err -> {
              ctx.response().putHeader("content-type", "application/json").end("[]");
            });
  }

  private void handleGetTraceFile(RoutingContext ctx) {
    String filename = ctx.pathParam("filename");
    if (filename == null || filename.contains("..") || !filename.endsWith(".jsonl")) {
      ctx.response().setStatusCode(400).end();
      return;
    }
    String filepath = tracePath + "/" + filename;
    vertx
        .fileSystem()
        .readFile(filepath)
        .onSuccess(
            buffer -> {
              String content = buffer.toString("UTF-8");
              JsonArray arr = new JsonArray();
              for (String line : content.split("\n")) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty()) {
                  try {
                    arr.add(new JsonObject(trimmed));
                  } catch (Exception e) {
                    logger.warn("Skipping corrupted trace line in {}: {}", filename, trimmed);
                  }
                }
              }
              ctx.response().putHeader("content-type", "application/json").end(arr.encode());
            })
        .onFailure(
            err -> {
              ctx.response().setStatusCode(404).end();
            });
  }

  public int getActualPort() {
    return actualPort;
  }
}
