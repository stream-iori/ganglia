package me.stream.ganglia.core.memory;

import io.vertx.core.Future;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import me.stream.ganglia.core.llm.ModelGateway;
import me.stream.ganglia.core.model.*;
import me.stream.ganglia.memory.ContextCompressor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith({VertxExtension.class, MockitoExtension.class})
class ContextCompressorTest {

    @Mock
    ModelGateway model;

    @Test
    void testSummarize(VertxTestContext testContext) {
        ContextCompressor compressor = new ContextCompressor(model);
        ModelOptions options = new ModelOptions(0.0, 100, "test-model");

        // Mock turns
        Message msg1 = Message.user("Do task");
        Message msg2 = Message.assistant("Doing it", null);
        Turn turn = new Turn("t1", msg1, new ArrayList<>(), msg2);

        // Mock Model Response
        when(model.chat(any(), any(), eq(options)))
                .thenReturn(Future.succeededFuture(new ModelResponse("Task completed successfully.", Collections.emptyList(), new TokenUsage(10, 5))));

        compressor.summarize(List.of(turn), options)
                .onComplete(testContext.succeeding(summary -> {
                    testContext.verify(() -> {
                        assertEquals("Task completed successfully.", summary);
                        testContext.completeNow();
                    });
                }));
    }
}
