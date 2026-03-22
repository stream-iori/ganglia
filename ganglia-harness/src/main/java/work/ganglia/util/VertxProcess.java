package work.ganglia.util;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.streams.ReadStream;
import io.vertx.core.streams.WriteStream;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A reactive wrapper around a native {@link Process} that implements {@link ReadStream} and {@link
 * WriteStream} interfaces.
 */
public class VertxProcess {
  private static final Logger log = LoggerFactory.getLogger(VertxProcess.class);

  private final Vertx vertx;
  private final Process process;
  private final Context context;

  private final StdoutStream stdout;
  private final StderrStream stderr;
  private final StdinStream stdin;

  private final Promise<Integer> exitPromise = Promise.promise();
  private final AtomicInteger streamsToClose = new AtomicInteger(2);
  private Integer capturedExitCode;

  /** Result of a process execution. */
  public record Result(int exitCode, String output) {
    public boolean succeeded() {
      return exitCode == 0;
    }
  }

  /** Thrown when process execution fails due to timeout or size limits. */
  public static class ExecutionException extends RuntimeException {
    private final String partialOutput;

    public ExecutionException(String message, String partialOutput) {
      super(message);
      this.partialOutput = partialOutput;
    }

    public String getPartialOutput() {
      return partialOutput;
    }
  }

  private VertxProcess(Vertx vertx, Process process) {
    this.vertx = vertx;
    this.process = process;
    this.context = vertx.getOrCreateContext();

    this.stdout = new StdoutStream();
    this.stderr = new StderrStream();
    this.stdin = new StdinStream();

    ProcessTracker.track(process);
    process
        .onExit()
        .thenAccept(
            p ->
                context.runOnContext(
                    v -> {
                      capturedExitCode = p.exitValue();
                      checkCompletion();
                    }));

    stdout.endHandler(v -> checkCompletion());
    stderr.endHandler(v -> checkCompletion());
  }

  private void checkCompletion() {
    if (capturedExitCode != null && streamsToClose.get() == 0) {
      if (!exitPromise.future().isComplete()) {
        exitPromise.complete(capturedExitCode);
      }
    }
  }

  private void decrementStreams() {
    streamsToClose.decrementAndGet();
    checkCompletion();
  }

  /**
   * Spawns a new native process.
   *
   * @param vertx the Vert.x instance
   * @param pb the configured {@link ProcessBuilder}
   * @return a future that completes with the {@link VertxProcess} instance
   */
  public static Future<VertxProcess> spawn(Vertx vertx, ProcessBuilder pb) {
    Promise<VertxProcess> promise = Promise.promise();
    vertx.executeBlocking(
        () -> {
          try {
            Process process = pb.start();
            promise.complete(new VertxProcess(vertx, process));
          } catch (IOException e) {
            promise.fail(e);
          }
          return null;
        },
        false);
    return promise.future();
  }

