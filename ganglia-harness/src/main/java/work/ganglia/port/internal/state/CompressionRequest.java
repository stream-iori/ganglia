package work.ganglia.port.internal.state;

import java.util.List;

import work.ganglia.port.chat.SessionContext;
import work.ganglia.port.chat.Turn;

/**
 * Request to compress a set of turns.
 *
 * @param toCompress the turns to compress
 * @param toKeep the turns to preserve unchanged
 * @param context the full session context
 * @param preTokens estimated tokens in the turns to compress
 * @param isForced whether this is a forced compression (aggressive mode)
 */
public record CompressionRequest(
    List<Turn> toCompress,
    List<Turn> toKeep,
    SessionContext context,
    int preTokens,
    boolean isForced) {

  /** Returns the number of turns to compress. */
  public int turnCount() {
    return toCompress.size();
  }
}
