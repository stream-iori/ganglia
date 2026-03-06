package work.ganglia.infrastructure.internal.memory;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TokenCounterTest {

    @Test
    void testCount() {
        TokenCounter counter = new TokenCounter();
        int count = counter.count("Hello world");
        assertTrue(count > 0, "Should count tokens");
    }
}
