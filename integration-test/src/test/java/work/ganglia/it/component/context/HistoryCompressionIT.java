package work.ganglia.it.component.context;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.Collections;

import org.junit.jupiter.api.Test;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.junit5.VertxTestContext;

import work.ganglia.it.support.MockModelIT;
import work.ganglia.port.chat.Message;
import work.ganglia.port.chat.SessionContext;
import work.ganglia.port.external.llm.ChatRequest;
import work.ganglia.port.external.llm.ModelResponse;
import work.ganglia.port.internal.state.TokenUsage;

public class HistoryCompressionIT extends MockModelIT {

  @Test
  void historyCompression_triggersOnLargeHistory(Vertx vertx, VertxTestContext testContext) {
    SessionContext context = newSession();

    for (int i = 0; i < 10; i++) {
      context = context.withNewMessage(Message.user("User message " + i));
      context =
          context.withNewMessage(
              Message.assistant("Assistant message " + i, Collections.emptyList()));
    }

    when(mockModel.chat(any(ChatRequest.class)))
        .thenReturn(
            Future.succeededFuture(
                new ModelResponse(
                    "Summarized history.", Collections.emptyList(), new TokenUsage(10, 10))));

    assertTrue(context.history().size() >= 20);
    testContext.completeNow();
  }
}
