package work.ganglia.infrastructure.internal.worktree;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Future;

import work.ganglia.port.external.tool.CommandExecutor;
import work.ganglia.port.external.tool.ObservationType;
import work.ganglia.port.internal.state.ObservationDispatcher;
import work.ganglia.port.internal.worktree.MergeResult;
import work.ganglia.port.internal.worktree.WorktreeHandle;
import work.ganglia.port.internal.worktree.WorktreeManager;
import work.ganglia.util.PathSanitizer;

/**
 * Git-based WorktreeManager adapter. Creates worktrees under {@code .ganglia/worktrees/} in the
 * repository root.
 *
 * <p>Commands used:
 *
 * <ul>
 *   <li>{@code git worktree add <path> -b <branch>}
 *   <li>{@code git checkout <target> && git merge --no-ff <branch>}
 *   <li>{@code git worktree remove <path> && git branch -d <branch>}
 *   <li>{@code git worktree list --porcelain} for orphan detection
 * </ul>
 */
public class GitWorktreeManager implements WorktreeManager {
  private static final Logger logger = LoggerFactory.getLogger(GitWorktreeManager.class);
  private static final String WORKTREE_DIR = ".ganglia/worktrees";

  private final CommandExecutor commandExecutor;
  private final Path repoRoot;
  private final ObservationDispatcher dispatcher;
  private final String sessionId;

  public GitWorktreeManager(
      CommandExecutor commandExecutor,
      Path repoRoot,
      ObservationDispatcher dispatcher,
      String sessionId) {
    this.commandExecutor = commandExecutor;
    this.repoRoot = repoRoot;
    this.dispatcher = dispatcher;
    this.sessionId = sessionId;
  }

  @Override
  public Future<WorktreeHandle> create(String branchPrefix) {
    String uuid = UUID.randomUUID().toString().substring(0, 8);
    String branchName = branchPrefix + "-" + uuid;
    Path worktreePath = repoRoot.resolve(WORKTREE_DIR).resolve(branchName);

    String command = "git worktree add %s -b %s".formatted(worktreePath.toString(), branchName);

    logger.info("Creating worktree: {} (branch: {})", worktreePath, branchName);

    return commandExecutor
        .execute(command, repoRoot.toString(), null)
        .map(
            result -> {
              if (!result.succeeded()) {
                throw new RuntimeException("Failed to create worktree: " + result.output());
              }

              PathSanitizer scopedMapper = new PathSanitizer(worktreePath.toString());
              WorktreeHandle handle = new WorktreeHandle(worktreePath, branchName, scopedMapper);

              dispatcher.dispatch(
                  sessionId,
                  ObservationType.WORKTREE_CREATED,
                  "Worktree created: " + branchName,
                  Map.of("worktreePath", worktreePath.toString(), "branch", branchName));

              logger.info("Worktree created: {}", worktreePath);
              return handle;
            });
  }

  @Override
  public Future<MergeResult> merge(WorktreeHandle handle, String targetBranch) {
    logger.info("Merging branch {} into {}", handle.branchName(), targetBranch);

    String checkoutCmd = "git checkout %s".formatted(targetBranch);
    String mergeCmd = "git merge --no-ff %s".formatted(handle.branchName());

    return commandExecutor
        .execute(checkoutCmd, repoRoot.toString(), null)
        .compose(checkoutResult -> commandExecutor.execute(mergeCmd, repoRoot.toString(), null))
        .compose(
            mergeResult -> {
              if (mergeResult.succeeded()) {
                return getHeadCommitHash()
                    .map(
                        hash -> {
                          dispatcher.dispatch(
                              sessionId,
                              ObservationType.WORKTREE_MERGE_SUCCESS,
                              "Merged " + handle.branchName(),
                              Map.of("branch", handle.branchName(), "commit", hash));
                          logger.info("Merge successful: {} -> {}", handle.branchName(), hash);
                          return MergeResult.success(hash);
                        });
              }

              // Merge failed — get conflict files, then abort
              return getConflictFiles()
                  .compose(
                      conflictFiles ->
                          abortMerge()
                              .map(
                                  v -> {
                                    dispatcher.dispatch(
                                        sessionId,
                                        ObservationType.WORKTREE_MERGE_CONFLICT,
                                        "Merge conflict: " + handle.branchName(),
                                        Map.of(
                                            "branch", handle.branchName(),
                                            "conflicts", conflictFiles.toString()));
                                    logger.warn(
                                        "Merge conflict for {}: {}",
                                        handle.branchName(),
                                        conflictFiles);
                                    return MergeResult.conflict(conflictFiles);
                                  }));
            });
  }

  @Override
  public Future<Void> cleanup(WorktreeHandle handle) {
    logger.info("Cleaning up worktree: {}", handle.worktreePath());

    String removeCmd = "git worktree remove %s --force".formatted(handle.worktreePath().toString());
    String deleteBranchCmd = "git branch -d %s".formatted(handle.branchName());

    return commandExecutor
        .execute(removeCmd, repoRoot.toString(), null)
        .compose(v -> commandExecutor.execute(deleteBranchCmd, repoRoot.toString(), null))
        .map(
            v -> {
              dispatcher.dispatch(
                  sessionId,
                  ObservationType.WORKTREE_CLEANUP,
                  "Worktree cleaned: " + handle.branchName(),
                  Map.of("branch", handle.branchName()));
              logger.info("Worktree cleaned: {}", handle.branchName());
              return null;
            });
  }

