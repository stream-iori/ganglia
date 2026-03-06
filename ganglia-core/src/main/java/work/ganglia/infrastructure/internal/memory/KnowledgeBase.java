package work.ganglia.infrastructure.internal.memory;

import io.vertx.core.Future;

/**
 * Interface for the long-term knowledge base.
 */
public interface KnowledgeBase {
    String DEFAULT_TOPIC = "project";

    /**
     * Ensures the storage for a specific topic is initialized.
     *
     * @param topic The knowledge topic.
     * @return A Future completing when initialized.
     */
    Future<Void> ensureInitialized(String topic);

    /**
     * Reads the knowledge content for a specific topic.
     *
     * @param topic The knowledge topic.
     * @return A Future completing with the content.
     */
    Future<String> read(String topic);

    /**
     * Appends content to the knowledge base for a specific topic.
     *
     * @param topic   The knowledge topic.
     * @param content The content to append.
     * @return A Future completing when the content is appended.
     */
    Future<Void> append(String topic, String content);

    /**
     * Ensures the default project topic is initialized.
     */
    default Future<Void> ensureInitialized() {
        return ensureInitialized(DEFAULT_TOPIC);
    }

    /**
     * Reads the default project topic content.
     */
    default Future<String> read() {
        return read(DEFAULT_TOPIC);
    }

    /**
     * Appends content to the default project topic.
     */
    default Future<Void> append(String content) {
        return append(DEFAULT_TOPIC, content);
    }
}
