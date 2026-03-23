package work.ganglia.web;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.file.FileSystem;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.ServerWebSocket;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.CorsHandler;
import io.vertx.ext.web.handler.StaticHandler;
import java.io.IOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import work.ganglia.kernel.loop.AgentLoop;
import work.ganglia.port.internal.state.AgentSignal;
import work.ganglia.port.internal.state.SessionManager;
import work.ganglia.util.Constants;
import work.ganglia.web.model.EventType;
import work.ganglia.web.model.JsonRpcNotification;
import work.ganglia.web.model.JsonRpcRequest;
import work.ganglia.web.model.JsonRpcResponse;
import work.ganglia.web.model.ServerEvent;

/** Verticle for handling WebUI connections via WebSocket and JSON-RPC. */
public class WebUIVerticle extends AbstractVerticle {
  private static final Logger logger = LoggerFactory.getLogger(WebUIVerticle.class);

  private final int port;
  private final String webroot;
  private final AgentLoop agentLoop;
  private final SessionManager sessionManager;

  private final Map<String, String> lastPrompts = new ConcurrentHashMap<>();
  private final Map<String, List<JsonObject>> sessionHistories = new ConcurrentHashMap<>();
  private final Map<String, Set<ServerWebSocket>> sessionSockets = new ConcurrentHashMap<>();

  private final int mcpServersCount;

  private WatchService watchService;
  private volatile Thread watcherThread;
  private long lastNotifyTime = 0;
  private static final long DEBOUNCE_MS = 1000;

  public WebUIVerticle(
      int port, AgentLoop agentLoop, SessionManager sessionManager, int mcpServersCount) {
    this(port, "webroot", agentLoop, sessionManager, mcpServersCount);
  }

  public WebUIVerticle(
      int port,
      String webroot,
      AgentLoop agentLoop,
      SessionManager sessionManager,
      int mcpServersCount) {
    this.port = port;
    this.webroot = webroot != null ? webroot : "webroot";
    this.agentLoop = agentLoop;
    this.sessionManager = sessionManager;
    this.mcpServersCount = mcpServersCount;
  }

  @Override
  public void start(Promise<Void> startPromise) {
    Router router = setupRouter();

    vertx
        .createHttpServer()
        .requestHandler(router)
        .webSocketHandshakeHandler(this::handleWebSocketHandshake)
        .listen(port)
        .onComplete(
            res -> {
              if (res.succeeded()) {
                logger.info("WebUI Server started on port {}", port);
                startPromise.complete();
                startFileWatcher();
              } else {
                logger.error("Failed to start WebUI Server", res.cause());
                startPromise.fail(res.cause());
              }
            });

    setupEventBusConsumers();
  }

