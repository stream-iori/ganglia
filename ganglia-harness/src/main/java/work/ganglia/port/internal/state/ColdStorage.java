package work.ganglia.port.internal.state;

import io.vertx.core.Future;

/**
 * L2 cold storage for Blackboard fact details. Reads and writes fact detail content by reference
 * path. Implementations may use file system, database, or other persistence mechanisms.
 */
public interface ColdStorage {

  /** Writes detail content to cold storage at the given reference path. */
  Future<Void> write(String detailRef, String content);

  /** Reads detail content from cold storage by reference path. Returns null if not found. */
  Future<String> read(String detailRef);
}
