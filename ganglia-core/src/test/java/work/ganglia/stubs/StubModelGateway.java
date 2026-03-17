package work.ganglia.stubs;

import io.vertx.core.Future;
import work.ganglia.port.external.llm.ChatRequest;
import work.ganglia.port.external.llm.ModelGateway;
import work.ganglia.port.external.llm.ModelResponse;
import work.ganglia.port.internal.state.ExecutionContext;

import java.util.LinkedList;
import java.util.Queue;

public class StubModelGateway implements ModelGateway {
    private final Queue<ModelResponse> responses = new LinkedList<>();

    public void addResponse(ModelResponse response) {
        responses.add(response);
    }

    @Override
    public Future<ModelResponse> chat(ChatRequest request) {
        ModelResponse response = responses.poll();
        return response != null ? Future.succeededFuture(response) : Future.failedFuture("No stub response available");
    }

    @Override
    public Future<ModelResponse> chatStream(ChatRequest request, ExecutionContext context) {
        ModelResponse response = responses.poll();
        if (response != null && response.content() != null) {
            context.emitStream(response.content());
        }
        return response != null ? Future.succeededFuture(response) : Future.failedFuture("No stub response available");
    }
}
