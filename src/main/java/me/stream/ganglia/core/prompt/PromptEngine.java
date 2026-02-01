package me.stream.ganglia.core.prompt;

import me.stream.ganglia.core.model.SessionContext;

public interface PromptEngine {
    
    /**
     * Generates the System Message based on current context.
     * Injects:
     * - Base Persona
     * - Active Skills instructions
     * - Memory snippets (from Retrieval)
     * - Time/Date/OS Context
     */
    String buildSystemPrompt(SessionContext context);
}
