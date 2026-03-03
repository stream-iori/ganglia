package me.stream.ganglia.core.prompt.context;

import io.vertx.core.Future;
import me.stream.ganglia.core.model.SessionContext;
import me.stream.ganglia.core.schedule.ScheduleableFactory;
import me.stream.ganglia.tools.model.ToolDefinition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Injects best practices and rules for using specific tools.
 * Dynamically generated based on available tools in the current context.
 */
public class ToolContextSource implements ContextSource {

    private final ScheduleableFactory scheduleableFactory;

    public ToolContextSource(ScheduleableFactory scheduleableFactory) {
        this.scheduleableFactory = scheduleableFactory;
    }

    @Override
    public Future<List<ContextFragment>> getFragments(SessionContext context) {
        List<ToolDefinition> tools = scheduleableFactory.getAvailableDefinitions(context);
        if (tools.isEmpty()) {
            return Future.succeededFuture(Collections.emptyList());
        }

        StringBuilder guidelines = new StringBuilder("## [Tool Usage Guidelines]\n");
        guidelines.append("You have access to a set of tools. Follow these best practices:\n");

        List<String> toolNames = tools.stream().map(ToolDefinition::name).collect(Collectors.toList());

        if (toolNames.contains("list_directory") || toolNames.contains("glob")) {
            guidelines.append("- **Discovery First**: Before reading or modifying files, use `list_directory` or `glob` to confirm paths and understand structure.\n");
        }

        if (toolNames.contains("read_file") || toolNames.contains("vertx_read")) {
            guidelines.append("- **Read Sparingly**: Only read files you actually need to see. Use `grep_search` to narrow down locations first.\n");
        }

        if (toolNames.contains("write_file")) {
            guidelines.append("- **Precise Writes**: When using `write_file`, ensure you have the full, correct content. Always verify the parent directory exists first.\n");
        }

        if (toolNames.contains("run_shell_command")) {
            guidelines.append("- **Shell Safety**: Prefer specialized tools over generic shell commands when possible. Always use non-interactive commands.\n");
        }
        
        if (toolNames.contains("ask_selection")) {
            guidelines.append("- **Clarify Early**: If a user request is ambiguous, use `ask_selection` immediately instead of guessing.\n");
        }

        List<ContextFragment> fragments = new ArrayList<>();
        // Priority 5 (Same as Skill context, to keep "How to" instructions together)
        fragments.add(new ContextFragment("ToolGuidelines", guidelines.toString(), 5, false));
        
        return Future.succeededFuture(fragments);
    }
}
