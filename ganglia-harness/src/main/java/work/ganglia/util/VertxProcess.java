package work.ganglia.util;

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
import java.util.concurrent.atomic.AtomicReference;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.streams.ReadStream;
import io.vertx.core.streams.WriteStream;

/**
 * A reactive wrapper around a native {@link Process} that implements {@link ReadStream} and {@link
 * WriteStream} interfaces.
 */
public class VertxProcess {

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
    return vertx.executeBlocking(() -> new VertxProcess(vertx, pb.start()), false);
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
              AtomicReference<Buffer> outputBuffer = new AtomicReference<>(Buffer.buffer());
              AtomicBoolean limitExceeded = new AtomicBoolean(false);
              AtomicBoolean finished = new AtomicBoolean(false);

              vp.stdout()
                  .handler(
                      data ->
                          handleStdoutData(
                              data, outputBuffer, limitExceeded, options, vp, chunkHandler));

              long timerId = scheduleTimeout(vertx, options, finished, outputBuffer, vp, promise);

              vp.exitCode()
                  .onComplete(
                      ar -> {
                        finished.set(true);
                        vertx.cancelTimer(timerId);

                        if (promise.future().isComplete()) {
                          return;
                        }

                        if (limitExceeded.get()) {
                          // Output was truncated — deliver partial result with exit code 1
                          promise.complete(
                              new Result(1, outputBuffer.get().toString(StandardCharsets.UTF_8)));
                          return;
                        }

                        if (ar.succeeded()) {
                          promise.complete(
                              new Result(
                                  ar.result(),
                                  outputBuffer.get().toString(StandardCharsets.UTF_8)));
                        } else {
                          promise.fail(ar.cause());
                        }
                      });

              return promise.future();
            });
  }

  private static void handleStdoutData(
      Buffer data,
      AtomicReference<Buffer> outputBuffer,
      AtomicBoolean limitExceeded,
      ProcessOptions options,
      VertxProcess vp,
      Handler<String> chunkHandler) {

    if (limitExceeded.get()) {
      return;
    }

    Buffer current = outputBuffer.get();
    if (current.length() + data.length() > options.maxOutputSize()) {
      limitExceeded.set(true);
      // Prepend the truncation marker so it survives any downstream
      // token-based truncation that takes a prefix of the output.
      int remaining = (int) (options.maxOutputSize() - current.length());
      String marker =
          "[OUTPUT TRUNCATED: exceeded "
              + options.maxOutputSize()
              + " bytes. Only the first portion is shown.]\n\n";
      Buffer truncated = Buffer.buffer();
      truncated.appendString(marker);
      truncated.appendBuffer(current);
      if (remaining > 0) {
        truncated.appendBuffer(data.slice(0, remaining));
      }
      outputBuffer.set(truncated);
      vp.destroyForcibly();
      // Do not fail — let the exitCode handler complete the promise normally
      return;
    }

    current.appendBuffer(data);
    if (chunkHandler != null) {
      chunkHandler.handle(data.toString(StandardCharsets.UTF_8));
    }
  }

  private static long scheduleTimeout(
      Vertx vertx,
      ProcessOptions options,
      AtomicBoolean finished,
      AtomicReference<Buffer> outputBuffer,
      VertxProcess vp,
      Promise<Result> promise) {

    return vertx.setTimer(
        options.timeoutMs(),
        id -> {
          if (!finished.get()) {
            vp.destroyForcibly();
            promise.fail(
                new ExecutionException(
                    "Command timed out after " + options.timeoutMs() + "ms",
                    outputBuffer.get().toString(StandardCharsets.UTF_8)));
          }
        });
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

                    runOnContextSafely(
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
                  runOnContextSafely(
                      v -> {
                        if (exceptionHandler != null) {
                          exceptionHandler.handle(e);
                        }
                      });
                } finally {
                  runOnContextSafely(
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

    /** Runs a task on the Vert.x context, ignoring RejectedExecutionException if context is closed. */
    private void runOnContextSafely(Handler<Void> task) {
      try {
        context.runOnContext(task);
      } catch (java.util.concurrent.RejectedExecutionException e) {
        // Context has been closed (e.g., Vert.x shutting down), ignore
      }
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
