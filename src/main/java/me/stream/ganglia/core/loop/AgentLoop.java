package me.stream.ganglia.core.loop;

import me.stream.ganglia.core.model.SessionContext;
import java.util.concurrent.CompletionStage;

public interface AgentLoop {
    
    /**
     * Starts or resumes the agent loop with a user input.
     * Returns the final answer after the loop settles.
     */
    CompletionStage<String> run(String userInput, SessionContext context);
}
