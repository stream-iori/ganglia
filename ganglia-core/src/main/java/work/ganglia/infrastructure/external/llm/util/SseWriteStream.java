package work.ganglia.infrastructure.external.llm.util;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.streams.WriteStream;

public class SseWriteStream implements WriteStream<Buffer> {

    private final Handler<Buffer> dataHandler;
    private Handler<Throwable> exceptionHandler;

    public SseWriteStream(Handler<Buffer> dataHandler) {
        this.dataHandler = dataHandler;
    }

    @Override
    public WriteStream<Buffer> exceptionHandler(Handler<Throwable> handler) {
        this.exceptionHandler = handler;
        return this;
    }

    @Override
    public Future<Void> write(Buffer data) {
        try {
            dataHandler.handle(data);
            return Future.succeededFuture();
        } catch (Exception e) {
            if (exceptionHandler != null) {
                exceptionHandler.handle(e);
            }
            return Future.failedFuture(e);
        }
    }

    public void write(Buffer data, Handler<AsyncResult<Void>> handler) {
        write(data).onComplete(handler);
    }

    @Override
    public Future<Void> end() {
        return Future.succeededFuture();
    }

    public void end(Handler<AsyncResult<Void>> handler) {
        end().onComplete(handler);
    }

    @Override
    public WriteStream<Buffer> setWriteQueueMaxSize(int maxSize) {
        return this;
    }

    @Override
    public boolean writeQueueFull() {
        return false;
    }

    @Override
    public WriteStream<Buffer> drainHandler(Handler<Void> handler) {
        return this;
    }
}
