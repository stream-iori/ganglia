package work.ganglia.port.internal.worktree;

import io.vertx.core.Future;

/**
 * Port interface for managing git worktrees used for parallel write-task isolation.
 *
 * <p>Worktree lifecycle:
 *
 * <ol>
 *   <li>{@link #create(String)} — create a new worktree with a unique branch
 *   <li>Execute tasks within the worktree's scoped PathMapper
 *   <li>{@link #merge(WorktreeHandle, String)} — merge the worktree branch into target
 *   <li>{@link #cleanup(WorktreeHandle)} — remove the worktree and its branch
 * </ol>
 */
public interface WorktreeManager {

  /**
   * Creates a new git worktree with a unique branch.
   *
   * @param branchPrefix prefix for the branch name (e.g., node ID)
   * @return handle to the created worktree
   */
  Future<WorktreeHandle> create(String branchPrefix);

  /**
   * Merges the worktree branch into the target branch using --no-ff. On conflict, performs merge
   * --abort and returns failure.
   *
   * @param handle the worktree to merge
   * @param targetBranch the branch to merge into (e.g., "main")
   * @return merge result with success/conflict info
   */
  Future<MergeResult> merge(WorktreeHandle handle, String targetBranch);

  /**
   * Removes the worktree directory and deletes the branch.
   *
   * @param handle the worktree to clean up
   */
  Future<Void> cleanup(WorktreeHandle handle);

  /**
   * Removes any orphaned worktrees from previous crashes. Called at engine startup for crash
   * recovery.
   */
  Future<Void> cleanupOrphans();

  /**
   * Reverts a merge commit from a specific branch.
   *
   * @param branchName the branch whose merge should be reverted
   * @param targetBranch the target branch that was merged into
   * @return succeeded future on success, failed future if revert fails
   */
  Future<Void> revert(String branchName, String targetBranch);
}
