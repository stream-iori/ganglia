package work.ganglia.infrastructure.internal.prompt;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

import work.ganglia.BaseGangliaTest;
import work.ganglia.config.ModelConfigProvider;
import work.ganglia.config.model.ModelConfig;
import work.ganglia.port.chat.SessionContext;
import work.ganglia.port.external.tool.ObservationType;
import work.ganglia.port.internal.memory.MemoryService;
import work.ganglia.port.internal.prompt.ContextBudget;
import work.ganglia.port.internal.skill.SkillRuntime;
import work.ganglia.port.internal.state.ObservationDispatcher;
import work.ganglia.util.TokenCounter;

@ExtendWith(VertxExtension.class)
class StandardPromptEngineBudgetTest extends BaseGangliaTest {

  private ModelConfigProvider modelConfig(int contextLimit, int maxTokens) {
    return new ModelConfigProvider() {
      @Override
      public ModelConfig getModelConfig(String modelKey) {
        return null;
      }

      @Override
      public String getModel() {
        return "test";
      }

      @Override
      public String getUtilityModel() {
        return "test";
      }

      @Override
      public double getTemperature() {
        return 0.7;
      }

      @Override
      public int getContextLimit() {
        return contextLimit;
      }

      @Override
      public int getMaxTokens() {
        return maxTokens;
      }

      @Override
      public boolean isStream() {
        return false;
      }

      @Override
      public boolean isUtilityStream() {
        return false;
      }

      @Override
      public String getBaseUrl() {
        return "http://localhost";
      }

      @Override
      public String getProvider() {
        return "test";
      }
    };
  }

  private StandardPromptEngine createEngine(int contextLimit, int maxTokens) {
    MemoryService memoryService = mock(MemoryService.class);
    when(memoryService.getModules()).thenReturn(Collections.emptyList());
    SkillRuntime skillRuntime = mock(SkillRuntime.class);
    when(skillRuntime.getActiveSkillsPrompt(any()))
        .thenReturn(io.vertx.core.Future.succeededFuture(""));
    when(skillRuntime.suggestSkills(any())).thenReturn(io.vertx.core.Future.succeededFuture(""));
    return new StandardPromptEngine(
        vertx,
        memoryService,
        skillRuntime,
        null,
        new TokenCounter(),
        modelConfig(contextLimit, maxTokens));
  }

  @Test
  void budgetDerivedFromModelConfig() {
    StandardPromptEngine engine = createEngine(128000, 4096);
    ContextBudget budget = engine.getBudget();
    ContextBudget expected = ContextBudget.from(128000, 4096);

    assertEquals(expected.contextLimit(), budget.contextLimit());
    assertEquals(expected.maxGenerationTokens(), budget.maxGenerationTokens());
    assertEquals(expected.systemPrompt(), budget.systemPrompt());
    assertEquals(expected.history(), budget.history());
    assertEquals(expected.toolOutputPerMessage(), budget.toolOutputPerMessage());
    assertEquals(expected.observationFallback(), budget.observationFallback());
    assertEquals(expected.compressionTarget(), budget.compressionTarget());
  }

  @Test
  void budgetDerivedFromSmallWindow() {
    StandardPromptEngine engine = createEngine(8000, 1000);
    ContextBudget budget = engine.getBudget();
    ContextBudget expected = ContextBudget.from(8000, 1000);

    assertEquals(expected.systemPrompt(), budget.systemPrompt());
    assertEquals(expected.history(), budget.history());
    assertEquals(expected.toolOutputPerMessage(), budget.toolOutputPerMessage());
  }

  @Test
  void budgetDerivedFromLargeWindow() {
    StandardPromptEngine engine = createEngine(200000, 4096);
    ContextBudget budget = engine.getBudget();
    ContextBudget expected = ContextBudget.from(200000, 4096);

    assertEquals(expected.history(), budget.history());
    assertEquals(expected.systemPrompt(), budget.systemPrompt());
  }

  @Test
  void budgetEventEmittedOnce(VertxTestContext testContext) {
    StandardPromptEngine engine = createEngine(128000, 4096);

    List<Map<String, Object>> captured = new ArrayList<>();
    ObservationDispatcher dispatcher =
        new ObservationDispatcher() {
          @Override
          public void dispatch(String sessionId, ObservationType type, String content) {}

          @Override
          public void dispatch(
              String sessionId, ObservationType type, String content, Map<String, Object> data) {
            if (type == ObservationType.CONTEXT_BUDGET_ALLOCATED) {
              captured.add(data);
            }
          }
        };
    engine.setDispatcher(dispatcher);

    SessionContext ctx = createSessionContext("budget-test-session");

    // Call prepareRequest twice
    engine
        .prepareRequest(ctx, 0)
        .compose(r -> engine.prepareRequest(ctx, 1))
        .onComplete(
            testContext.succeeding(
                result ->
                    testContext.verify(
                        () -> {
                          assertEquals(
                              1,
                              captured.size(),
                              "CONTEXT_BUDGET_ALLOCATED should be emitted exactly once per session");
                          Map<String, Object> data = captured.get(0);
                          assertEquals(128000, data.get("contextLimit"));
                          assertEquals(4096, data.get("maxGenerationTokens"));
                          assertNotNull(data.get("systemPromptBudget"));
                          assertNotNull(data.get("historyBudget"));
                          assertNotNull(data.get("toolOutputBudget"));
                          assertNotNull(data.get("observationFallback"));
                          assertNotNull(data.get("compressionTarget"));
                          testContext.completeNow();
                        })));
  }

  @Test
  void budgetEventNotEmittedWithoutDispatcher(VertxTestContext testContext) {
    StandardPromptEngine engine = createEngine(128000, 4096);
    // No dispatcher set — should not throw
    SessionContext ctx = createSessionContext();
    engine
        .prepareRequest(ctx, 0)
        .onComplete(
            testContext.succeeding(
                result ->
                    testContext.verify(
                        () -> {
                          assertNotNull(result);
                          testContext.completeNow();
                        })));
  }
}
