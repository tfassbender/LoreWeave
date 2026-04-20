package com.tfassbender.loreweave.graph;

import com.tfassbender.loreweave.config.LoreWeaveConfig;
import com.tfassbender.loreweave.git.GitSyncException;
import com.tfassbender.loreweave.git.GitVaultClient;
import com.tfassbender.loreweave.git.PullResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SyncServiceTest {

    @TempDir
    Path tmp;

    @Test
    void firstSyncBuildsIndexFromLocalVaultWithoutRemote() throws Exception {
        Path vault = tmp.resolve("vault");
        Files.createDirectories(vault);
        Files.writeString(vault.resolve("note.md"), "---\ntype: character\ntitle: X\nsummary: y\nschema_version: 1\n---\n",
                StandardCharsets.UTF_8);

        SyncService svc = new SyncService(config(vault, Optional.empty()), new ThrowingGitClient());

        SyncService.SyncOutcome out = svc.syncNow();
        assertThat(out.index().size()).isEqualTo(1);
        assertThat(out.lastSync().ok()).isTrue();
        assertThat(out.lastSync().updatedFiles()).isZero();
    }

    @Test
    void missingLocalPathAndNoRemoteYieldsEmptyIndexStillOk() {
        Path vault = tmp.resolve("does-not-exist");
        SyncService svc = new SyncService(config(vault, Optional.empty()), new ThrowingGitClient());

        SyncService.SyncOutcome out = svc.syncNow();
        assertThat(out.index().size()).isZero();
        assertThat(out.lastSync().ok()).isTrue();
    }

    @Test
    void syncFailurePreservesPreviousIndex() throws Exception {
        Path vault = tmp.resolve("vault");
        Files.createDirectories(vault);
        Files.writeString(vault.resolve("note.md"), "---\ntype: x\ntitle: t\nsummary: s\nschema_version: 1\n---\n",
                StandardCharsets.UTF_8);
        // Pretend a remote exists and .git is already present so pull is attempted.
        Files.createDirectories(vault.resolve(".git"));

        FailOnPullClient gitClient = new FailOnPullClient(false);
        SyncService svc = new SyncService(config(vault, Optional.of("file:///fake")), gitClient);

        // First sync: clone returns false (.git present), pull succeeds (no-op), index builds.
        svc.syncNow();
        int sizeAfterFirst = svc.currentIndex().size();
        assertThat(sizeAfterFirst).isEqualTo(1);

        // Second sync: pull fails → previous index must be preserved.
        gitClient.failNext = true;
        assertThatThrownBy(svc::syncNow).isInstanceOf(GitSyncException.class);
        assertThat(svc.currentIndex().size()).isEqualTo(sizeAfterFirst);
        assertThat(svc.lastSync().ok()).isFalse();
        assertThat(svc.lastSync().errorMessage()).contains("boom");
    }

    @Test
    void lastSyncStartsAsNever() {
        SyncService svc = new SyncService(config(tmp.resolve("vault"), Optional.empty()), new ThrowingGitClient());
        assertThat(svc.lastSync().ok()).isFalse();
        assertThat(svc.lastSync().errorMessage()).isEqualTo("sync has not run yet");
    }

    @Test
    void successUpdatesChangedFilesFromPull() throws Exception {
        Path vault = tmp.resolve("vault");
        Files.createDirectories(vault);
        Files.createDirectories(vault.resolve(".git"));
        Files.writeString(vault.resolve("note.md"), "---\ntype: x\ntitle: t\nsummary: s\nschema_version: 1\n---\n",
                StandardCharsets.UTF_8);

        CountingGitClient gitClient = new CountingGitClient(7);
        SyncService svc = new SyncService(config(vault, Optional.of("file:///fake")), gitClient);
        SyncService.SyncOutcome out = svc.syncNow();
        assertThat(out.lastSync().ok()).isTrue();
        assertThat(out.lastSync().updatedFiles()).isEqualTo(7);
    }

    // ---- helpers ----

    private LoreWeaveConfig config(Path vault, Optional<String> remote) {
        return new LoreWeaveConfig() {
            @Override public Vault vault() {
                return new Vault() {
                    @Override public Optional<String> remote() { return remote; }
                    @Override public Path localPath() { return vault; }
                };
            }
            @Override public Sync sync() {
                return () -> Duration.ofMinutes(5);
            }
            @Override public Auth auth() {
                return Optional::empty;
            }
            @Override public Logging logging() {
                return () -> tmp.resolve("logs");
            }
        };
    }

    /** A GitVaultClient stub that throws if the sync path tries to call it. */
    private static final class ThrowingGitClient extends GitVaultClient {
        @Override public boolean cloneIfMissing(Path localPath, String remoteUrl) {
            throw new AssertionError("cloneIfMissing unexpectedly called");
        }
        @Override public PullResult pull(Path localPath) {
            throw new AssertionError("pull unexpectedly called");
        }
    }

    /** Reports clone-already-present and either succeeds or fails the pull. */
    private static final class FailOnPullClient extends GitVaultClient {
        boolean failNext;
        FailOnPullClient(boolean failNext) { this.failNext = failNext; }
        @Override public boolean cloneIfMissing(Path localPath, String remoteUrl) { return false; }
        @Override public PullResult pull(Path localPath) {
            if (failNext) throw new GitSyncException("boom");
            return new PullResult("main", "a", "a", 0, false);
        }
    }

    /** Successful client whose pull reports a configurable changed-files count. */
    private static final class CountingGitClient extends GitVaultClient {
        private final int changed;
        private final AtomicInteger pullCalls = new AtomicInteger();
        CountingGitClient(int changed) { this.changed = changed; }
        @Override public boolean cloneIfMissing(Path localPath, String remoteUrl) { return false; }
        @Override public PullResult pull(Path localPath) {
            pullCalls.incrementAndGet();
            return new PullResult("main", "before", "after", changed, false);
        }
    }
}
