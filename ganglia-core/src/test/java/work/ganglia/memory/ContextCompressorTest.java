package work.ganglia.memory;

import io.vertx.core.Future;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import work.ganglia.core.llm.ModelGateway;
import work.ganglia.core.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import work.ganglia.core.config.ConfigManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith({VertxExtension.class, MockitoExtension.class})
class ContextCompressorTest {

    @Mock
    ModelGateway model;
    @Mock
    ConfigManager configManager;

    @Test
    void testSummarize(VertxTestContext testContext) {
        when(configManager.getUtilityModel()).thenReturn("test-utility-model");
        when(configManager.getTemperature()).thenReturn(0.0);
        when(configManager.getMaxTokens()).thenReturn(100);

        ContextCompressor compressor = new ContextCompressor(model, configManager);
        ModelOptions options = new ModelOptions(0.0, 100, "test-model", true);
        ModelOptions summaryOptions = new ModelOptions(0.0, 100, "test-utility-model", false);

        // Mock turns
        Message msg1 = Message.user("Do task");
        Message msg2 = Message.assistant("Doing it", null);
        Turn turn = new Turn("t1", msg1, new ArrayList<>(), msg2);

        // Mock Model Response
        when(model.chat(any(), any(), eq(summaryOptions)))
                .thenReturn(Future.succeededFuture(new ModelResponse("Task completed successfully.", Collections.emptyList(), new TokenUsage(10, 5))));

        compressor.summarize(List.of(turn), options)
                .onComplete(testContext.succeeding(summary -> {
                    testContext.verify(() -> {
                        assertEquals("Task completed successfully.", summary);
                        testContext.completeNow();
                    });
                }));
    }

    @Test
    void testReflect(VertxTestContext testContext) {
        when(configManager.getUtilityModel()).thenReturn("test-utility-model");
        when(configManager.getTemperature()).thenReturn(0.0);
        when(configManager.getMaxTokens()).thenReturn(100);

        ContextCompressor compressor = new ContextCompressor(model, configManager);

        Message msg1 = Message.user("Add a feature");
        Message msg2 = Message.assistant("Added feature", null);
        Turn turn = new Turn("t1", msg1, new ArrayList<>(), msg2);

        when(model.chat(any(), any(), any()))
                .thenReturn(Future.succeededFuture(new ModelResponse("- Added feature X\n- Updated docs", Collections.emptyList(), new TokenUsage(10, 5))));

        compressor.reflect(turn)
                .onComplete(testContext.succeeding(reflection -> {
                    testContext.verify(() -> {
                        assertTrue(reflection.startsWith("-"));
                        assertTrue(reflection.contains("Added feature X"));
                        testContext.completeNow();
                    });
                }));
    }

    @Test
    void testCompress(VertxTestContext testContext) {
        when(configManager.getUtilityModel()).thenReturn("test-utility-model");
        when(configManager.getTemperature()).thenReturn(0.0);
        when(configManager.getMaxTokens()).thenReturn(100);

        ContextCompressor compressor = new ContextCompressor(model, configManager);

        Turn t1 = Turn.newTurn("t1", Message.user("Task 1"));
        Turn t2 = Turn.newTurn("t2", Message.user("Task 2"));

        when(model.chat(any(), any(), any()))
                .thenReturn(Future.succeededFuture(new ModelResponse("DENSE SUMMARY", Collections.emptyList(), new TokenUsage(20, 10))));

        compressor.compress(List.of(t1, t2))
                .onComplete(testContext.succeeding(res -> {
                    testContext.verify(() -> {
                        assertEquals("DENSE SUMMARY", res);
                        testContext.completeNow();
                    });
                }));
    }
}