  /**
   * Executes a command and captures its entire output.
   *
   * @param vertx the Vert.x instance
   * @param command the command and arguments
   * @param options execution options
   * @param chunkHandler optional handler for streaming output chunks
   * @return a future that completes with the captured {@link Result}
   */
  public static Future<Result> execute(
      Vertx vertx, List<String> command, ProcessOptions options, Handler<String> chunkHandler) {

    ProcessBuilder pb = new ProcessBuilder(command);
    if (options.workingDir() != null && !options.workingDir().isEmpty()) {
      pb.directory(new java.io.File(options.workingDir()));
    }
    pb.redirectErrorStream(true);

    return spawn(vertx, pb)
        .compose(
            vp -> {
              Promise<Result> promise = Promise.promise();
              Buffer outputBuffer = Buffer.buffer();
              boolean[] limitExceeded = {false};
              boolean[] finished = {false};

              vp.stdout()
                  .handler(
                      data -> {
                        if (limitExceeded[0]) {
                          return;
                        }

                        if (outputBuffer.length() + data.length() > options.maxOutputSize()) {
                          limitExceeded[0] = true;
                          int remaining = (int) (options.maxOutputSize() - outputBuffer.length());
                          if (remaining > 0) {
                            outputBuffer.appendBuffer(data.slice(0, remaining));
                          }
                          vp.destroyForcibly();
                          promise.fail(
                              new ExecutionException(
                                  "Output size limit exceeded ("
                                      + options.maxOutputSize()
                                      + " bytes)",
                                  outputBuffer.toString(StandardCharsets.UTF_8)));
                          return;
                        }

                        outputBuffer.appendBuffer(data);
                        if (chunkHandler != null) {
                          chunkHandler.handle(data.toString(StandardCharsets.UTF_8));
                        }
                      });

              long timerId =
                  vertx.setTimer(
                      options.timeoutMs(),
                      id -> {
                        if (!finished[0]) {
                          vp.destroyForcibly();
                          promise.fail(
                              new ExecutionException(
                                  "Command timed out after " + options.timeoutMs() + "ms",
                                  outputBuffer.toString(StandardCharsets.UTF_8)));
                        }
                      });

              vp.exitCode()
                  .onComplete(
                      ar -> {
                        finished[0] = true;
                        vertx.cancelTimer(timerId);

                        if (limitExceeded[0] || promise.future().isComplete()) {
                          return;
                        }

                        if (ar.succeeded()) {
                          promise.complete(
                              new Result(
                                  ar.result(), outputBuffer.toString(StandardCharsets.UTF_8)));
                        } else {
                          promise.fail(ar.cause());
                        }
                      });

              return promise.future();
            });
  }

  public static Future<Result> execute(
      Vertx vertx,
      List<String> command,
      String workingDir,
      long timeoutMs,
      long maxOutputSize,
      Handler<String> chunkHandler) {
    return execute(
        vertx, command, new ProcessOptions(workingDir, timeoutMs, maxOutputSize), chunkHandler);
  }

  public static Future<Result> execute(
      Vertx vertx,
      List<String> command,
      long timeoutMs,
      long maxOutputSize,
      Handler<String> chunkHandler) {
    return execute(vertx, command, null, timeoutMs, maxOutputSize, chunkHandler);
  }

  public static Future<Result> execute(
      Vertx vertx, List<String> command, long timeoutMs, long maxOutputSize) {
    return execute(vertx, command, null, timeoutMs, maxOutputSize, null);
  }

  public ReadStream<Buffer> stdout() {
    return stdout;
  }

  public ReadStream<Buffer> stderr() {
    return stderr;
  }

  public WriteStream<Buffer> stdin() {
    return stdin;
  }

  public Future<Integer> exitCode() {
    return exitPromise.future();
  }

  public boolean isAlive() {
    return process.isAlive();
  }

  public void destroy() {
    process.destroy();
    stdout.close();
    stderr.close();
    stdin.close();
  }

  public void destroyForcibly() {
    process.destroyForcibly();
    stdout.close();
    stderr.close();
    stdin.close();
  }

  private abstract class BaseReadStream implements ReadStream<Buffer> {
    protected final AtomicBoolean paused = new AtomicBoolean(false);
    protected final Object lock = new Object();
    protected Handler<Buffer> dataHandler;
    protected Handler<Throwable> exceptionHandler;
    protected Handler<Void> endHandler;
    protected Thread readThread;
    protected final List<Buffer> pendingBuffers = new ArrayList<>();
    protected boolean ended = false;

    protected void startReader(InputStream is, String name) {
      readThread =
          new Thread(
              () -> {
                try (InputStream stream = new BufferedInputStream(is)) {
                  byte[] buffer = new byte[8192];
                  int n;
                  while (true) {
                    synchronized (lock) {
                      while (paused.get()) {
                        lock.wait();
                      }
                    }
                    n = stream.read(buffer);
                    if (n == -1) {
                      break;
                    }

                    Buffer b = Buffer.buffer(n);
                    b.appendBytes(buffer, 0, n);

                    context.runOnContext(
                        v -> {
                          if (dataHandler != null) {
                            dataHandler.handle(b);
                          } else {
                            pendingBuffers.add(b);
                          }
                        });
                  }
                } catch (InterruptedException e) {
                  Thread.currentThread().interrupt();
                } catch (Exception e) {
                  context.runOnContext(
                      v -> {
                        if (exceptionHandler != null) {
                          exceptionHandler.handle(e);
                        }
                      });
                } finally {
                  context.runOnContext(
                      v -> {
                        ended = true;
                        if (endHandler != null) {
                          endHandler.handle(null);
                        }
                        decrementStreams();
                      });
                }
              },
              name);
      readThread.setDaemon(true);
      readThread.start();
    }

