package work.ganglia.port.internal.worktree;

import java.nio.file.Path;

import work.ganglia.util.PathMapper;

/**
 * Handle to a git worktree created for isolated parallel execution.
 *
 * @param worktreePath absolute path to the worktree directory
 * @param branchName the branch name created for this worktree
 * @param scopedMapper PathMapper rooted at worktreePath for sandboxed file access
 */
public record WorktreeHandle(Path worktreePath, String branchName, PathMapper scopedMapper) {}
