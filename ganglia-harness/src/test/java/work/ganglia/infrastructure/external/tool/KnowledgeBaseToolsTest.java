package work.ganglia.infrastructure.external.tool;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import work.ganglia.infrastructure.internal.memory.FileSystemLongTermMemory;
import work.ganglia.port.chat.SessionContext;
import work.ganglia.port.internal.memory.LongTermMemory;

@ExtendWith(VertxExtension.class)
class KnowledgeBaseToolsTest {

  private Vertx vertx;
  private LongTermMemory longTermMemory;
  private KnowledgeBaseTools tools;
  private static final String TEST_MEMORY_FILE = "target/TEST_MEMORY_TOOLS.md";

  @BeforeEach
  void setUp(Vertx vertx) {
    this.vertx = vertx;
    this.longTermMemory = new FileSystemLongTermMemory(vertx, TEST_MEMORY_FILE);
    this.tools = new KnowledgeBaseTools(vertx, longTermMemory);
  }

  @AfterEach
  void tearDown(Vertx vertx, VertxTestContext testContext) {
    vertx.fileSystem().delete(TEST_MEMORY_FILE).onComplete(ar -> testContext.completeNow());
  }

  @ParameterizedTest
  @ValueSource(strings = {"The sky is blue.", "Java is object-oriented.", "Vert.x is reactive."})
  @DisplayName("Parameterized LongTermMemory Remember Test")
  void testRemember(String fact, VertxTestContext testContext) {
    SessionContext context =
        new SessionContext(
            UUID.randomUUID().toString(),
            Collections.emptyList(),
            null,
            Collections.emptyMap(),
            Collections.emptyList(),
            null);

    tools
        .remember(Map.of("fact", fact), context)
        .compose(result -> longTermMemory.read())
        .onComplete(
            testContext.succeeding(
                content -> {
                  testContext.verify(
                      () -> {
                        assertTrue(
                            content.contains(fact), "Knowledge base should contain: " + fact);
                        testContext.completeNow();
                      });
                }));
  }
}
