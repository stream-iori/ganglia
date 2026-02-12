package me.stream.ganglia.core.skills;

import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import me.stream.ganglia.memory.KnowledgeBase;
import me.stream.ganglia.core.model.SessionContext;
import me.stream.ganglia.tools.model.ToDoList;
import me.stream.ganglia.core.prompt.StandardPromptEngine;
import me.stream.ganglia.skills.SkillPromptInjector;
import me.stream.ganglia.skills.SkillRegistry;
import me.stream.ganglia.skills.SkillSuggester;
import me.stream.ganglia.tools.DefaultToolExecutor;
import me.stream.ganglia.tools.ToolsFactory;
import me.stream.ganglia.tools.model.ToolCall;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith({VertxExtension.class, MockitoExtension.class})
class SkillIntegrationTest {

    @Mock
    KnowledgeBase knowledgeBase;

    @Test
    void testSkillPromptInjection(Vertx vertx, VertxTestContext testContext) {
        Path skillsDir = Paths.get("src/test/resources/skills");
        SkillRegistry registry = new SkillRegistry(vertx, skillsDir);
        SkillPromptInjector injector = new SkillPromptInjector(vertx, registry, skillsDir);
        StandardPromptEngine engine = new StandardPromptEngine(vertx, knowledgeBase, injector, null);

        registry.init().compose(v -> {
            SessionContext context = new SessionContext(
                UUID.randomUUID().toString(),
                Collections.emptyList(),
                null,
                Collections.emptyMap(),
                List.of("test-skill"),
                null,
                ToDoList.empty()
            );
            return engine.buildSystemPrompt(context);
        }).onComplete(testContext.succeeding(prompt -> {
            assertTrue(prompt.contains("Active Skills"));
            assertTrue(prompt.contains("Test Skill - test-prompt"));
            assertTrue(prompt.contains("Use test-driven development."));
            testContext.completeNow();
        }));
    }

    @Test
    void testSkillSuggestion(Vertx vertx, VertxTestContext testContext) {
        Path skillsDir = Paths.get("src/test/resources/skills");
        SkillRegistry registry = new SkillRegistry(vertx, skillsDir);
        SkillSuggester suggester = new SkillSuggester(vertx, registry);
        StandardPromptEngine engine = new StandardPromptEngine(vertx, knowledgeBase, null, suggester);

        registry.init().compose(v -> {
            return vertx.fileSystem().writeFile("./dummy.test", io.vertx.core.buffer.Buffer.buffer("dummy"))
                .compose(v2 -> {
                    SessionContext context = new SessionContext(
                        UUID.randomUUID().toString(),
                        Collections.emptyList(),
                        null,
                        Collections.emptyMap(),
                        new ArrayList<>(),
                        null,
                        ToDoList.empty()
                    );
                    return engine.buildSystemPrompt(context);
                });
        }).onComplete(testContext.succeeding(prompt -> {
            assertTrue(prompt.contains("Skill Suggestions"));
            assertTrue(prompt.contains("test-skill"));

            // Cleanup
            vertx.fileSystem().delete("./dummy.test")
                .onComplete(ar -> testContext.completeNow());
        }));
    }

    @Test
    void testFullSkillLifecycle(Vertx vertx, VertxTestContext testContext) {
        Path skillsDir = Paths.get("src/test/resources/skills");
        SkillRegistry registry = new SkillRegistry(vertx, skillsDir);
        DefaultToolExecutor toolExecutor = new DefaultToolExecutor(new ToolsFactory(vertx, null, knowledgeBase), registry);

        registry.init().onComplete(testContext.succeeding(v -> {
            SessionContext context = new SessionContext(
                UUID.randomUUID().toString(),
                Collections.emptyList(),
                null,
                Collections.emptyMap(),
                new ArrayList<>(), // Empty initially
                null,
                ToDoList.empty()
            );

            // 1. Check available skills
            ToolCall listCall = new ToolCall("c1", "list_available_skills", Collections.emptyMap());
            toolExecutor.execute(listCall, context).onComplete(testContext.succeeding(res1 -> {
                assertTrue(res1.output().contains("test-skill"));

                // 2. Activate skill
                ToolCall activateCall = new ToolCall("c2", "activate_skill", java.util.Map.of("skillId", "test-skill"));
                toolExecutor.execute(activateCall, context).onComplete(testContext.succeeding(res2 -> {
                    SessionContext contextWithSkill = res2.modifiedContext();
                    assertNotNull(contextWithSkill);
                    assertTrue(contextWithSkill.activeSkillIds().contains("test-skill"));

                    // 3. Verify skill tool is available
                    var tools = toolExecutor.getAvailableTools(contextWithSkill);
                    assertTrue(tools.stream().anyMatch(t -> t.name().equals("test_tool")));

                    // 4. Execute skill tool
                    ToolCall skillToolCall = new ToolCall("c3", "test_tool", java.util.Map.of("arg", "hello"));
                    toolExecutor.execute(skillToolCall, contextWithSkill).onComplete(testContext.succeeding(res3 -> {
                        assertTrue(res3.output().contains("Test tool executed with arg: hello"));
                        testContext.completeNow();
                    }));
                }));
            }));
        }));
    }
}
