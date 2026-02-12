package me.stream.ganglia.core.prompt.context;

import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;

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
                ## [Context] (Priority: 3)
                - Use Java 17
                """;

        List<ContextFragment> fragments = resolver.parse("test.md", content);

        assertEquals(2, fragments.size());
        
        ContextFragment f1 = fragments.stream().filter(f -> f.name().contains("Mandates")).findFirst().orElseThrow();
        assertEquals(2, f1.priority());
        assertTrue(f1.content().contains("- Do not delete"));

        ContextFragment f2 = fragments.stream().filter(f -> f.name().contains("Context")).findFirst().orElseThrow();
        assertEquals(3, f2.priority());
        assertTrue(f2.content().contains("- Use Java 17"));
        
        testContext.completeNow();
    }
}
