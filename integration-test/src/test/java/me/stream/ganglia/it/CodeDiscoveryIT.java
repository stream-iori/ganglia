package me.stream.ganglia.it;

import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import me.stream.Main;
import me.stream.ganglia.core.Ganglia;
import me.stream.ganglia.core.model.SessionContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(VertxExtension.class)
public class CodeDiscoveryIT {

    private Ganglia ganglia;

    @BeforeEach
    void setUp(Vertx vertx, VertxTestContext testContext) {
        Main.bootstrap(vertx)
            .onComplete(testContext.succeeding(g -> {
                this.ganglia = g;
                testContext.completeNow();
            }));
    }

    @Test
    void testFullCodeDiscoveryWorkflow(Vertx vertx, VertxTestContext testContext, @TempDir Path tempDir) {
        String sessionId = UUID.randomUUID().toString();
        String testFile = tempDir.resolve("discovery_test.java").toString();
        String content = """
            public class DiscoveryTest { 
                public void hello() { 
                    System.out.println("SECRET_CODE_123"); 
                } 
            }
            """;

        // 1. Create a file using write_file
        String input1 = String.format("Write the following content to '%s': %s", testFile, content);
        
        ganglia.sessionManager().getSession(sessionId)
            .compose(context -> ganglia.agentLoop().run(input1, context))
            .compose(res1 -> {
                testContext.verify(() -> {
                    assertTrue(res1.toLowerCase().contains("success") || res1.toLowerCase().contains("written"));
                });
                // 2. Discover the file using glob
                String input2 = String.format("Find all .java files in '%s' using glob.", tempDir.toString());
                return ganglia.sessionManager().getSession(sessionId)
                    .compose(context -> ganglia.agentLoop().run(input2, context));
            })
            .compose(res2 -> {
                testContext.verify(() -> {
                    assertTrue(res2.contains("discovery_test.java"));
                });
                // 3. Search for the secret code using grep_search
                String input3 = String.format("Search for 'SECRET_CODE_123' in '%s' using grep_search.", tempDir.toString());
                return ganglia.sessionManager().getSession(sessionId)
                    .compose(context -> ganglia.agentLoop().run(input3, context));
            })
            .compose(res3 -> {
                testContext.verify(() -> {
                    assertTrue(res3.contains("SECRET_CODE_123"));
                    assertTrue(res3.contains("discovery_test.java"));
                });
                // 4. Read the file using read_file
                String input4 = String.format("Read the content of '%s' using read_file.", testFile);
                return ganglia.sessionManager().getSession(sessionId)
                    .compose(context -> ganglia.agentLoop().run(input4, context));
            })
            .onComplete(testContext.succeeding(res4 -> {
                testContext.verify(() -> {
                    assertTrue(res4.contains("public class DiscoveryTest"));
                    testContext.completeNow();
                });
            }));
    }
}
