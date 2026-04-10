package work.ganglia.port.internal.memory;

import io.vertx.core.Future;

/** Interface for the long-term knowledge base. */
public interface LongTermMemory {
  String DEFAULT_TOPIC = "project";
  String USER_PROFILE_TOPIC = "user-profile";

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
   * @param topic The knowledge topic.
   * @param content The content to append.
   * @return A Future completing when the content is appended.
   */
  Future<Void> append(String topic, String content);

  /** Ensures the default project topic is initialized. */
  default Future<Void> ensureInitialized() {
    return ensureInitialized(DEFAULT_TOPIC);
  }

  /** Reads the default project topic content. */
  default Future<String> read() {
    return read(DEFAULT_TOPIC);
  }

  /** Appends content to the default project topic. */
  default Future<Void> append(String content) {
    return append(DEFAULT_TOPIC, content);
  }

  /** Reads the user profile content. */
  default Future<String> readUserProfile() {
    return read(USER_PROFILE_TOPIC);
  }

  /** Appends content to the user profile. */
  default Future<Void> appendUserProfile(String content) {
    return append(USER_PROFILE_TOPIC, content);
  }

  /** Ensures the user profile topic is initialized. */
  default Future<Void> ensureUserProfileInitialized() {
    return ensureInitialized(USER_PROFILE_TOPIC);
  }

  /**
   * Returns the character count of the content for a topic.
   *
   * @param topic The knowledge topic.
   * @return A Future with the character count.
   */
  default Future<Integer> getSize(String topic) {
    return read(topic).map(String::length);
  }

  /**
   * Replaces the entire content of a topic. Used for consolidation/compaction. Default
   * implementation throws UnsupportedOperationException; override in implementations that support
   * replacement.
   *
   * @param topic The knowledge topic.
   * @param content The new content to replace the existing content.
   * @return A Future completing when the replacement is done.
   */
  default Future<Void> replace(String topic, String content) {
    return Future.failedFuture(new UnsupportedOperationException("replace not supported"));
  }
}
