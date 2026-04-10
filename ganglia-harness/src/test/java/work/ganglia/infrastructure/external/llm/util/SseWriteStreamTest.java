package work.ganglia.infrastructure.external.llm.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import io.vertx.core.buffer.Buffer;

class SseWriteStreamTest {

  @Test
  void testWriteBufferCallsDataHandler() {
    List<Buffer> received = new ArrayList<>();
    SseWriteStream stream = new SseWriteStream(received::add);

    Buffer data = Buffer.buffer("hello SSE");
    stream.write(data);

    assertEquals(1, received.size());
    assertEquals("hello SSE", received.get(0).toString("UTF-8"));
  }

  @Test
  void testGetRawDataAccumulatesWrites() {
    SseWriteStream stream = new SseWriteStream(buf -> {});

    stream.write(Buffer.buffer("part1"));
    stream.write(Buffer.buffer("part2"));

    String raw = stream.getRawData();
    assertEquals("part1part2", raw);
  }

  @Test
  void testExceptionHandlerCalledOnError() {
    AtomicReference<Throwable> caught = new AtomicReference<>();
    SseWriteStream stream =
        new SseWriteStream(
            buf -> {
              throw new RuntimeException("handler error");
            });
    stream.exceptionHandler(caught::set);

    stream.write(Buffer.buffer("trigger error"));

    assertNotNull(caught.get());
    assertEquals("handler error", caught.get().getMessage());
  }

  @Test
  void testWriteQueueFullReturnsFalse() {
    SseWriteStream stream = new SseWriteStream(buf -> {});
    assertFalse(stream.writeQueueFull());
  }

  @Test
  void testSetWriteQueueMaxSizeReturnsThis() {
    SseWriteStream stream = new SseWriteStream(buf -> {});
    assertSame(stream, stream.setWriteQueueMaxSize(1024));
  }

  @Test
  void testDrainHandlerReturnsThis() {
    SseWriteStream stream = new SseWriteStream(buf -> {});
    assertSame(stream, stream.drainHandler(v -> {}));
  }

  @Test
  void testEndReturnsSucceeded() {
    SseWriteStream stream = new SseWriteStream(buf -> {});
    stream.end().onComplete(ar -> assertEquals(true, ar.succeeded()));
  }

  @Test
  void testWriteWithHandlerCallback() {
    List<Boolean> results = new ArrayList<>();
    SseWriteStream stream = new SseWriteStream(buf -> {});

    stream.write(Buffer.buffer("data"), ar -> results.add(ar.succeeded()));

    assertEquals(1, results.size());
    assertEquals(true, results.get(0));
  }

  @Test
  void testEndWithHandlerCallback() {
    List<Boolean> results = new ArrayList<>();
    SseWriteStream stream = new SseWriteStream(buf -> {});

    stream.end(ar -> results.add(ar.succeeded()));

    assertEquals(1, results.size());
    assertEquals(true, results.get(0));
  }

  @Test
  void testRawDataTruncatedAtMaxBufferSize() {
    SseWriteStream stream = new SseWriteStream(buf -> {});

    // Fill the raw buffer to exactly MAX_BUFFER_SIZE (16384 bytes)
    byte[] fill = new byte[16384];
    stream.write(Buffer.buffer(fill));

    // Now buffer is full — additional writes should not be appended to raw
    stream.write(Buffer.buffer("after limit"));

    String raw = stream.getRawData();
    assertEquals(16384, raw.length()); // Only first 16KB stored
  }
}
