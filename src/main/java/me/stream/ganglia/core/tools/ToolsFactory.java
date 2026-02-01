package me.stream.ganglia.core.tools;

import io.vertx.core.Vertx;

/**
 * Factory for creating and managing built-in tool sets.
 */
public class ToolsFactory {
    private final Vertx vertx;
    private final JVMFileSystemTools jvmFileSystemTools;
    private final BashFileSystemTools bashFileSystemTools;

    public ToolsFactory(Vertx vertx) {
        this.vertx = vertx;
        this.jvmFileSystemTools = new JVMFileSystemTools(vertx);
        this.bashFileSystemTools = new BashFileSystemTools(vertx);
    }

    public JVMFileSystemTools getJvmFileSystemTools() {
        return jvmFileSystemTools;
    }

    public BashFileSystemTools getBashFileSystemTools() {
        return bashFileSystemTools;
    }
}