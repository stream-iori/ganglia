package work.ganglia.coding.prompt;

import io.vertx.core.Future;
import work.ganglia.port.chat.SessionContext;
import work.ganglia.port.internal.prompt.ContextFragment;
import work.ganglia.port.internal.prompt.WorkflowContextSource;

import java.util.List;

public class CodingWorkflowSource implements WorkflowContextSource {

    @Override
    public Future<List<ContextFragment>> getFragments(SessionContext sessionContext) {
        String workflow = """
            ## [Primary Workflow: Research-Strategy-Execution]
            You operate using a strictly sequential Research -> Strategy -> Execution lifecycle for all Directives.

            ### 1. Research
            - **Goal**: Systematically map the codebase and validate all assumptions before proposing changes.
            - **Actions**: Use `grep_search`, `glob`, and `list_directory` to understand file structures and existing patterns. Use `read_file` to validate assumptions.
            - **Confirmation**: ALWAYS confirm the failure state by empirically reproducing reported issues (e.g., via a test case) before fixing.

            ### 2. Strategy
            - **Goal**: Formulate a grounded implementation plan based on Research findings.
            - **Actions**: Present a concise summary of your strategy. If the task is complex, use the ToDo tools to decompose it.

            ### 3. Execution (Plan -> Act -> Validate)
            For each sub-task:
            - **Plan**: Define the specific implementation approach and the testing strategy.
            - **Act**: Apply targeted, surgical changes strictly related to the sub-task. Follow all workspace standards.
            - **Validate**: Run tests and workspace standards (linting, type-checking) to confirm behavioral correctness and ensure no regressions.
            """;

        return Future.succeededFuture(List.of(
            ContextFragment.mandatory("Workflow", workflow, ContextFragment.PRIORITY_WORKFLOW)
        ));
    }
}
