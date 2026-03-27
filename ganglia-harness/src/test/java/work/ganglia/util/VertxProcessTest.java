package work.ganglia.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.core.Vertx;
import io.vertx.core.streams.WriteStream;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
class VertxProcessTest {

  @Test
  void testExecuteSimpleCommand(Vertx vertx, VertxTestContext testContext) {
    VertxProcess.execute(vertx, List.of("echo", "hello"), 5000, 10000)
        .onComplete(
            testContext.succeeding(
                result -> {
                  testContext.verify(
                      () -> {
                        assertEquals(0, result.exitCode());
                        assertTrue(result.succeeded());
                        assertTrue(result.output().contains("hello"));
                        testContext.completeNow();
                      });
                }));
  }

  @Test
  void testExecuteWithWorkingDir(Vertx vertx, VertxTestContext testContext) {
    String workingDir = System.getProperty("user.dir");
    VertxProcess.execute(vertx, List.of("pwd"), workingDir, 5000, 10000, null)
        .onComplete(
            testContext.succeeding(
                result -> {
                  testContext.verify(
                      () -> {
                        assertEquals(0, result.exitCode());
                        testContext.completeNow();
                      });
                }));
  }

  @Test
  void testExecuteWithChunkHandler(Vertx vertx, VertxTestContext testContext) {
    AtomicBoolean chunkReceived = new AtomicBoolean(false);

    VertxProcess.execute(
            vertx,
            List.of("echo", "streaming output"),
            5000,
            10000,
            chunk -> chunkReceived.set(true))
        .onComplete(
            testContext.succeeding(
                result -> {
                  testContext.verify(
                      () -> {
                        assertTrue(chunkReceived.get(), "Should have received chunks");
                        testContext.completeNow();
                      });
                }));
  }

  @Test
  void testExecuteNonZeroExitCode(Vertx vertx, VertxTestContext testContext) {
    VertxProcess.execute(vertx, List.of("sh", "-c", "exit 1"), 5000, 10000)
        .onComplete(
            testContext.succeeding(
                result -> {
                  testContext.verify(
                      () -> {
                        assertEquals(1, result.exitCode());
                        assertFalse(result.succeeded());
                        testContext.completeNow();
                      });
                }));
  }

  @Test
  void testExecuteTimeout(Vertx vertx, VertxTestContext testContext) {
    // Very short timeout on a slow command
    VertxProcess.execute(vertx, List.of("sleep", "10"), 100, 10000)
        .onComplete(
            testContext.failing(
                err -> {
                  testContext.verify(
                      () -> {
                        assertTrue(
                            err instanceof VertxProcess.ExecutionException,
                            "Should be ExecutionException on timeout");
                        assertTrue(err.getMessage().contains("timed out"));
                        testContext.completeNow();
                      });
                }));
  }

  @Test
  void testResultRecord() {
    VertxProcess.Result result = new VertxProcess.Result(0, "output");
    assertEquals(0, result.exitCode());
    assertEquals("output", result.output());
    assertTrue(result.succeeded());

    VertxProcess.Result failResult = new VertxProcess.Result(1, "error");
    assertFalse(failResult.succeeded());
  }

  @Test
  void testExecutionException() {
    VertxProcess.ExecutionException ex = new VertxProcess.ExecutionException("timeout", "partial");
    assertEquals("timeout", ex.getMessage());
    assertEquals("partial", ex.getPartialOutput());
  }

  @Test
  void testSpawnAndExitCode(Vertx vertx, VertxTestContext testContext) {
    ProcessBuilder pb = new ProcessBuilder("echo", "spawned");
    VertxProcess.spawn(vertx, pb)
        .compose(vp -> vp.exitCode())
        .onComplete(
            testContext.succeeding(
                code -> {
                  testContext.verify(
                      () -> {
                        assertNotNull(code);
                        assertEquals(0, code);
                        testContext.completeNow();
                      });
                }));
  }

