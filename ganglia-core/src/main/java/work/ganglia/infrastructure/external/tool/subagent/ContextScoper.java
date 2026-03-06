package work.ganglia.infrastructure.external.tool.subagent;

import work.ganglia.port.chat.SessionContext;
import work.ganglia.infrastructure.external.tool.model.ToDoList;

import java.util.*;

/**
 * Utility for scoping and isolating context for Sub-Agents.
 */
public class ContextScoper {

    /**
     * Creates a scoped session context for a sub-agent.
     * Starts with a clean history but inherits metadata and active skills.
     */
    public static SessionContext scope(String childSessionId, SessionContext parentContext, Map<String, Object> extraMetadata) {
        Map<String, Object> childMetadata = new HashMap<>(parentContext.metadata());
        if (extraMetadata != null) {
            childMetadata.putAll(extraMetadata);
        }

        // Sub-agents usually don't inherit the parent's ToDo list, as they have their own focused task.
        // But they might need some global state from metadata.

        return new SessionContext(
            childSessionId,
            Collections.emptyList(),
            null,
            childMetadata,
            parentContext.activeSkillIds(),
            parentContext.modelOptions(),
            ToDoList.empty()
        );
    }
}