    @Override
    public ReadStream<Buffer> exceptionHandler(Handler<Throwable> handler) {
      this.exceptionHandler = handler;
      return this;
    }

    @Override
    public ReadStream<Buffer> handler(Handler<Buffer> handler) {
      this.dataHandler = handler;
      if (handler != null && !pendingBuffers.isEmpty()) {
        List<Buffer> toProcess = new ArrayList<>(pendingBuffers);
        pendingBuffers.clear();
        for (Buffer b : toProcess) {
          handler.handle(b);
        }
      }
      return this;
    }

    @Override
    public ReadStream<Buffer> pause() {
      paused.set(true);
      return this;
    }

    @Override
    public ReadStream<Buffer> resume() {
      paused.set(false);
      synchronized (lock) {
        lock.notifyAll();
      }
      return this;
    }

    @Override
    public ReadStream<Buffer> fetch(long amount) {
      return resume();
    }

    @Override
    public ReadStream<Buffer> endHandler(Handler<Void> handler) {
      this.endHandler = handler;
      if (ended && handler != null) {
        handler.handle(null);
      }
      return this;
    }

    public void close() {
      if (readThread != null) {
        readThread.interrupt();
      }
    }
  }

  private class StdoutStream extends BaseReadStream {
    StdoutStream() {
      startReader(process.getInputStream(), "vertx-process-stdout-" + process.pid());
    }
  }

  private class StderrStream extends BaseReadStream {
    StderrStream() {
      startReader(process.getErrorStream(), "vertx-process-stderr-" + process.pid());
    }
  }

  private class StdinStream implements WriteStream<Buffer> {
    private final OutputStream os = new BufferedOutputStream(process.getOutputStream());
    private Handler<Throwable> exceptionHandler;
    private Handler<Void> drainHandler;
    private int maxSize = 64 * 1024;
    private final java.util.concurrent.atomic.AtomicLong pendingSize =
        new java.util.concurrent.atomic.AtomicLong(0);

    @Override
    public WriteStream<Buffer> exceptionHandler(Handler<Throwable> handler) {
      this.exceptionHandler = handler;
      return this;
    }

    @Override
    public Future<Void> write(Buffer data) {
      byte[] bytes = data.getBytes();
      pendingSize.addAndGet(bytes.length);

      Promise<Void> promise = Promise.promise();
      vertx.executeBlocking(
          () -> {
            try {
              os.write(bytes);
              os.flush();
              long newSize = pendingSize.addAndGet(-bytes.length);
              if (newSize < maxSize / 2 && drainHandler != null) {
                context.runOnContext(v -> drainHandler.handle(null));
              }
              promise.complete();
            } catch (IOException e) {
              promise.fail(e);
              if (exceptionHandler != null) {
                context.runOnContext(v -> exceptionHandler.handle(e));
              }
            }
            return null;
          },
          false);
      return promise.future();
    }

    @Override
    public Future<Void> end() {
      Promise<Void> promise = Promise.promise();
      vertx.executeBlocking(
          () -> {
            try {
              os.close();
              promise.complete();
            } catch (IOException e) {
              promise.fail(e);
            }
            return null;
          },
          false);
      return promise.future();
    }

    @Override
    public WriteStream<Buffer> setWriteQueueMaxSize(int maxSize) {
      this.maxSize = maxSize;
      return this;
    }

    @Override
    public boolean writeQueueFull() {
      return pendingSize.get() >= maxSize;
    }

    @Override
    public WriteStream<Buffer> drainHandler(Handler<Void> handler) {
      this.drainHandler = handler;
      return this;
    }

    public void close() {
      try {
        os.close();
      } catch (IOException ignored) {
        // Ignored
      }
    }
  }
}
