package me.stream.ganglia.core.util;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Utility for ensuring file operations stay within the project sandbox.
 */
public class PathSanitizer {

    private final String sandboxRoot;

    public PathSanitizer() {
        this(System.getProperty("user.dir"));
    }

    public PathSanitizer(String sandboxRoot) {
        this.sandboxRoot = sandboxRoot;
    }

    /**
     * Validates that a path is within the sandbox root and returns its absolute form.
     * @param inputPath The path provided by the agent.
     * @return Absolute path string if valid.
     * @throws SecurityException If the path escapes the sandbox.
     */
    public String sanitize(String inputPath) {
        if (inputPath == null || inputPath.isEmpty()) {
            throw new IllegalArgumentException("Path cannot be empty");
        }

        try {
            // 1. Resolve real path of the sandbox root
            Path root = Paths.get(sandboxRoot).toAbsolutePath().normalize();
            try {
                root = root.toRealPath();
            } catch (IOException ignored) {}

            // 2. Resolve the requested path
            Path requested = Paths.get(inputPath);
            if (!requested.isAbsolute()) {
                requested = root.resolve(inputPath);
            }
            requested = requested.normalize();
            
            Path absoluteRequested;
            try {
                absoluteRequested = requested.toRealPath();
            } catch (IOException e) {
                // If it doesn't exist, just normalize the absolute path
                absoluteRequested = requested.toAbsolutePath().normalize();
            }

            // 3. Sandbox check
            if (!absoluteRequested.startsWith(root)) {
                throw new SecurityException("Access denied: Path escapes project sandbox (" + root + "): " + inputPath);
            }

            return absoluteRequested.toString();
        } catch (SecurityException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to resolve path: " + inputPath, e);
        }
    }

    /**
     * Sanitizes a string for safe use in a shell command.
     * Prevents shell injection by escaping special characters.
     */
    public static String escapeShellArg(String arg) {
        if (arg == null) return "''";
        if (arg.isEmpty()) return "''";
        
        // Wrap in single quotes and escape existing single quotes
        // ' becomes '\''
        return "'" + arg.replace("'", "'\\''") + "'";
    }
}
