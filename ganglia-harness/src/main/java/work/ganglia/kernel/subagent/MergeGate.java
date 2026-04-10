package work.ganglia.kernel.subagent;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Future;

import work.ganglia.kernel.task.AgentTaskResult;
import work.ganglia.port.internal.worktree.WorktreeHandle;
import work.ganglia.port.internal.worktree.WorktreeManager;

/**
 * Sequential merge gate: merges worktree branches one-by-one into the target branch, running
 * validation after each successful merge. On merge conflict or validation failure, the branch is
 * skipped and recorded as a failed merge.
 */
public class MergeGate {
  private static final Logger logger = LoggerFactory.getLogger(MergeGate.class);

  /** Validation callback invoked after each successful merge. */
  public interface Validator {
    Future<AgentTaskResult> validate();
  }

  /** Result of merging all worktree branches. */
  public record MergeGateResult(boolean allMerged, int mergedCount, List<String> failedMerges) {}

  private final WorktreeManager worktreeManager;
  private final Validator validator;
  private final String targetBranch;

  public MergeGate(WorktreeManager worktreeManager, Validator validator, String targetBranch) {
    this.worktreeManager = worktreeManager;
    this.validator = validator;
    this.targetBranch = targetBranch;
  }

  /**
   * Merges all worktree branches sequentially into the target branch. After each successful merge,
   * runs the validator. If validation fails, records the branch as a failed merge.
   *
   * @param handles the worktree handles to merge
   * @return result summarizing merged and failed branches
   */
  public Future<MergeGateResult> mergeAll(List<WorktreeHandle> handles) {
    return mergeSequential(handles, 0, 0, new ArrayList<>());
  }

  private Future<MergeGateResult> mergeSequential(
      List<WorktreeHandle> handles, int index, int mergedCount, List<String> failedMerges) {
    if (index >= handles.size()) {
      return Future.succeededFuture(
          new MergeGateResult(failedMerges.isEmpty(), mergedCount, failedMerges));
    }

    WorktreeHandle handle = handles.get(index);

    return worktreeManager
        .merge(handle, targetBranch)
        .compose(
            mergeResult -> {
              if (!mergeResult.success()) {
                failedMerges.add(
                    handle.branchName() + ": merge conflict — " + mergeResult.conflictFiles());
                return worktreeManager
                    .cleanup(handle)
                    .compose(v -> mergeSequential(handles, index + 1, mergedCount, failedMerges));
              }

              // Merge succeeded — run validation
              return validator
                  .validate()
                  .compose(
                      validationResult -> {
                        if (validationResult.status() == AgentTaskResult.Status.SUCCESS) {
                          return worktreeManager
                              .cleanup(handle)
                              .compose(
                                  v ->
                                      mergeSequential(
                                          handles, index + 1, mergedCount + 1, failedMerges));
                        }

                        // Validation failed — revert the merge, then cleanup
                        logger.info(
                            "Validation failed for branch {}, reverting merge",
                            handle.branchName());
                        failedMerges.add(
                            handle.branchName()
                                + ": validation failed — "
                                + validationResult.output());
                        return worktreeManager
                            .revert(handle.branchName(), targetBranch)
                            .recover(
                                err -> {
                                  logger.warn(
                                      "Failed to revert merge for branch {}: {}",
                                      handle.branchName(),
                                      err.getMessage());
                                  // Continue even if revert fails
                                  return Future.succeededFuture(null);
                                })
                            .compose(
                                v ->
                                    worktreeManager
                                        .cleanup(handle)
                                        .recover(
                                            cleanupErr -> {
                                              logger.warn(
                                                  "Failed to cleanup worktree {}: {}",
                                                  handle.branchName(),
                                                  cleanupErr.getMessage());
                                              return Future.succeededFuture(null);
                                            }))
                            .compose(
                                v ->
                                    mergeSequential(handles, index + 1, mergedCount, failedMerges));
                      });
            });
  }
}
