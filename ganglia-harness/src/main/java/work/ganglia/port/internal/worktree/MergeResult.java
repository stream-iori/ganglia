package work.ganglia.port.internal.worktree;

import java.util.List;

/**
 * Result of merging a worktree branch into a target branch.
 *
 * @param success true if the merge completed without conflicts
 * @param conflictFiles list of conflicting file paths (empty if success)
 * @param mergeCommitHash the merge commit hash (null if merge failed)
 */
public record MergeResult(boolean success, List<String> conflictFiles, String mergeCommitHash) {

  public static MergeResult success(String commitHash) {
    return new MergeResult(true, List.of(), commitHash);
  }

  public static MergeResult conflict(List<String> conflictFiles) {
    return new MergeResult(false, conflictFiles, null);
  }
}
