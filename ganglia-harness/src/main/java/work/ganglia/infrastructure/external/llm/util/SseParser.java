package work.ganglia.infrastructure.external.llm.util;

import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.core.parsetools.RecordParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SseParser implements Handler<Buffer> {
  private static final Logger logger = LoggerFactory.getLogger(SseParser.class);
  private final RecordParser parser;
  private final Handler<JsonObject> eventHandler;
  private final Handler<Throwable> exceptionHandler;

  public SseParser(Handler<JsonObject> eventHandler, Handler<Throwable> exceptionHandler) {
    this.eventHandler = eventHandler;
    this.exceptionHandler = exceptionHandler;
    this.parser = RecordParser.newDelimited("\n\n", this::handleRecord);
  }

  @Override
  public void handle(Buffer buffer) {
    try {
      parser.handle(buffer);
    } catch (Exception e) {
      if (exceptionHandler != null) {
        exceptionHandler.handle(e);
      }
    }
  }

  private void handleRecord(Buffer record) {
    String chunk = record.toString("UTF-8").trim();
    if (chunk.isEmpty()) return;

    String[] lines = chunk.split("\n");
    for (String line : lines) {
      if (line.startsWith("data:")) {
        String data = line.substring(5).trim();
        if ("[DONE]".equals(data)) {
          // OpenAI stream end indicator
          continue;
        }
        try {
          JsonObject json = new JsonObject(data);
          if (eventHandler != null) {
            eventHandler.handle(json);
          }
        } catch (Exception e) {
          logger.debug("Failed to parse SSE data as JSON: {}", data, e);
        }
      }
    }
  }
}
