package work.ganglia.infrastructure.internal.prompt.context;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import io.vertx.core.Future;

import work.ganglia.kernel.task.AgentTaskFactory;
import work.ganglia.port.chat.SessionContext;
import work.ganglia.port.external.tool.ToolDefinition;
import work.ganglia.port.internal.prompt.ContextFragment;
import work.ganglia.port.internal.prompt.ContextSource;

/**
 * Injects best practices and rules for using specific tools. Dynamically generated based on
 * available tools in the current context.
 */
public class ToolContextSource implements ContextSource {

  private final Supplier<AgentTaskFactory> taskFactoryProvider;

  public ToolContextSource(Supplier<AgentTaskFactory> taskFactoryProvider) {
    this.taskFactoryProvider = taskFactoryProvider;
  }

  @Override
  public Future<List<ContextFragment>> getFragments(SessionContext context) {
    AgentTaskFactory taskFactory = taskFactoryProvider.get();
    if (taskFactory == null) {
      return Future.succeededFuture(Collections.emptyList());
    }

    List<ToolDefinition> tools = taskFactory.getAvailableDefinitions(context);
    if (tools.isEmpty()) {
      return Future.succeededFuture(Collections.emptyList());
    }

    StringBuilder guidelines = new StringBuilder("## [Tool Usage Guidelines]\n");
    guidelines.append("You have access to a set of tools. Follow these best practices:\n");

    List<String> toolNames = tools.stream().map(ToolDefinition::name).collect(Collectors.toList());

    if (toolNames.contains("list_directory")) {
      guidelines.append(
          "- **Discovery First**: Before reading or modifying files, use `list_directory` to confirm paths and understand structure.\n");
    }

    if (toolNames.contains("read_file") || toolNames.contains("vertx_read")) {
      guidelines.append(
          "- **Read Sparingly**: Only read files you actually need to see. Use `grep_search` to narrow down locations first.\n");
    }

    if (toolNames.contains("write_file")) {
      guidelines.append(
          "- **Precise Writes**: When using `write_file`, ensure you have the full, correct content. Always verify the parent directory exists first.\n");
    }

    if (toolNames.contains("run_shell_command")) {
      guidelines.append(
          "- **Shell Safety**: Prefer specialized tools over generic shell commands when possible. Always use non-interactive commands.\n");
    }

    if (toolNames.contains("ask_selection")) {
      guidelines.append(
          "- **Mandatory User Interaction**: You MUST use `ask_selection` whenever you need to present choices or ask for clarification. DO NOT just output options as text; using the tool is the ONLY way to trigger the rich interactive UI for the user.\n");
    }

    List<ContextFragment> fragments = new ArrayList<>();
    // Capability layer (Tools) - Mandatory rules for usage
    fragments.add(
        ContextFragment.mandatory(
            "ToolGuidelines", guidelines.toString(), ContextFragment.PRIORITY_TOOLS));

    return Future.succeededFuture(fragments);
  }
}
