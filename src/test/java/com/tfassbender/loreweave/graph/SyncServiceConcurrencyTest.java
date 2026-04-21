package com.tfassbender.loreweave.graph;

import com.tfassbender.loreweave.config.LoreWeaveConfig;
import com.tfassbender.loreweave.git.GitVaultClient;
import com.tfassbender.loreweave.git.PullResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the concurrency guarantees of {@link SyncService#syncNow()}: the
 * single-permit semaphore must serialize overlapping calls cleanly, so two
 * concurrent syncs never interleave. We drive this with a fake
 * {@link GitVaultClient} whose pull blocks on a latch; a second caller has to
 * wait for the first to finish before the second pull begins.
 */
class SyncServiceConcurrencyTest {

    @TempDir
    Path tmp;

    @Test
    void concurrentSyncCallsAreSerialized() throws Exception {
        Path vault = tmp.resolve("vault");
        Files.createDirectories(vault);
        Files.createDirectories(vault.resolve(".git"));
        Files.writeString(vault.resolve("note.md"),
                "---\ntype: x\ntitle: T\nsummary: s\nschema_version: 1\n---\n",
                StandardCharsets.UTF_8);

        CountDownLatch pullStarted = new CountDownLatch(1);
        CountDownLatch releasePull = new CountDownLatch(1);
        AtomicInteger inFlightPulls = new AtomicInteger();
        AtomicInteger maxInFlight = new AtomicInteger();
        AtomicInteger totalPulls = new AtomicInteger();

        GitVaultClient latchedClient = new GitVaultClient() {
            @Override
            public boolean cloneIfMissing(Path localPath, String remoteUrl) {
                return false;
            }

            @Override
            public PullResult pull(Path localPath) {
                int now = inFlightPulls.incrementAndGet();
                maxInFlight.updateAndGet(prev -> Math.max(prev, now));
                pullStarted.countDown();
                try {
                    releasePull.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
                totalPulls.incrementAndGet();
                inFlightPulls.decrementAndGet();
                return new PullResult("main", "a", "a", 0, false);
            }
        };

        SyncService svc = new SyncService(config(vault, Optional.of("file:///fake")), latchedClient);

        CompletableFuture<Void> first = CompletableFuture.runAsync(svc::syncNow);
        // Wait for the first sync to enter pull() so the second caller hits a held semaphore.
        assertThat(pullStarted.await(5, TimeUnit.SECONDS)).isTrue();
        CompletableFuture<Void> second = CompletableFuture.runAsync(svc::syncNow);

        // Briefly give the second task a chance to run; it should be waiting on the permit.
        Thread.sleep(200);
        assertThat(inFlightPulls.get()).isEqualTo(1);

        // Release the first sync; the second should proceed.
        releasePull.countDown();
        first.get(5, TimeUnit.SECONDS);
        second.get(5, TimeUnit.SECONDS);

        // Both syncs completed, and at no point were two pulls in flight.
        assertThat(totalPulls.get()).isEqualTo(2);
        assertThat(maxInFlight.get()).isEqualTo(1);
        // The index is still consistent: one note served, lastSync ok.
        assertThat(svc.currentIndex().size()).isEqualTo(1);
        assertThat(svc.lastSync().ok()).isTrue();
    }

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
}
