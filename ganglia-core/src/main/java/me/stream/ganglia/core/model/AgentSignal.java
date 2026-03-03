package me.stream.ganglia.core.model;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A signal used to propagate a hard abort request through the agent loop and underlying operations.
 */
public class AgentSignal {
    private final AtomicBoolean aborted = new AtomicBoolean(false);

    public AgentSignal() {
    }

    public void abort() {
        aborted.set(true);
    }

    public boolean isAborted() {
        return aborted.get();
    }
}
