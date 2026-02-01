package me.stream.ganglia.core.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Represents the full context of a running session.
 */
public record SessionContext(
    String sessionId,
    List<Message> history,
    Map<String, Object> metadata, // Arbitrary session metadata
    List<String> activeSkillIds, // Currently active skills
    ModelOptions modelOptions // Current model configuration
) {
    public SessionContext withNewMessage(Message msg) {
        List<Message> newHistory = new ArrayList<>(history);
        newHistory.add(msg);
        return new SessionContext(sessionId, newHistory, metadata, activeSkillIds, modelOptions);
    }

    public SessionContext withModelOptions(ModelOptions newOptions) {
        return new SessionContext(sessionId, history, metadata, activeSkillIds, newOptions);
    }
}