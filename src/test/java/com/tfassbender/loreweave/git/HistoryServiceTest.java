package com.tfassbender.loreweave.git;

import com.tfassbender.loreweave.config.LoreWeaveConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HistoryServiceTest {

    @TempDir
    Path tmp;

    @Test
    void clampsRequestedPageSizeToConfiguredMax() {
        FakeGit git = new FakeGit(commits(20));
        HistoryService svc = new HistoryService(config(tmp, 10, 5), git);

        HistoryPage page = svc.page(0, 100, false);

        assertThat(page.pageSize()).isEqualTo(5);
        assertThat(page.commits()).hasSize(5);
        assertThat(page.hasMore()).isTrue();
        // Should request maxSize + 1 from JGit so it can decide has_more.
        assertThat(git.lastLimit).isEqualTo(6);
    }

    @Test
    void hasMoreFalseAtEndOfLog() {
        FakeGit git = new FakeGit(commits(3));
        HistoryService svc = new HistoryService(config(tmp, 10, 20), git);

        HistoryPage page = svc.page(0, 10, false);

        assertThat(page.commits()).hasSize(3);
        assertThat(page.hasMore()).isFalse();
    }

    @Test
    void hasMoreTrueWhenExactlyOneExtraExists() {
        FakeGit git = new FakeGit(commits(11));
        HistoryService svc = new HistoryService(config(tmp, 10, 20), git);

        HistoryPage page = svc.page(0, 10, false);

        assertThat(page.commits()).hasSize(10);
        assertThat(page.hasMore()).isTrue();
    }

    @Test
    void rejectsNegativeOffset() {
        HistoryService svc = new HistoryService(config(tmp, 10, 20), new FakeGit(List.of()));
        assertThatThrownBy(() -> svc.page(-1, 10, false))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsZeroPageSize() {
        HistoryService svc = new HistoryService(config(tmp, 10, 20), new FakeGit(List.of()));
        assertThatThrownBy(() -> svc.page(0, 0, false))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void offsetIsForwardedToGitClient() {
        FakeGit git = new FakeGit(commits(20));
        HistoryService svc = new HistoryService(config(tmp, 10, 20), git);

        svc.page(7, 5, true);

        assertThat(git.lastOffset).isEqualTo(7);
        assertThat(git.lastIncludeFiles).isTrue();
    }

    private static List<CommitEntry> commits(int n) {
        return java.util.stream.IntStream.range(0, n)
                .mapToObj(i -> new CommitEntry(
                        String.format("%040d", i),
                        String.format("%07d", i),
                        "msg " + i,
                        "Alice",
                        Instant.EPOCH,
                        List.of()))
                .toList();
    }

    private static LoreWeaveConfig config(Path local, int defaultSize, int maxSize) {
        return new LoreWeaveConfig() {
            @Override public Vault vault() {
                return new Vault() {
                    @Override public Optional<String> remote() { return Optional.empty(); }
                    @Override public Path localPath() { return local; }
                    @Override public Auth auth() {
                        return new Auth() {
                            @Override public String username() { return "x-access-token"; }
                            @Override public Optional<String> token() { return Optional.empty(); }
                        };
                    }
                };
            }
            @Override public Sync sync() { return () -> Duration.ofMinutes(5); }
            @Override public Auth auth() { return Optional::empty; }
            @Override public Logging logging() { return () -> Path.of("./logs"); }
            @Override public History history() {
                return new History() {
                    @Override public int defaultPageSize() { return defaultSize; }
                    @Override public int maxPageSize() { return maxSize; }
                };
            }
        };
    }

    /** Test double for {@link GitVaultClient}: returns a fixed list, sliced by offset/limit. */
    private static class FakeGit extends GitVaultClient {
        private final List<CommitEntry> all;
        int lastOffset = -1;
        int lastLimit = -1;
        boolean lastIncludeFiles;
        final AtomicInteger calls = new AtomicInteger();

        FakeGit(List<CommitEntry> all) {
            this.all = all;
        }

        @Override
        public List<CommitEntry> readHistory(Path localPath, int offset, int limit, boolean includeFiles) {
            calls.incrementAndGet();
            this.lastOffset = offset;
            this.lastLimit = limit;
            this.lastIncludeFiles = includeFiles;
            int from = Math.min(offset, all.size());
            int to = Math.min(from + limit, all.size());
            return all.subList(from, to);
        }
    }
}