  @Override
  public Future<Void> cleanupOrphans() {
    logger.info("Scanning for orphan worktrees under {}", WORKTREE_DIR);

    return commandExecutor
        .execute("git worktree list --porcelain", repoRoot.toString(), null)
        .compose(
            result -> {
              List<String> orphanPaths = parseOrphanWorktrees(result.output());
              if (orphanPaths.isEmpty()) {
                logger.info("No orphan worktrees found");
                return Future.succeededFuture(null);
              }

              logger.info("Found {} orphan worktrees to clean", orphanPaths.size());
              return cleanupOrphanList(orphanPaths, 0);
            });
  }

  private List<String> parseOrphanWorktrees(String porcelainOutput) {
    String gangliaWorktreeDir = repoRoot.resolve(WORKTREE_DIR).toString();
    return Arrays.stream(porcelainOutput.split("\n"))
        .filter(line -> line.startsWith("worktree "))
        .map(line -> line.substring("worktree ".length()).trim())
        .filter(path -> path.startsWith(gangliaWorktreeDir))
        .toList();
  }

  private Future<Void> cleanupOrphanList(List<String> paths, int index) {
    if (index >= paths.size()) {
      return Future.succeededFuture();
    }

    String path = paths.get(index);
    String branchName = Path.of(path).getFileName().toString();
    String removeCmd = "git worktree remove %s --force".formatted(path);
    String deleteBranchCmd = "git branch -d %s".formatted(branchName);

    logger.info("Removing orphan worktree: {}", path);

    return commandExecutor
        .execute(removeCmd, repoRoot.toString(), null)
        .compose(v -> commandExecutor.execute(deleteBranchCmd, repoRoot.toString(), null))
        .recover(
            err -> {
              logger.warn("Failed to clean orphan {}: {}", path, err.getMessage());
              return Future.succeededFuture(new work.ganglia.util.VertxProcess.Result(0, ""));
            })
        .compose(v -> cleanupOrphanList(paths, index + 1));
  }

  private Future<String> getHeadCommitHash() {
    return commandExecutor
        .execute("git rev-parse HEAD", repoRoot.toString(), null)
        .map(result -> result.output().trim());
  }

  private Future<List<String>> getConflictFiles() {
    return commandExecutor
        .execute("git diff --name-only --diff-filter=U", repoRoot.toString(), null)
        .map(
            result ->
                Arrays.stream(result.output().trim().split("\n"))
                    .filter(s -> !s.isBlank())
                    .toList());
  }

  private Future<Void> abortMerge() {
    return commandExecutor.execute("git merge --abort", repoRoot.toString(), null).mapEmpty();
  }

  @Override
  public Future<Void> revert(String branchName, String targetBranch) {
    logger.info("Reverting merge of branch {} into {}", branchName, targetBranch);

    // First, checkout the target branch to ensure we're on the right branch
    String checkoutCmd = "git checkout %s".formatted(targetBranch);

    return commandExecutor
        .execute(checkoutCmd, repoRoot.toString(), null)
        .compose(
            checkoutResult -> {
              if (!checkoutResult.succeeded()) {
                return Future.failedFuture(
                    "Failed to checkout " + targetBranch + ": " + checkoutResult.output());
              }

              // Find the merge commit (the second parent of HEAD should be the merged branch)
              // Use git log to find the commit that merged the branch
              String findMergeCommit = "git rev-parse --verify %s^{commit}".formatted(branchName);

              return commandExecutor
                  .execute(findMergeCommit, repoRoot.toString(), null)
                  .compose(
                      findResult -> {
                        if (!findResult.succeeded()) {
                          return Future.failedFuture(
                              "Failed to find merge commit for "
                                  + branchName
                                  + ": "
                                  + findResult.output());
                        }

                        String mergeCommit = findResult.output().trim();
                        logger.info("Found merge commit {} for branch {}", mergeCommit, branchName);

                        // Revert the merge commit with -m 1 to keep the mainline (target branch)
                        // -m 1 means we keep the first parent (the target branch state before
                        // merge)
                        String revertCmd = "git revert -m 1 --no-edit %s".formatted(mergeCommit);

                        return commandExecutor
                            .execute(revertCmd, repoRoot.toString(), null)
                            .compose(
                                revertResult -> {
                                  if (!revertResult.succeeded()) {
                                    return Future.failedFuture(
                                        "Failed to revert merge commit "
                                            + mergeCommit
                                            + ": "
                                            + revertResult.output());
                                  }

                                  logger.info(
                                      "Successfully reverted merge of branch {} (commit {})",
                                      branchName,
                                      mergeCommit);
                                  return Future.succeededFuture(null);
                                });
                      });
            });
  }
}
