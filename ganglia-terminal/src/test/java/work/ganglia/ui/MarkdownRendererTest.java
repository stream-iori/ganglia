package work.ganglia.ui;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class MarkdownRendererTest {

    @Test
    void testBasicRendering() {
        MarkdownRenderer renderer = new MarkdownRenderer();
        String result = renderer.render("""
            # Title
            This is **bold** and *italic*.
            """);

        // Title: Bold (1), Cyan (36), Underline (4)
        assertTrue(result.contains("\u001B[4;36;1mTitle"));
        // Bold: [1m
        assertTrue(result.contains("\u001B[1mbold"));
        // Italic: [3m
        assertTrue(result.contains("\u001B[3mitalic"));
    }

    @Test
    void testCodeBlocks() {
        MarkdownRenderer renderer = new MarkdownRenderer();
        String result = renderer.render("""
            ```java
            int x = 1;
            ```
            """);
        assertTrue(result.contains("--- Code Block ---"));
        assertTrue(result.contains("int x = 1;"));
    }
}
