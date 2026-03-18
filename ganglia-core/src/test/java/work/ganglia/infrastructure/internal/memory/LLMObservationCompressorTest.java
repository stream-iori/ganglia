package work.ganglia.infrastructure.internal.memory;

import io.vertx.core.Future;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import work.ganglia.port.chat.Message;
import work.ganglia.port.external.llm.ModelGateway;
import work.ganglia.port.external.llm.ModelResponse;
import work.ganglia.port.internal.memory.model.CompressionContext;
import work.ganglia.port.internal.state.TokenUsage;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(VertxExtension.class)
class LLMObservationCompressorTest {

    private ModelGateway modelGateway;
    private LLMObservationCompressor compressor;

    @BeforeEach
    void setUp() {
        modelGateway = Mockito.mock(ModelGateway.class);
        compressor = new LLMObservationCompressor(modelGateway, 100); // Small threshold for testing
    }

    @Test
    void testRequiresCompression() {
        assertTrue(compressor.requiresCompression("a".repeat(101)));
        assertFalse(compressor.requiresCompression("a".repeat(50)));
    }

    @Test
    void testCompress(VertxTestContext testContext) {
        String rawOutput = "Very long output that needs compression".repeat(10);
        String compressedSummary = "Short summary";
        
        when(modelGateway.chat(any())).thenReturn(Future.succeededFuture(
            new ModelResponse(compressedSummary, Collections.emptyList(), new TokenUsage(10, 5))
        ));

        CompressionContext context = new CompressionContext("ls", "find files", 50);
        
        compressor.compress(rawOutput, context)
            .onComplete(testContext.succeeding(result -> {
                testContext.verify(() -> {
                    assertEquals(compressedSummary, result);
                    testContext.completeNow();
                });
            }));
    }

    @Test
    void testNoCompressionNeeded(VertxTestContext testContext) {
        String rawOutput = "short";
        compressor.compress(rawOutput, new CompressionContext("tool", "task", 50))
            .onComplete(testContext.succeeding(result -> {
                testContext.verify(() -> {
                    assertEquals(rawOutput, result);
                    testContext.completeNow();
                });
            }));
    }
}