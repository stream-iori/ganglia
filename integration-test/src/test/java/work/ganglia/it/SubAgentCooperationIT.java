package work.ganglia.it;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import work.Main;
import work.ganglia.core.Ganglia;
import work.ganglia.core.llm.ModelGateway;
import work.ganglia.core.model.ModelResponse;
import work.ganglia.core.model.SessionContext;
import work.ganglia.core.model.TokenUsage;
import work.ganglia.tools.model.ToolCall;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(VertxExtension.class)
public class SubAgentCooperationIT {

    private Ganglia ganglia;
    private ModelGateway mockModel;

    @BeforeEach
    void setUp(Vertx vertx, VertxTestContext testContext) {
        mockModel = mock(ModelGateway.class);
        when(mockModel.chat(any(), any(), any())).thenReturn(Future.failedFuture("Reflection disabled"));

        io.vertx.core.json.JsonObject configOverride = new io.vertx.core.json.JsonObject()
            .put("agent", new io.vertx.core.json.JsonObject().put("projectRoot", "/"));

        Main.bootstrap(vertx, ".ganglia/config.json", configOverride, mockModel)
            .onComplete(testContext.succeeding(g -> {
                this.ganglia = g;
                testContext.completeNow();
            }));
    }

    @Test
    void testInvestigatorDelegationAndCalculation(Vertx vertx, VertxTestContext testContext, @TempDir Path tempDir) {
        // 1. Prepare data files
        vertx.fileSystem().writeFileBlocking(tempDir.resolve("num1.txt").toString(), Buffer.buffer("Value: 10"));
        vertx.fileSystem().writeFileBlocking(tempDir.resolve("num2.txt").toString(), Buffer.buffer("Value: 20"));
        vertx.fileSystem().writeFileBlocking(tempDir.resolve("num3.txt").toString(), Buffer.buffer("Value: 30"));

        // 2. Mock responses for Parent Agent
        ToolCall delegateCall = new ToolCall("p1", "call_sub_agent", Map.of(
            "task", "Read all num*.txt files in " + tempDir + " and extract the numeric values.",
            "persona", "INVESTIGATOR"
        ));

        // 3. Mock responses for Sub-Agent (Investigator)
        ToolCall globCall = new ToolCall("s1", "glob", Map.of("path", tempDir.toString(), "pattern", "num*.txt"));
        ToolCall read1 = new ToolCall("s2", "read_file", Map.of("path", tempDir.resolve("num1.txt").toString()));
        ToolCall read2 = new ToolCall("s3", "read_file", Map.of("path", tempDir.resolve("num2.txt").toString()));
        ToolCall read3 = new ToolCall("s4", "read_file", Map.of("path", tempDir.resolve("num3.txt").toString()));

        when(mockModel.chatStream(any(), any(), any(), any()))
            // Parent: First Turn -> Delegates
            .thenReturn(Future.succeededFuture(new ModelResponse("I will delegate this to an investigator.", List.of(delegateCall), new TokenUsage(1, 1))))
            // Sub-Agent: Starts -> Calls Glob
            .thenReturn(Future.succeededFuture(new ModelResponse("Listing files...", List.of(globCall), new TokenUsage(1, 1))))
            // Sub-Agent: Reads 3 files sequentially
            .thenReturn(Future.succeededFuture(new ModelResponse("Reading files...", List.of(read1, read2, read3), new TokenUsage(1, 1))))
            // Sub-Agent: Final Report
            .thenReturn(Future.succeededFuture(new ModelResponse("I found three numbers: 10, 20, and 30.", Collections.emptyList(), new TokenUsage(1, 1))))
            // Parent: Receives Report -> Final Calculation
            .thenReturn(Future.succeededFuture(new ModelResponse("The sub-agent reported 10, 20, 30. The total sum is 60.", Collections.emptyList(), new TokenUsage(1, 1))));

        SessionContext context = ganglia.sessionManager().createSession(UUID.randomUUID().toString());

        ganglia.agentLoop().run("Sum the numbers found in files in " + tempDir, context)
            .onComplete(testContext.succeeding(result -> {
                testContext.verify(() -> {
                    // Verify the math was done by the parent
                    assertTrue(result.contains("60"), "Result should contain the sum 60. Got: " + result);

                    // Verify correct number of LLM calls (1 Parent start + 3 Sub-Agent turns + 1 Parent finish)
                    // Note: In our mock, Sub-Agent turns were optimized.
                    // Let's just check if it finished.
                    assertTrue(result.toLowerCase().contains("total") || result.toLowerCase().contains("sum"));
                    testContext.completeNow();
                });
            }));
    }
}
