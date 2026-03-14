package work.ganglia.infrastructure.internal.prompt;

import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import work.ganglia.port.chat.Message;
import work.ganglia.port.external.llm.ModelOptions;
import work.ganglia.port.chat.Role;
import work.ganglia.port.chat.SessionContext;
import work.ganglia.kernel.AgentEnv;
import work.ganglia.port.external.llm.ChatRequest;
import work.ganglia.kernel.task.DefaultAgentTaskFactory;
import work.ganglia.kernel.task.AgentTaskFactory;
import work.ganglia.infrastructure.internal.memory.TokenCounter;
import work.ganglia.stubs.StubToolExecutor;
import work.ganglia.kernel.todo.ToDoList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(VertxExtension.class)
class StandardPromptEngineTest {

    @Test
    void testBuildPromptInjectsToDo(Vertx vertx, VertxTestContext testContext) {
        TokenCounter counter = new TokenCounter();
        StubToolExecutor toolExecutor = new StubToolExecutor();
        AgentEnv env = new AgentEnv(vertx, null, null, null, null, null, null, null, null, null, null);
        AgentTaskFactory taskFactory = new DefaultAgentTaskFactory(env, toolExecutor, null, null, null);
        StandardPromptEngine engine = new StandardPromptEngine(vertx, null, null, taskFactory, counter);
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
                TokenCounter counter = new TokenCounter();
                StubToolExecutor toolExecutor = new StubToolExecutor();
                AgentEnv env = new AgentEnv(vertx, null, null, null, null, null, null, null, null, null, null);
                AgentTaskFactory taskFactory = new DefaultAgentTaskFactory(env, toolExecutor, null, null, null);
                StandardPromptEngine engine = new StandardPromptEngine(vertx, null, null, taskFactory, counter);
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

        // These steps are added to the CURRENT turn
        context = context.addStep(m1).addStep(m2).addStep(m3);

        // Prune to 1 token (current turn is ALWAYS kept entirely)
        List<Message> pruned = context.getPrunedHistory(1, counter);
        assertEquals(3, pruned.size());
        assertEquals("Msg 1", pruned.get(0).content());
        assertEquals("Msg 3", pruned.get(2).content());
    }

    @Test
    void testPrepareRequest(Vertx vertx, VertxTestContext testContext) {
        StubToolExecutor toolExecutor = new StubToolExecutor(); // Returns empty list by default
        TokenCounter counter = new TokenCounter();
        AgentEnv env = new AgentEnv(vertx, null, null, null, null, null, null, null, null, null, null);
        AgentTaskFactory taskFactory = new DefaultAgentTaskFactory(env, toolExecutor, null, null, null);
        StandardPromptEngine engine = new StandardPromptEngine(vertx, null, null, taskFactory, counter);

        ModelOptions options = new ModelOptions(0.0, 100, "test-model", true);
        SessionContext context = new SessionContext("sid", Collections.emptyList(), null, Collections.emptyMap(), Collections.emptyList(), options, ToDoList.empty());

        engine.prepareRequest(context, 0).onComplete(testContext.succeeding(request -> {
            assertEquals(1, request.messages().size());
            assertEquals(Role.SYSTEM, request.messages().get(0).role());
            assertEquals("test-model", request.options().modelName());
            testContext.completeNow();
        }));
    }
}
