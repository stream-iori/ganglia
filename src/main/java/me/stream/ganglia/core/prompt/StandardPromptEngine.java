package me.stream.ganglia.core.prompt;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import me.stream.ganglia.memory.KnowledgeBase;
import me.stream.ganglia.core.model.SessionContext;
import me.stream.ganglia.tools.model.ToDoList;
import me.stream.ganglia.skills.SkillPromptInjector;
import me.stream.ganglia.skills.SkillSuggester;

public class StandardPromptEngine implements PromptEngine {
    private final Vertx vertx;
    private final KnowledgeBase knowledgeBase;
    private final SkillPromptInjector skillInjector;
    private final SkillSuggester skillSuggester;

    public StandardPromptEngine(Vertx vertx, KnowledgeBase knowledgeBase, SkillPromptInjector skillInjector, SkillSuggester skillSuggester) {
        this.vertx = vertx;
        this.knowledgeBase = knowledgeBase;
        this.skillInjector = skillInjector;
        this.skillSuggester = skillSuggester;
    }

        @Override
        public Future<String> buildSystemPrompt(SessionContext context) {
            StringBuilder sb = new StringBuilder();
            sb.append("You are Ganglia, an advanced AI software engineer.\n");
            sb.append("You are running in a CLI environment.\n\n");

            // Inject ToDo List
            ToDoList toDoList = context.toDoList();
            if (toDoList != null) {
                sb.append("## Current Plan\n");
                sb.append(toDoList.toString()).append("\n\n");
            } else {
                sb.append("## Current Plan\nNo active plan. You should create one using todo_add if the task is complex.\n\n");
            }

            // Inject Memory Instructions
            sb.append("## Memory & Context\n");
            sb.append("- You have access to a persistent knowledge base (MEMORY.md).\n");
            sb.append("- Use the 'remember' tool to save important facts, user preferences, or architectural decisions.\n");
            sb.append("- Use 'grep' or 'read' to search MEMORY.md if you need to recall project context.\n\n");

            Future<String> guidelinesFuture = loadGuidelines();

            Future<String> skillsFuture = skillInjector != null ?
                skillInjector.injectSkills(context.activeSkillIds()) : Future.succeededFuture("");

            Future<String> suggestionsFuture = skillSuggester != null ?
                skillSuggester.suggestSkills(".", context.activeSkillIds()) : Future.succeededFuture("");

            return Future.join(guidelinesFuture, skillsFuture, suggestionsFuture)
                    .map(composite -> {
                        String guidelines = composite.resultAt(0);
                        String skillsContext = composite.resultAt(1);
                        String suggestions = composite.resultAt(2);

                        sb.append("## Guidelines\n");
                        sb.append(guidelines).append("\n");

                        if (!skillsContext.isEmpty()) {
                            sb.append("\n").append(skillsContext).append("\n");
                        }
                        if (!suggestions.isEmpty()) {
                            sb.append("\n").append(suggestions).append("\n");
                        }
                        return sb.toString();
                    });
        }

        private Future<String> loadGuidelines() {
            return vertx.fileSystem().exists("GANGLIA.md")
                    .compose(exists -> {
                        if (exists) {
                            return vertx.fileSystem().readFile("GANGLIA.md").map(io.vertx.core.buffer.Buffer::toString);
                        } else {
                            return Future.succeededFuture("""
                                    - Break down complex tasks into steps using todo_add.
                                    - Mark tasks as complete using todo_complete ONLY when the work is verified.
                                    - Use tools to explore the codebase. Do not hallucinate file contents.
                                    """);
                        }
                    });
        }}
