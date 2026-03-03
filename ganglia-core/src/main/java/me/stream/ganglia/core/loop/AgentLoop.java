package me.stream.ganglia.core.loop;

import io.vertx.core.Future;
import me.stream.ganglia.core.model.AgentSignal;
import me.stream.ganglia.core.model.SessionContext;

public interface AgentLoop {
    
    /**
     * Starts or resumes the agent loop with a user input.
     * Returns the final answer after the loop settles.
     */
    default Future<String> run(String userInput, SessionContext context) {
        return run(userInput, context, new AgentSignal());
    }

    /**
     * Starts or resumes the agent loop with a user input and a cancellation signal.
     */
    Future<String> run(String userInput, SessionContext context, AgentSignal signal);

    /**
     * Resumes the loop by providing the result of an interrupted tool execution (e.g. user selection).
     */
    default Future<String> resume(String toolOutput, SessionContext context) {
        return resume(toolOutput, context, new AgentSignal());
    }

    /**
     * Resumes the loop by providing the result of an interrupted tool execution, with a cancellation signal.
     */
    Future<String> resume(String toolOutput, SessionContext context, AgentSignal signal);
}
