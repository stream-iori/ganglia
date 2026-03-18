package work.ganglia.it;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import work.ganglia.port.external.llm.ModelResponse;
import work.ganglia.port.internal.state.TokenUsage;
import work.ganglia.it.harness.E2ETestHarness;
import work.ganglia.it.harness.TestScenario;
import work.ganglia.port.external.tool.ToolCall;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@ExtendWith(VertxExtension.class)
public class Scenario1KnowledgeAcquisitionE2EIT {

    private E2ETestHarness harness;
    private HttpServer httpServer;
    private int port = 8085;

    @BeforeEach
    void setUp(Vertx vertx, VertxTestContext testContext) {
        harness = new E2ETestHarness(vertx);

        httpServer = vertx.createHttpServer();
        httpServer.requestHandler(req -> {
            if ("/readme.md".equals(req.path())) {
                req.response().putHeader("content-type", "text/markdown").end("# Project Conventions\n- Use Java 17 records.");
            } else {
                req.response().setStatusCode(404).end();
            }
        });

        httpServer.listen(port)
            .compose(v -> harness.setup(new JsonObject().put("agent", new JsonObject().put("projectRoot", "."))))
            .onComplete(testContext.succeedingThenComplete());
    }

    @AfterEach
    void tearDown(Vertx vertx, VertxTestContext testContext) {
        httpServer.close().onComplete(testContext.succeedingThenComplete());
    }

    @Test
    void testWebFetchAndRemember(Vertx vertx, VertxTestContext testContext) {
        ToolCall fetchCall = new ToolCall("c1", "web_fetch", Map.of("url", "http://localhost:" + port + "/readme.md"));
        ToolCall rememberCall = new ToolCall("c2", "remember", Map.of("fact", "Convention: Use Java 17 records."));

        TestScenario scenario = new TestScenario(
            "scenario1",
            "Knowledge Acquisition",
            "Fetch the content from http://localhost:" + port + "/readme.md and remember the project conventions.",
            List.of(
                new ModelResponse("Fetching conventions...", List.of(fetchCall), new TokenUsage(1, 1)),
                new ModelResponse("Remembering fact...", List.of(rememberCall), new TokenUsage(1, 1)),
                new ModelResponse("Task complete.", Collections.emptyList(), new TokenUsage(1, 1))
            ),
            Collections.emptyList(),
            List.of(
                new TestScenario.Expectation("OUTPUT_CONTAINS", "Task complete."),
                new TestScenario.Expectation("MEMORY_CONTAINS", "Use Java 17 records.")
            )
        );

        harness.runScenario(scenario)
            .onComplete(testContext.succeeding(result -> {
                // Cleanup .ganglia/memory/MEMORY.md if it exists
                try {
                    Files.deleteIfExists(Path.of(".ganglia/memory/MEMORY.md"));
                } catch (Exception ignore) {}
                testContext.completeNow();
            }));
    }
}
