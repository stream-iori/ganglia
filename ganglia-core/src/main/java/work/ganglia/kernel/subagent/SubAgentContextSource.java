package work.ganglia.kernel.subagent;

import work.ganglia.port.internal.prompt.ContextFragment;
import io.vertx.core.Future;
import work.ganglia.port.chat.SessionContext;
import work.ganglia.port.internal.prompt.ContextSource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Injects specific instructions if the agent is running as a Sub-Agent.
 */
public class SubAgentContextSource implements ContextSource {

    @Override
    public Future<List<ContextFragment>> getFragments(SessionContext context) {
        Object isSubObj = context.metadata().getOrDefault("is_sub_agent", false);
        boolean isSub = isSubObj instanceof Boolean ? (Boolean) isSubObj : Boolean.parseBoolean(isSubObj.toString());

        if (!isSub) {
            return Future.succeededFuture(Collections.emptyList());
        }

        String persona = (String) context.metadata().getOrDefault("sub_agent_persona", "GENERAL");

        StringBuilder sb = new StringBuilder("## [SUB-AGENT MODE]\n");
        sb.append("You are currently acting as a specialized SUB-AGENT delegated by a Parent Orchestrator.\n");
        sb.append("Your scope is restricted to the specific task provided.\n");
        sb.append("- ROLE: ").append(persona).append("\n");
        sb.append("- CONSTRAINT: When finished, provide your final answer as a structured summary report for the Parent Agent.\n");

        List<ContextFragment> fragments = new ArrayList<>();
        fragments.add(new ContextFragment("SubAgentMode", sb.toString(), 1, true)); // Priority 1: Mandatory

        return Future.succeededFuture(fragments);
    }
}
