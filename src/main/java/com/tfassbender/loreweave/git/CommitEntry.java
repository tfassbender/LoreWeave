package com.tfassbender.loreweave.git;

import java.time.Instant;
import java.util.List;

/**
 * A single entry from {@code git log}, newest-first.
 *
 * @param sha           full 40-char commit SHA
 * @param shortSha      abbreviated 7-char SHA for display
 * @param message       full commit message (subject + body, as JGit returns it)
 * @param author        author display name (no email)
 * @param timestamp     author timestamp
 * @param changedFiles  files touched relative to the first parent; empty for the
 *                      root commit, or whenever {@code includeFiles=false} was
 *                      passed to {@link GitVaultClient#readHistory(int, int, boolean)}
 */
public record CommitEntry(
        String sha,
        String shortSha,
        String message,
        String author,
        Instant timestamp,
        List<FileChange> changedFiles) {
}
