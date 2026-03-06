package work.ganglia.infrastructure.internal.prompt.context;

import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(VertxExtension.class)
public class DailyContextSourceTest {

    private DailyContextSource source;
    private String testPath;

    @BeforeEach
    void setUp(Vertx vertx, @TempDir Path tempDir) {
        testPath = tempDir.toString();
        source = new DailyContextSource(vertx, testPath);
    }

    @Test
    void testResolve(Vertx vertx, VertxTestContext testContext) {
        String dateStr = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
        String filePath = testPath + "/daily-" + dateStr + ".md";
        String content = "# Daily Record\nSome accomplishments.";

        vertx.fileSystem().writeFile(filePath, io.vertx.core.buffer.Buffer.buffer(content))
            .compose(v -> source.getFragments(null))
            .onComplete(testContext.succeeding(fragments -> {
                testContext.verify(() -> {
                    assertEquals(1, fragments.size());
                    assertEquals("Daily Journal", fragments.get(0).name());
                    assertTrue(fragments.get(0).content().contains("Some accomplishments"));
                    assertEquals(9, fragments.get(0).priority());
                    testContext.completeNow();
                });
            }));
    }

    @Test
    void testResolveMissingFile(VertxTestContext testContext) {
        source.getFragments(null)
            .onComplete(testContext.succeeding(fragments -> {
                testContext.verify(() -> {
                    assertTrue(fragments.isEmpty());
                    testContext.completeNow();
                });
            }));
    }
}
