package me.stream.ganglia.core.prompt;

import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import me.stream.ganglia.memory.KnowledgeBase;
import me.stream.ganglia.core.model.SessionContext;
import me.stream.ganglia.tools.model.ToDoList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith({VertxExtension.class, MockitoExtension.class})
class StandardPromptEngineTest {

    @Mock
    KnowledgeBase knowledgeBase;

    @Test
    void testBuildPromptInjectsToDo(Vertx vertx, VertxTestContext testContext) {
        StandardPromptEngine engine = new StandardPromptEngine(vertx, knowledgeBase, null, null);
        ToDoList toDoList = ToDoList.empty().addTask("Task A");
        SessionContext context = new SessionContext(UUID.randomUUID().toString(), Collections.emptyList(), null, Collections.emptyMap(), Collections.emptyList(), null, toDoList);

        engine.buildSystemPrompt(context).onComplete(testContext.succeeding(prompt -> {
            assertTrue(prompt.contains("Task A"));
            assertTrue(prompt.contains("MEMORY.md"));
            testContext.completeNow();
        }));
    }

    @Test
    void testBuildPromptInjectsGuidelines(Vertx vertx, VertxTestContext testContext) {
        String guidelines = "# Test Guidelines\n- Rule 1";
        vertx.fileSystem().writeFile("GANGLIA.md", io.vertx.core.buffer.Buffer.buffer(guidelines))
            .compose(v -> {
                StandardPromptEngine engine = new StandardPromptEngine(vertx, knowledgeBase, null, null);
                SessionContext context = new SessionContext(UUID.randomUUID().toString(), Collections.emptyList(), null, Collections.emptyMap(), Collections.emptyList(), null, ToDoList.empty());
                return engine.buildSystemPrompt(context);
            })
            .onComplete(testContext.succeeding(prompt -> {
                assertTrue(prompt.contains("Rule 1"));
                // Clean up
                vertx.fileSystem().delete("GANGLIA.md");
                testContext.completeNow();
            }));
    }
}
