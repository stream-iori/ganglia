package work.ganglia.util;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Common utilities for file system operations.
 */
public class FileSystemUtil {
    private static final Logger logger = LoggerFactory.getLogger(FileSystemUtil.class);

    /**
     * Ensures that a directory exists.
     *
     * @param vertx The Vertx instance.
     * @param path  The path to the directory.
     * @return A future that completes when the directory exists.
     */
    public static Future<Void> ensureDirectoryExists(Vertx vertx, String path) {
        return vertx.fileSystem().exists(path)
                .compose(exists -> {
                    if (exists) {
                        return Future.succeededFuture();
                    }
                    logger.info("Creating directory: {}", path);
                    return vertx.fileSystem().mkdirs(path);
                })
                .recover(err -> {
                    // If mkdirs fails because it already exists (race condition), ignore
                    return vertx.fileSystem().exists(path)
                            .compose(exists -> exists ? Future.succeededFuture() : Future.failedFuture(err));
                });
    }

    /**
     * Ensures that a file exists, creating it with default content if it does not.
     * Also ensures that the parent directory exists.
     *
     * @param vertx          The Vertx instance.
     * @param filePath       The path to the file.
     * @param defaultContent The default content to write if the file is created.
     * @return A future that completes when the file exists.
     */
    public static Future<Void> ensureFileWithDefault(Vertx vertx, String filePath, Buffer defaultContent) {
        return vertx.fileSystem().exists(filePath)
                .compose(exists -> {
                    if (exists) {
                        return Future.succeededFuture();
                    }

                    Path path = Paths.get(filePath);
                    Path parent = path.getParent();

                    Future<Void> dirFuture = parent != null ? ensureDirectoryExists(vertx, parent.toString()) : Future.succeededFuture();

                    return dirFuture.compose(v -> {
                        logger.info("Initializing file with default content: {}", filePath);
                        return vertx.fileSystem().writeFile(filePath, defaultContent);
                    });
                });
    }

    /**
     * Ensures that a file exists, creating it with default content if it does not.
     *
     * @param vertx          The Vertx instance.
     * @param filePath       The path to the file.
     * @param defaultContent The default content to write if the file is created.
     * @return A future that completes when the file exists.
     */
    public static Future<Void> ensureFileWithDefault(Vertx vertx, String filePath, String defaultContent) {
        return ensureFileWithDefault(vertx, filePath, Buffer.buffer(defaultContent));
    }
}
