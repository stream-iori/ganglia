package work.ganglia.it;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import work.Main;
import work.ganglia.core.Ganglia;
import work.ganglia.core.llm.ModelGateway;
.ganglia.core.model.*;
import work.ganglia.core.model.ModelResponse;
import work.ganglia.core.model.Role;
import work.ganglia.core.model.SessionContext;
import work.ganglia.core.model.TokenUsage;
import work.ganglia.tools.model.ToolCall;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(VertxExtension.class)
public class BatchFileSystemIT {

    private Ganglia ganglia;
    private ModelGateway mockModel;
    private String fileAPath;
    private String fileBPath;
    private SessionContext baseContext;

    @BeforeEach
    void setUp(Vertx vertx, @TempDir Path tempDir, VertxTestContext testContext) throws Exception {
        Path realTemp = tempDir.toRealPath();
        fileAPath = realTemp.resolve("a.txt").toString();
        fileBPath = realTemp.resolve("b.txt").toString();
        Files.writeString(Path.of(fileAPath), "Content from A");
        Files.writeString(Path.of(fileBPath), "Content from B");

        mockModel = mock(ModelGateway.class);

        ToolCall batchCall = new ToolCall("call_1", "read_files", Map.of(
            "paths", List.of(fileAPath, fileBPath)
        ));

        ModelResponse response1 = new ModelResponse("Reading both files.", List.of(batchCall), new TokenUsage(10, 10));
        ModelResponse response2 = new ModelResponse("I have read both.", Collections.emptyList(), new TokenUsage(10, 10));

        when(mockModel.chatStream(any(), any(), any(), any()))
            .thenReturn(Future.succeededFuture(response1))
            .thenReturn(Future.succeededFuture(response2));

        // Mock Config to have a very small context limit
        io.vertx.core.json.JsonObject configOverride = new io.vertx.core.json.JsonObject()
            .put("agent", new io.vertx.core.json.JsonObject()
                .put("projectRoot", realTemp.toString()));

        Main.bootstrap(vertx, ".ganglia/config.json", configOverride, mockModel)
            .onComplete(testContext.succeeding(g -> {
                this.ganglia = g;
                this.baseContext = ganglia.sessionManager().createSession(UUID.randomUUID().toString());
                testContext.completeNow();
            }));
    }

    @Test
    void testBatchReadWorkflow(Vertx vertx, VertxTestContext testContext) {
        String sessionId = baseContext.sessionId();
        ganglia.agentLoop().run("Compare file a and b", baseContext)
            .compose(res -> ganglia.sessionManager().getSession(sessionId))
            .onComplete(testContext.succeeding(resultContext -> {
                testContext.verify(() -> {
                    boolean foundBoth = resultContext.history().stream()
                        .anyMatch(m -> m.role() == Role.TOOL && m.content() != null &&
                                  m.content().contains("Content from A") &&
                                  m.content().contains("Content from B"));

                    assertTrue(foundBoth, "Combined content not found in history");
                    testContext.completeNow();
                });
            }));
    }
}
