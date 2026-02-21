package me.stream.ganglia.core.prompt;

import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import me.stream.ganglia.memory.KnowledgeBase;
import me.stream.ganglia.memory.TokenCounter;
import me.stream.ganglia.core.model.*;
import me.stream.ganglia.tools.ToolExecutor;
import me.stream.ganglia.tools.model.ToDoList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith({VertxExtension.class, MockitoExtension.class})
class StandardPromptEngineTest {

    @Mock
    KnowledgeBase knowledgeBase;
    @Mock
    ToolExecutor toolExecutor;

    @Test
    void testBuildPromptInjectsToDo(Vertx vertx, VertxTestContext testContext) {
        StandardPromptEngine engine = new StandardPromptEngine(vertx, knowledgeBase, null, null, toolExecutor);
        ToDoList toDoList = ToDoList.empty().addTask("Task A");
        SessionContext context = new SessionContext(UUID.randomUUID().toString(), Collections.emptyList(), null, Collections.emptyMap(), Collections.emptyList(), null, toDoList);

        engine.buildSystemPrompt(context).onComplete(testContext.succeeding(prompt -> {
            assertTrue(prompt.contains("Task A"));
            testContext.completeNow();
        }));
    }

    @Test
    void testBuildPromptInjectsGuidelines(Vertx vertx, VertxTestContext testContext) {
        String guidelines = "## [Guidelines]\n- Rule 1";
        vertx.fileSystem().writeFile("GANGLIA.md", io.vertx.core.buffer.Buffer.buffer(guidelines))
            .compose(v -> {
                StandardPromptEngine engine = new StandardPromptEngine(vertx, knowledgeBase, null, null, toolExecutor);
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

    @Test
    void testPruneHistory() {
        TokenCounter counter = new TokenCounter();
        SessionContext context = new SessionContext("sid", Collections.emptyList(), null, Collections.emptyMap(), Collections.emptyList(), null, ToDoList.empty());
        
        Message m1 = Message.user("Msg 1");
        Message m2 = Message.assistant("Msg 2");
        Message m3 = Message.user("Msg 3");
        
        context = context.addStep(m1).addStep(m2).addStep(m3);

        // Prune to 1 token (should keep ONLY the last message)
        List<Message> pruned = context.getPrunedHistory(1, counter);
        assertEquals(1, pruned.size());
        assertEquals("Msg 3", pruned.get(0).content());
    }

    @Test
    void testPrepareRequest(Vertx vertx, VertxTestContext testContext) {
        when(toolExecutor.getAvailableTools(any())).thenReturn(Collections.emptyList());
        StandardPromptEngine engine = new StandardPromptEngine(vertx, knowledgeBase, null, null, toolExecutor);
        
        ModelOptions options = new ModelOptions(0.0, 100, "test-model");
        SessionContext context = new SessionContext("sid", Collections.emptyList(), null, Collections.emptyMap(), Collections.emptyList(), options, ToDoList.empty());

        engine.prepareRequest(context, 0).onComplete(testContext.succeeding(request -> {
            assertEquals(1, request.messages().size());
            assertEquals(Role.SYSTEM, request.messages().get(0).role());
            assertEquals("test-model", request.options().modelName());
            testContext.completeNow();
        }));
    }
}
