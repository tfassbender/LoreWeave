package com.tfassbender.loreweave.git;

import com.tfassbender.loreweave.config.LoreWeaveConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;

/**
 * Pages over the git log on disk. Concurrency-safe with respect to {@code /sync}
 * — JGit reads commits from the object DB, so a sync that fast-forwards or
 * resets HEAD does not break a reader.
 *
 * <p>Pagination is offset + page-size (newest commit at offset 0). Page-size
 * above {@link LoreWeaveConfig.History#maxPageSize()} is clamped silently,
 * mirroring how {@code /search} and {@code /related} clamp their limits.
 * {@code has_more} is computed by requesting one extra commit from the log
 * and checking whether it materialised.
 *
 * <p>Argument validation (negative offset, zero page-size) is the caller's
 * responsibility; this service treats its inputs as already-sanitized.
 */
@ApplicationScoped
public class HistoryService {

    private final LoreWeaveConfig config;
    private final GitVaultClient git;

    @Inject
    public HistoryService(LoreWeaveConfig config, GitVaultClient git) {
        this.config = config;
        this.git = git;
    }

    public int defaultPageSize() {
        return config.history().defaultPageSize();
    }

    public int maxPageSize() {
        return config.history().maxPageSize();
    }

    public HistoryPage page(int offset, int pageSize, boolean includeFiles) {
        if (offset < 0) throw new IllegalArgumentException("offset must be >= 0");
        if (pageSize < 1) throw new IllegalArgumentException("pageSize must be >= 1");

        int effectiveSize = Math.min(pageSize, maxPageSize());

        List<CommitEntry> commits = git.readHistory(
                config.vault().localPath(),
                offset,
                effectiveSize + 1,
                includeFiles);

        boolean hasMore = commits.size() > effectiveSize;
        List<CommitEntry> page = hasMore ? commits.subList(0, effectiveSize) : commits;
        return new HistoryPage(offset, effectiveSize, hasMore, List.copyOf(page));
    }
}
