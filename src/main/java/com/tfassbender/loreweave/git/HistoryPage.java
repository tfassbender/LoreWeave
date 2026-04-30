package com.tfassbender.loreweave.git;

import java.util.List;

/**
 * One page of git history.
 *
 * @param offset    zero-based index of the first commit in {@link #commits()}
 *                  within the full log
 * @param pageSize  effective page size after clamping
 * @param hasMore   {@code true} iff at least one commit exists past
 *                  {@code offset + commits.size()}
 * @param commits   commits in newest-first order
 */
public record HistoryPage(int offset, int pageSize, boolean hasMore, List<CommitEntry> commits) {
}
