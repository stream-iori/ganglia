package me.stream.ganglia.core.prompt;

import io.vertx.junit5.VertxExtension;
import me.stream.ganglia.core.memory.KnowledgeBase;
import me.stream.ganglia.core.model.SessionContext;
import me.stream.ganglia.core.model.ToDoList;
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
    void testBuildPromptInjectsToDo() {
        StandardPromptEngine engine = new StandardPromptEngine(knowledgeBase);
        ToDoList toDoList = ToDoList.empty().addTask("Task A");
        SessionContext context = new SessionContext(UUID.randomUUID().toString(), Collections.emptyList(), null, Collections.emptyMap(), Collections.emptyList(), null, toDoList);

        String prompt = engine.buildSystemPrompt(context);
        
        assertTrue(prompt.contains("Task A"));
        assertTrue(prompt.contains("MEMORY.md"));
    }
}
