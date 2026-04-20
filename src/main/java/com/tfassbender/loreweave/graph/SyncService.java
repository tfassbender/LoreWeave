package com.tfassbender.loreweave.graph;

import com.tfassbender.loreweave.config.LoreWeaveConfig;
import com.tfassbender.loreweave.git.GitSyncException;
import com.tfassbender.loreweave.git.GitVaultClient;
import com.tfassbender.loreweave.git.PullResult;
import io.quarkus.runtime.Startup;
import io.quarkus.scheduler.Scheduled;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Owns the live {@link Index} and the most-recent {@link LastSync} outcome.
 * Every sync — manual or scheduled — goes through {@link #syncNow()}, which is
 * serialized via a single-permit semaphore so concurrent requests can't tear
 * the index.
 *
 * <p>Resolution of what each sync actually does:
 * <ul>
 *   <li>Remote set + local {@code .git} missing and dir empty → clone, then scan.</li>
 *   <li>Remote set + local {@code .git} present → pull fast-forward (with hard-reset fallback on divergence), then scan.</li>
 *   <li>Remote set + local dir non-empty with no {@code .git} → {@link GitSyncException} (operator config error).</li>
 *   <li>Remote blank + local dir exists → scan whatever is there; no git traffic.</li>
 *   <li>Remote blank + local dir missing → serve an empty index; still reports success.</li>
 * </ul>
 *
 * <p>If any step fails we keep the previous {@link Index} and record the
 * failure on {@link #lastSync()}. Availability is not sacrificed for freshness.
 */
@ApplicationScoped
@Startup
public class SyncService {

    private static final Logger LOG = Logger.getLogger(SyncService.class);

    private final LoreWeaveConfig config;
    private final GitVaultClient git;
    private final IndexBuilder indexBuilder;
    private final Semaphore permit = new Semaphore(1, true);

    private volatile Index currentIndex = new Index(Map.of(), new com.tfassbender.loreweave.graph.ValidationReport(Map.of()));
    private volatile LastSync lastSync = LastSync.never();

    @Inject
    public SyncService(LoreWeaveConfig config, GitVaultClient git) {
        this(config, git, new IndexBuilder());
    }

    /** Constructor for unit tests — avoids CDI bootstrap. */
    public SyncService(LoreWeaveConfig config, GitVaultClient git, IndexBuilder indexBuilder) {
        this.config = config;
        this.git = git;
        this.indexBuilder = indexBuilder;
    }

    @PostConstruct
    void bootstrap() {
        try {
            syncNow();
        } catch (RuntimeException ex) {
            LOG.warnf("Bootstrap sync failed: %s (serving empty index until next sync)", ex.getMessage());
        }
    }

    public Index currentIndex() {
        return currentIndex;
    }

    public LastSync lastSync() {
        return lastSync;
    }

    /**
     * Runs a full sync (clone-if-missing → pull → scan → build → swap).
     * Concurrent callers block up to 30 s for the semaphore; if that fails,
     * a {@link SyncInProgressException} is thrown so HTTP callers can map
     * it to 409 without hanging the event loop.
     */
    public SyncOutcome syncNow() {
        boolean acquired;
        try {
            acquired = permit.tryAcquire(30, TimeUnit.SECONDS);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new SyncInProgressException("interrupted while waiting for sync permit");
        }
        if (!acquired) {
            throw new SyncInProgressException("another sync is in progress");
        }
        try {
            return doSync();
        } finally {
            permit.release();
        }
    }

    private SyncOutcome doSync() {
        Path localPath = config.vault().localPath();
        Optional<String> remote = config.vault().remote().filter(s -> !s.isBlank());
        int changedFiles = 0;

        try {
            if (remote.isPresent()) {
                boolean cloned = git.cloneIfMissing(localPath, remote.get());
                if (!cloned) {
                    PullResult pr = git.pull(localPath);
                    changedFiles = Math.max(pr.changedFiles(), 0);
                }
            } else if (!Files.isDirectory(localPath)) {
                LOG.infof("No remote and local-path %s does not exist; serving empty index", localPath);
                currentIndex = new Index(Map.of(), new com.tfassbender.loreweave.graph.ValidationReport(Map.of()));
                lastSync = LastSync.success(Instant.now(), 0);
                return new SyncOutcome(currentIndex, lastSync);
            }

            Index built = indexBuilder.build(localPath);
            currentIndex = built;
            lastSync = LastSync.success(Instant.now(), changedFiles);
            LOG.infof("Sync ok: %d notes served, %d files changed", built.size(), changedFiles);
            return new SyncOutcome(built, lastSync);
        } catch (GitSyncException gse) {
            lastSync = LastSync.failure(Instant.now(), gse.getMessage());
            LOG.errorf("Sync failed: %s (previous index retained: %d notes)",
                    gse.getMessage(), currentIndex.size());
            throw gse;
        } catch (RuntimeException ex) {
            lastSync = LastSync.failure(Instant.now(), ex.getClass().getSimpleName() + ": " + ex.getMessage());
            LOG.errorf(ex, "Sync failed unexpectedly (previous index retained: %d notes)", currentIndex.size());
            throw ex;
        }
    }

    @Scheduled(every = "{loreweave.sync.interval}", delay = 30, delayUnit = TimeUnit.SECONDS)
    void scheduledSync() {
        if (!permit.tryAcquire()) {
            LOG.debug("Scheduled sync skipped — another sync in progress");
            return;
        }
        try {
            doSync();
        } catch (RuntimeException ex) {
            // lastSync already records the failure; don't rethrow to keep the scheduler alive.
            LOG.debugf("Scheduled sync threw %s (recorded in lastSync)", ex.getClass().getSimpleName());
        } finally {
            permit.release();
        }
    }

    public record SyncOutcome(Index index, LastSync lastSync) {}
}
