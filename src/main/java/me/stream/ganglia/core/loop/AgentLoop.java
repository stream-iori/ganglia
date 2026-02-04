package me.stream.ganglia.core.loop;

import io.vertx.core.Future;
import me.stream.ganglia.core.model.SessionContext;

public interface AgentLoop {
    
    /**
     * Starts or resumes the agent loop with a user input.
     * Returns the final answer after the loop settles.
     */
    Future<String> run(String userInput, SessionContext context);

    /**
     * Resumes the loop by providing the result of an interrupted tool execution (e.g. user selection).
     */
    Future<String> resume(String toolOutput, SessionContext context);
}
