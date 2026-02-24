package me.stream.ganglia.core.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks spawned child processes to ensure they are cleaned up on shutdown.
 */
public class ProcessTracker {
    private static final Logger logger = LoggerFactory.getLogger(ProcessTracker.class);
    private static final Set<Process> trackedProcesses = ConcurrentHashMap.newKeySet();

    static {
        // Register shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(ProcessTracker::cleanupAll, "process-cleanup-hook"));
    }

    public static void track(Process process) {
        trackedProcesses.add(process);
        process.onExit().thenRun(() -> trackedProcesses.remove(process));
    }

    public static void untrack(Process process) {
        trackedProcesses.remove(process);
    }

    public static void cleanupAll() {
        if (trackedProcesses.isEmpty()) return;
        
        logger.info("Cleaning up {} tracked child processes...", trackedProcesses.size());
        for (Process process : trackedProcesses) {
            if (process.isAlive()) {
                logger.debug("Force destroying process: {}", process.pid());
                process.destroyForcibly();
            }
        }
        trackedProcesses.clear();
    }
}
