package work.ganglia.stubs;

import java.util.ArrayList;
import java.util.List;
import work.ganglia.port.internal.state.ExecutionContext;

public class StubExecutionContext implements ExecutionContext {
  private final String sessionId;
  private final List<String> streams = new ArrayList<>();
  private java.util.function.Consumer<String> streamConsumer;
  private Throwable error;

  public StubExecutionContext(String sessionId) {
    this.sessionId = sessionId;
  }

  public StubExecutionContext(
      String sessionId, java.util.function.Consumer<String> streamConsumer) {
    this.sessionId = sessionId;
    this.streamConsumer = streamConsumer;
  }

  public StubExecutionContext() {
    this("test-session");
  }

  @Override
  public String sessionId() {
    return sessionId;
  }

  @Override
  public void emitStream(String chunk) {
    streams.add(chunk);
    if (streamConsumer != null) {
      streamConsumer.accept(chunk);
    }
  }

  @Override
  public void emitError(Throwable error) {
    this.error = error;
  }

  public List<String> getStreams() {
    return streams;
  }

  public Throwable getError() {
    return error;
  }
}
