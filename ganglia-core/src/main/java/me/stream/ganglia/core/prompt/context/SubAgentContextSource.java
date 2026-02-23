package me.stream.ganglia.core.prompt.context;

import io.vertx.core.Future;
import me.stream.ganglia.core.model.SessionContext;

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
        
        switch (persona) {
            case "INVESTIGATOR" -> {
                sb.append("- ROLE: Code Investigator.\n");
                sb.append("- FOCUS: Use tools like grep, glob, and read to understand the code. Do NOT modify any files.\n");
                sb.append("- GOAL: Provide a detailed technical report of your findings.\n");
            }
            case "REFACTORER" -> {
                sb.append("- ROLE: Refactoring Expert.\n");
                sb.append("- FOCUS: Use replace_in_file to make precise, surgical changes. Avoid unnecessary rewrites.\n");
                sb.append("- GOAL: Report exactly what was modified and why.\n");
            }
            default -> sb.append("- ROLE: Specialized Worker.\n");
        }
        
        sb.append("- CONSTRAINT: When finished, provide your final answer as a structured summary report for the Parent Agent.\n");

        List<ContextFragment> fragments = new ArrayList<>();
        fragments.add(new ContextFragment("SubAgentMode", sb.toString(), 1, true)); // Priority 1: Mandatory
        
        return Future.succeededFuture(fragments);
    }
}
