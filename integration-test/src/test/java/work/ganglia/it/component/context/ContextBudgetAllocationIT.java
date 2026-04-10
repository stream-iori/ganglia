package work.ganglia.it.component.context;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

import work.ganglia.BootstrapOptions;
import work.ganglia.coding.CodingAgentBuilder;
import work.ganglia.kernel.loop.AgentLoopObserver;
import work.ganglia.port.chat.SessionContext;
import work.ganglia.port.external.llm.ChatRequest;
import work.ganglia.port.external.llm.ModelGateway;
import work.ganglia.port.external.llm.ModelResponse;
import work.ganglia.port.external.tool.ObservationType;
import work.ganglia.port.internal.prompt.ContextBudget;
import work.ganglia.port.internal.state.TokenUsage;

@ExtendWith(VertxExtension.class)
class ContextBudgetAllocationIT {

  @TempDir Path tempDir;

  @Test
  void budgetEventEmittedWithCorrectFields(Vertx vertx, VertxTestContext testContext)
      throws IOException {

    List<Map<String, Object>> captured = new CopyOnWriteArrayList<>();
    AgentLoopObserver observer =
        new AgentLoopObserver() {
          @Override
          public void onObservation(
              String sessionId, ObservationType type, String content, Map<String, Object> data) {
            if (type == ObservationType.CONTEXT_BUDGET_ALLOCATED) {
              captured.add(data);
            }
          }

          @Override
          public void onUsageRecorded(String sessionId, TokenUsage usage) {}
        };

    ModelGateway mockModel = mock(ModelGateway.class);
    when(mockModel.chat(any(ChatRequest.class))).thenReturn(Future.failedFuture("disabled"));
    when(mockModel.chatStream(any(ChatRequest.class), any()))
        .thenReturn(
            Future.succeededFuture(
                new ModelResponse("Done.", Collections.emptyList(), new TokenUsage(1, 1))));

    String projectRoot = tempDir.toRealPath().toString();

    CodingAgentBuilder.bootstrap(
            vertx,
            BootstrapOptions.builder()
                .projectRoot(projectRoot)
                .modelGatewayOverride(mockModel)
                .overrideConfig(
                    new JsonObject().put("webui", new JsonObject().put("enabled", false)))
                .extraObservers(List.of(observer))
                .build())
        .compose(
            g -> {
              SessionContext ctx = g.sessionManager().createSession("budget-it-session");
              return g.agentLoop().run("Hello", ctx).map(g);
            })
        .compose(
            g -> {
              SessionContext ctx2 = g.sessionManager().createSession("budget-it-session");
              return g.agentLoop().run("Again", ctx2);
            })
        .onComplete(
            testContext.succeeding(
                ignored ->
                    testContext.verify(
                        () -> {
                          assertFalse(captured.isEmpty(), "Budget event must be emitted");
                          Map<String, Object> data = captured.get(0);

                          assertNotNull(data.get("contextLimit"));
                          assertNotNull(data.get("maxGenerationTokens"));
                          assertNotNull(data.get("systemPromptBudget"));
                          assertNotNull(data.get("historyBudget"));
                          assertNotNull(data.get("toolOutputBudget"));
                          assertNotNull(data.get("observationFallback"));
                          assertNotNull(data.get("compressionTarget"));

                          int ctxLimit = (Integer) data.get("contextLimit");
                          int maxGen = (Integer) data.get("maxGenerationTokens");
                          ContextBudget expected = ContextBudget.from(ctxLimit, maxGen);
                          assertEquals(
                              (Integer) expected.systemPrompt(), data.get("systemPromptBudget"));
                          assertEquals((Integer) expected.history(), data.get("historyBudget"));
                          assertEquals(
                              (Integer) expected.toolOutputPerMessage(),
                              data.get("toolOutputBudget"));
                          assertEquals(
                              (Integer) expected.observationFallback(),
                              data.get("observationFallback"));
                          assertEquals(
                              (Integer) expected.compressionTarget(),
                              data.get("compressionTarget"));

                          testContext.completeNow();
                        })));
  }
}
