package work.ganglia.stubs;

import io.vertx.core.Future;
import work.ganglia.port.external.llm.ModelGateway;
import work.ganglia.port.chat.Message;
import work.ganglia.port.external.llm.ModelOptions;
import work.ganglia.port.external.llm.ModelResponse;
import work.ganglia.port.external.tool.ToolDefinition;

import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

public class StubModelGateway implements ModelGateway {

    private final Queue<ModelResponse> responses = new LinkedList<>();

    @Override
    public Future<ModelResponse> chat(List<Message> history, List<ToolDefinition> availableTools, ModelOptions options) {
        if (responses.isEmpty()) {
            return Future.failedFuture("No stub response available");
        }
        return Future.succeededFuture(responses.poll());
    }

    @Override
    public Future<ModelResponse> chatStream(List<Message> history, List<ToolDefinition> availableTools, ModelOptions options, String sessionId) {
        // For testing, chatStream behaves like chat but we can simulate events if needed.
        if (responses.isEmpty()) {
            return Future.failedFuture("No stub response available");
        }
        return Future.succeededFuture(responses.poll());
    }

    public void addResponse(ModelResponse response) {
        responses.offer(response);
    }

    public void addResponses(ModelResponse... responses) {
        for (ModelResponse r : responses) {
            this.responses.offer(r);
        }
    }
}
