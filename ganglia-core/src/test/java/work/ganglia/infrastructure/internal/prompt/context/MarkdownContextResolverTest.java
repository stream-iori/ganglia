package work.ganglia.infrastructure.internal.prompt.context;

import work.ganglia.port.internal.prompt.ContextFragment;
import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(VertxExtension.class)
class MarkdownContextResolverTest {

    private MarkdownContextResolver resolver;

    @BeforeEach
    void setUp(Vertx vertx) {
        resolver = new MarkdownContextResolver(vertx);
    }

    @Test
    void testParseMarkdownByHeaders(VertxTestContext testContext) {
        String content = """
                # Project
                ## [Mandates] (Priority: 2)
                - Do not delete
                ## [Context] (Priority: 3, Mandatory)
                - Use Java 17
                ## Simple Header
                Some content for simple header.
                """;

        resolver.parse("test.md", content)
                .onComplete(testContext.succeeding(fragments -> {
                    testContext.verify(() -> {
                        assertEquals(3, fragments.size());

                        ContextFragment f1 = fragments.get(0);
                        assertEquals("Mandates", f1.name());
                        assertEquals(2, f1.priority());
                        assertFalse(f1.isMandatory());
                        assertEquals("- Do not delete", f1.content());

                        ContextFragment f2 = fragments.get(1);
                        assertEquals("Context", f2.name());
                        assertEquals(3, f2.priority());
                        assertTrue(f2.isMandatory());
                        assertEquals("- Use Java 17", f2.content());

                        ContextFragment f3 = fragments.get(2);
                        assertEquals("Simple Header", f3.name());
                        assertEquals(5, f3.priority());
                        assertFalse(f3.isMandatory());
                        assertEquals("Some content for simple header.", f3.content());

                        testContext.completeNow();
                    });
                }));
    }

    @Test
    void testEmptyContent(VertxTestContext testContext) {
        resolver.parse("empty.md", "")
                .onComplete(testContext.succeeding(fragments -> {
                    testContext.verify(() -> {
                        assertTrue(fragments.isEmpty());
                        testContext.completeNow();
                    });
                }));
    }

    @Test
    void testNoH2Headers(VertxTestContext testContext) {
        String content = """
                # Title
                ### H3 Header
                Some content
                """;
        resolver.parse("no_h2.md", content)
                .onComplete(testContext.succeeding(fragments -> {
                    testContext.verify(() -> {
                        assertTrue(fragments.isEmpty());
                        testContext.completeNow();
                    });
                }));
    }
}
