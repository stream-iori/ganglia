package me.stream.ganglia.core.prompt.context;

import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(VertxExtension.class)
class FileContextSourceTest {

    private FileContextSource source;

    @BeforeEach
    void setUp(Vertx vertx) {
        MarkdownContextResolver resolver = new MarkdownContextResolver(vertx);
        source = new FileContextSource(vertx, resolver, "GANGLIA.md");
    }

    @Test
    void testGetFragmentsFromFile(Vertx vertx, VertxTestContext testContext) {
        String content = "## [Mandates] (Priority: 2)\n- Rule X";
        vertx.fileSystem().writeFile("GANGLIA.md", io.vertx.core.buffer.Buffer.buffer(content))
            .compose(v -> source.getFragments(null))
            .onComplete(testContext.succeeding(fragments -> {
                assertEquals(1, fragments.size());
                assertEquals("Mandates", fragments.get(0).name());
                // Cleanup
                vertx.fileSystem().delete("GANGLIA.md")
                    .onComplete(ar -> testContext.completeNow());
            }));
    }
}