  @Override
  public void stop() {
    if (watcherThread != null) {
      watcherThread.interrupt();
    }
    if (watchService != null) {
      try {
        watchService.close();
      } catch (IOException e) {
        logger.error("Failed to close watch service", e);
      }
    }
    if (watcherThread != null) {
      try {
        watcherThread.join(2000);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }
  }

  private void startFileWatcher() {
    try {
      this.watchService = FileSystems.getDefault().newWatchService();
      Path root = Path.of(".");
      registerRecursive(root);

      watcherThread =
          new Thread(
              () -> {
                try {
                  WatchKey key;
                  while (!Thread.currentThread().isInterrupted()
                      && (key = watchService.take()) != null) {
                    for (WatchEvent<?> event : key.pollEvents()) {
                      WatchEvent.Kind<?> kind = event.kind();
                      if (kind == StandardWatchEventKinds.OVERFLOW) {
                        continue;
                      }

                      Path context = (Path) event.context();
                      Path dir = (Path) key.watchable();
                      Path fullPath = dir.resolve(context);

                      if (kind == StandardWatchEventKinds.ENTRY_CREATE
                          && Files.isDirectory(fullPath)) {
                        try {
                          registerRecursive(fullPath);
                        } catch (IOException e) {
                          logger.error("Failed to register new directory {}", fullPath, e);
                        }
                      }

                      // Debounce notification
                      long now = System.currentTimeMillis();
                      if (now - lastNotifyTime > DEBOUNCE_MS) {
                        lastNotifyTime = now;
                        vertx.runOnContext(v -> broadcastFileTree());
                      }
                    }
                    key.reset();
                  }
                } catch (ClosedWatchServiceException e) {
                  // Normal during stop
                } catch (InterruptedException e) {
                  Thread.currentThread().interrupt();
                } catch (Exception e) {
                  if (!Thread.currentThread().isInterrupted()) {
                    logger.error("Error in file watcher", e);
                  }
                }
              },
              "ganglia-file-watcher");
      watcherThread.setDaemon(true);
      watcherThread.start();
    } catch (IOException e) {
      logger.error("Failed to initialize File Watcher", e);
    }
  }

  private void registerRecursive(Path root) throws IOException {
    Files.walk(root)
        .filter(Files::isDirectory)
        .filter(
            p -> {
              String name = p.getFileName().toString();
              // FIXME: 需要考虑过滤掉 gitignore 里的文件
              return (!name.startsWith(".") || name.equals(".ganglia")) && !name.equals("target");
            })
        .forEach(
            p -> {
              try {
                p.register(
                    watchService,
                    StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_DELETE,
                    StandardWatchEventKinds.ENTRY_MODIFY);
              } catch (IOException e) {
                logger.error("Failed to register watch for {}", p, e);
              }
            });
  }

  private void broadcastFileTree() {
    buildFileTreeAsync(Path.of("."))
        .onSuccess(
            tree -> {
              sessionSockets
                  .keySet()
                  .forEach(
                      sessionId -> {
                        publishEvent(sessionId, EventType.FILE_TREE, tree);
                      });
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
                .allowedMethod(HttpMethod.GET)
                .allowedMethod(HttpMethod.POST)
                .allowedMethod(HttpMethod.OPTIONS)
                .allowedHeader("Content-Type")
                .allowedHeader("X-Requested-With")
                .allowedHeader("Authorization")
                .allowCredentials(true));

    router.route().handler(BodyHandler.create());

    String resolvedWebroot = Files.isDirectory(Path.of(webroot)) ? webroot : "webroot";
    logger.info("Serving WebUI from: {}", resolvedWebroot);
    router.route().handler(StaticHandler.create(resolvedWebroot).setIndexPage("index.html"));

    return router;
  }

  private void handleWebSocketHandshake(io.vertx.core.http.ServerWebSocketHandshake handshake) {
    if (!"/ws".equals(handshake.path())) {
      handshake.reject();
      return;
    }

    handshake.accept().onSuccess(this::setupWebSocket);
  }

  private void setupWebSocket(ServerWebSocket ws) {
    logger.info("WebUI WebSocket connected");
    String[] currentSessionId = new String[1];

    ws.textMessageHandler(
        text -> {
          try {
            JsonRpcRequest request = new JsonObject(text).mapTo(JsonRpcRequest.class);
            if (!"2.0".equals(request.jsonrpc()) || request.method() == null) {
              return;
            }

            String sessionId =
                request.params() != null ? request.params().getString("sessionId") : null;
            if (sessionId != null) {
              currentSessionId[0] = sessionId;
              sessionSockets.computeIfAbsent(sessionId, k -> new CopyOnWriteArraySet<>()).add(ws);
            }

            handleRpcRequest(request, ws, sessionId);
          } catch (Exception e) {
            logger.error("Failed to parse websocket message: {}", text, e);
          }
        });

    ws.closeHandler(
        v -> {
          if (currentSessionId[0] != null) {
            Set<ServerWebSocket> sockets = sessionSockets.get(currentSessionId[0]);
            if (sockets != null) {
              sockets.remove(ws);
              if (sockets.isEmpty()) {
                sessionSockets.remove(currentSessionId[0]);
              }
            }
          }
          logger.info("WebUI WebSocket closed");
        });
  }

  private void setupEventBusConsumers() {
    // Outbound interceptor for caching history
    vertx
        .eventBus()
        .<JsonObject>consumer(
            Constants.ADDRESS_UI_OUTBOUND_CACHE,
            msg -> {
              String sessionId = msg.headers().get("sessionId");
              if (sessionId != null) {
                sessionHistories.computeIfAbsent(sessionId, k -> new ArrayList<>()).add(msg.body());
              }
            });

    // Forward internal events to WebSocket
    vertx
        .eventBus()
        .<JsonObject>consumer(
            "ganglia.ui.ws.events",
            msg -> forwardToWebSocket(msg.headers().get("sessionId"), "server_event", msg.body()));

    // Forward TTY streams to WebSocket
    vertx
        .eventBus()
        .<JsonObject>consumer(
            "ganglia.ui.ws.tty",
            msg -> forwardToWebSocket(msg.headers().get("sessionId"), "tty_event", msg.body()));
  }

  private void handleRpcRequest(JsonRpcRequest request, ServerWebSocket ws, String sessionId) {
    if (sessionId == null) {
      return;
    }

    switch (request.method()) {
      case "SYNC" -> handleSync(request, ws, sessionId);
      case "LIST_FILES" -> handleListFiles(request, ws, sessionId);
      case "START" -> handleStart(request, ws, sessionId);
      case "RETRY" -> handleRetry(request, ws, sessionId);
      case "RESPOND_ASK" -> handleRespondAsk(request, ws, sessionId);
      case "CANCEL" -> handleCancel(request, ws, sessionId);
      case "READ_FILE" -> handleReadFile(request, ws, sessionId);
      default -> logger.warn("Unknown RPC method: {}", request.method());
    }
  }

  private void handleSync(JsonRpcRequest request, ServerWebSocket ws, String sessionId) {
    // Push Init Config first
    publishEvent(
        sessionId,
        EventType.INIT_CONFIG,
        new ServerEvent.InitConfigData(
            Path.of(".").toAbsolutePath().toString(), sessionId, mcpServersCount));

    List<JsonObject> history = sessionHistories.getOrDefault(sessionId, Collections.emptyList());
    sendRpcResponse(ws, request.id(), new JsonObject().put("history", new JsonArray(history)));
  }

  private void handleListFiles(JsonRpcRequest request, ServerWebSocket ws, String sessionId) {
    buildFileTreeAsync(Path.of("."))
        .onComplete(
            res -> {
              if (res.succeeded()) {
                publishEvent(sessionId, EventType.FILE_TREE, res.result());
                sendRpcResponse(ws, request.id(), new JsonObject().put("status", "ok"));
              } else {
                logger.error("Failed to build file tree", res.cause());
                sendRpcResponse(ws, request.id(), new JsonObject().put("status", "error"));
              }
            });
  }

  private void handleStart(JsonRpcRequest request, ServerWebSocket ws, String sessionId) {
    String prompt = request.params().getString("prompt");
    lastPrompts.put(sessionId, prompt);

    ServerEvent userEvent =
        createServerEvent(EventType.USER_MESSAGE, new ServerEvent.UserMessageData(prompt));
    JsonObject userJson = JsonObject.mapFrom(userEvent);

    forwardToWebSocket(sessionId, "server_event", userJson);
    sessionHistories.computeIfAbsent(sessionId, k -> new ArrayList<>()).add(userJson);

    sessionManager
        .getSession(sessionId)
        .onComplete(
            res -> {
              if (res.succeeded()) {
                agentLoop.run(prompt, res.result(), new AgentSignal());
              }
            });
    sendRpcResponse(ws, request.id(), new JsonObject().put("status", "started"));
  }

  private void handleRetry(JsonRpcRequest request, ServerWebSocket ws, String sessionId) {
    String lastPrompt = lastPrompts.get(sessionId);
    if (lastPrompt != null) {
      sessionManager
          .getSession(sessionId)
          .onComplete(
              res -> {
                if (res.succeeded() && res.result() != null) {
                  agentLoop.run(lastPrompt, res.result(), new AgentSignal());
                }
              });
    }
    sendRpcResponse(ws, request.id(), new JsonObject().put("status", "retrying"));
  }

  private void handleRespondAsk(JsonRpcRequest request, ServerWebSocket ws, String sessionId) {
    JsonArray answers = request.params().getJsonArray("answers");
    StringBuilder formattedAnswers = new StringBuilder();

    if (answers != null) {
      if (answers.size() == 1) {
        // Single answer: just use the raw value
        Object val = answers.getValue(0);
        if (val instanceof JsonArray ja) {
          formattedAnswers.append(
              String.join(", ", ja.getList().stream().map(Object::toString).toList()));
        } else {
          formattedAnswers.append(val != null ? val.toString() : "");
        }
      } else {
        // Multiple answers: format clearly
        for (int i = 0; i < answers.size(); i++) {
          formattedAnswers.append("Question ").append(i + 1).append(": ");
          Object val = answers.getValue(i);
          if (val instanceof JsonArray ja) {
            formattedAnswers.append(
                String.join(", ", ja.getList().stream().map(Object::toString).toList()));
          } else {
            formattedAnswers.append(val != null ? val.toString() : "");
          }
          if (i < answers.size() - 1) {
            formattedAnswers.append("\n");
          }
        }
      }
    }

    String userInput = formattedAnswers.toString();

    sessionManager
        .getSession(sessionId)
        .onComplete(
            res -> {
              if (res.succeeded() && res.result() != null) {
                agentLoop.resume(userInput, res.result(), new AgentSignal());
              }
            });
    sendRpcResponse(ws, request.id(), new JsonObject().put("status", "resumed"));
  }

  private void handleCancel(JsonRpcRequest request, ServerWebSocket ws, String sessionId) {
    agentLoop.stop(sessionId);
    sendRpcResponse(ws, request.id(), new JsonObject().put("status", "cancelled"));
  }

  private void handleReadFile(JsonRpcRequest request, ServerWebSocket ws, String sessionId) {
    String filePath = request.params().getString("path");
    vertx.executeBlocking(
        () -> {
          try {
            if ("WORKSPACE_DIFF_VIRTUAL_PATH".equals(filePath)) {
              String mockDiff =
                  "--- a/README.md\n+++ b/README.md\n@@ -1,3 +1,4 @@\n # Ganglia\n+Modified by Agent via WebUI\n";
              publishEvent(
                  sessionId,
                  EventType.FILE_CONTENT,
                  new ServerEvent.FileContentData(filePath, mockDiff, "diff"));
            } else {
              Path path = Path.of(filePath);
              if (Files.exists(path) && !Files.isDirectory(path)) {
                String content = Files.readString(path);
                String ext =
                    filePath.contains(".") ? filePath.substring(filePath.lastIndexOf('.') + 1) : "";
                publishEvent(
                    sessionId,
                    EventType.FILE_CONTENT,
                    new ServerEvent.FileContentData(filePath, content, ext));
              }
            }
          } catch (Exception e) {
            logger.error("Failed to read file: {}", filePath, e);
          }
          return null;
        });
    sendRpcResponse(ws, request.id(), new JsonObject().put("status", "reading"));
  }

  private void publishEvent(String sessionId, EventType type, Object data) {
    forwardToWebSocket(
        sessionId, "server_event", JsonObject.mapFrom(createServerEvent(type, data)));
  }

  private ServerEvent createServerEvent(EventType type, Object data) {
    return new ServerEvent(UUID.randomUUID().toString(), System.currentTimeMillis(), type, data);
  }

  private void forwardToWebSocket(String sessionId, String method, Object params) {
    if (sessionId == null) {
      return;
    }
    Set<ServerWebSocket> sockets = sessionSockets.get(sessionId);
    if (sockets != null) {
      String payload = JsonObject.mapFrom(JsonRpcNotification.create(method, params)).encode();
      sockets.forEach(
          ws -> {
            if (!ws.isClosed()) {
              ws.writeTextMessage(payload);
            }
          });
    }
  }

  private void sendRpcResponse(ServerWebSocket ws, Object id, JsonObject result) {
    if (id != null) {
      ws.writeTextMessage(JsonObject.mapFrom(JsonRpcResponse.success(id, result)).encode());
    }
  }

  private Future<JsonObject> buildFileTreeAsync(Path root) {
    String name = root.getFileName() != null ? root.getFileName().toString() : ".";
    String path = root.toString().replace("\\", "/");
    JsonObject node = new JsonObject().put("name", name).put("path", path);

    FileSystem fs = vertx.fileSystem();
    return fs.props(root.toString())
        .compose(
            props -> {
              if (props.isDirectory()) {
                node.put("type", "directory");
                return fs.readDir(root.toString())
                    .compose(
                        list -> {
                          List<Future<JsonObject>> childFutures =
                              list.stream()
                                  .map(Path::of)
                                  .filter(
                                      p -> {
                                        String fileName = p.getFileName().toString();
                                        return (!fileName.startsWith(".")
                                                || fileName.equals(".ganglia"))
                                            && !fileName.equals("target");
                                      })
                                  .map(this::buildFileTreeAsync)
                                  .collect(Collectors.toList());

                          if (childFutures.isEmpty()) {
                            node.put("children", new JsonArray());
                            return Future.succeededFuture(node);
                          }

                          return Future.all(childFutures)
                              .map(
                                  cf -> {
                                    List<JsonObject> childrenList = new ArrayList<>();
                                    for (int i = 0; i < cf.size(); i++) {
                                      childrenList.add(cf.resultAt(i));
                                    }

                                    // Sort: directories first, then alphabetically
                                    childrenList.sort(
                                        (j1, j2) -> {
                                          boolean d1 = "directory".equals(j1.getString("type"));
                                          boolean d2 = "directory".equals(j2.getString("type"));
                                          if (d1 != d2) {
                                            return d1 ? -1 : 1;
                                          }
                                          return j1.getString("name")
                                              .compareToIgnoreCase(j2.getString("name"));
                                        });

                                    node.put("children", new JsonArray(childrenList));
                                    return node;
                                  });
                        });
              } else {
                node.put("type", "file");
                return Future.succeededFuture(node);
              }
            });
  }
}