  @Test
  void testProcessOptionsWrapper(Vertx vertx, VertxTestContext testContext) {
    ProcessOptions options = new ProcessOptions(null, 5000, 10000);
    VertxProcess.execute(vertx, List.of("echo", "opts"), options, null)
        .onComplete(
            testContext.succeeding(
                result -> {
                  testContext.verify(
                      () -> {
                        assertTrue(result.output().contains("opts"));
                        testContext.completeNow();
                      });
                }));
  }

  @Test
  void testSpawnAndStdin(Vertx vertx, VertxTestContext testContext) {
    // cat reads stdin and echoes to stdout
    ProcessBuilder pb = new ProcessBuilder("cat");
    VertxProcess.spawn(vertx, pb)
        .onComplete(
            testContext.succeeding(
                vp -> {
                  testContext.verify(
                      () -> {
                        // Write to stdin and close
                        WriteStream<io.vertx.core.buffer.Buffer> stdin = vp.stdin();
                        assertNotNull(stdin);
                        stdin.write(io.vertx.core.buffer.Buffer.buffer("hello\n"));
                        vp.destroy();
                        testContext.completeNow();
                      });
                }));
  }

  @Test
  void testSpawnStdoutAndStderr(Vertx vertx, VertxTestContext testContext) {
    ProcessBuilder pb = new ProcessBuilder("echo", "stdout-output");
    VertxProcess.spawn(vertx, pb)
        .onComplete(
            testContext.succeeding(
                vp -> {
                  testContext.verify(
                      () -> {
                        assertNotNull(vp.stdout());
                        assertNotNull(vp.stderr());
                        testContext.completeNow();
                      });
                }));
  }

  @Test
  void testSpawnIsAliveAndDestroy(Vertx vertx, VertxTestContext testContext) {
    ProcessBuilder pb = new ProcessBuilder("sleep", "30");
    VertxProcess.spawn(vertx, pb)
        .onComplete(
            testContext.succeeding(
                vp -> {
                  testContext.verify(
                      () -> {
                        assertTrue(vp.isAlive());
                        vp.destroyForcibly();
                        testContext.completeNow();
                      });
                }));
  }

  @Test
  void testProcessOptionsConstructor() {
    ProcessOptions opts = new ProcessOptions("/tmp", 1000, 2000);
    assertEquals("/tmp", opts.workingDir());
    assertEquals(1000, opts.timeoutMs());
    assertEquals(2000, opts.maxOutputSize());
  }

  @Test
  void testProcessOptionsDefaults() {
    ProcessOptions opts = ProcessOptions.defaults();
    assertNull(opts.workingDir());
    assertEquals(60000, opts.timeoutMs());
    assertTrue(opts.maxOutputSize() > 0);
  }

  @Test
  void testProcessOptionsWithWorkingDir() {
    ProcessOptions base = ProcessOptions.defaults();
    ProcessOptions updated = base.withWorkingDir("/home/user");
    assertEquals("/home/user", updated.workingDir());
    assertEquals(base.timeoutMs(), updated.timeoutMs());
    assertEquals(base.maxOutputSize(), updated.maxOutputSize());
  }

  @Test
  void testProcessOptionsWithTimeout() {
    ProcessOptions base = ProcessOptions.defaults();
    ProcessOptions updated = base.withTimeout(5000);
    assertEquals(5000, updated.timeoutMs());
    assertEquals(base.workingDir(), updated.workingDir());
    assertEquals(base.maxOutputSize(), updated.maxOutputSize());
  }

  @Test
  void testProcessOptionsWithMaxOutputSize() {
    ProcessOptions base = ProcessOptions.defaults();
    ProcessOptions updated = base.withMaxOutputSize(512);
    assertEquals(512, updated.maxOutputSize());
    assertEquals(base.workingDir(), updated.workingDir());
    assertEquals(base.timeoutMs(), updated.timeoutMs());
  }
}
