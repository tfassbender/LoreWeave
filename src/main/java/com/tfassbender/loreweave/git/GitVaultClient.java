package com.tfassbender.loreweave.git;

import com.tfassbender.loreweave.config.LoreWeaveConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.MergeCommand;
import org.eclipse.jgit.api.ResetCommand;
import org.eclipse.jgit.api.TransportCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.util.io.DisabledOutputStream;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

/**
 * Thin JGit wrapper for the two git operations LoreWeave needs: clone-if-missing
 * on boot, and pull on each sync tick.
 *
 * <p>The vault is read-only from LoreWeave's perspective — we never write
 * commits locally — so pulls always try a fast-forward first and fall back to a
 * hard reset against the remote-tracking branch on divergent history. The two
 * outcomes leave the working tree in the same state; the fallback only matters
 * for logging and the {@code force_reset} flag on {@link PullResult}.
 */
@ApplicationScoped
public class GitVaultClient {

    private static final Logger LOG = Logger.getLogger(GitVaultClient.class);

    private final CredentialsProvider credentialsProvider;

    /** No-arg constructor used by tests against unauthenticated local file:// remotes. */
    public GitVaultClient() {
        this.credentialsProvider = null;
    }

    @Inject
    public GitVaultClient(LoreWeaveConfig config) {
        this.credentialsProvider = buildProvider(config);
    }

    private static CredentialsProvider buildProvider(LoreWeaveConfig config) {
        return config.vault().auth().token()
                .filter(t -> !t.isBlank())
                .<CredentialsProvider>map(t -> new UsernamePasswordCredentialsProvider(
                        config.vault().auth().username(), t))
                .orElse(null);
    }

    private <C extends TransportCommand<C, ?>> C withCredentials(C command) {
        if (credentialsProvider != null) {
            command.setCredentialsProvider(credentialsProvider);
        }
        return command;
    }

    /**
     * Clones the remote into {@code localPath} if the directory doesn't yet
     * contain a git repository. Idempotent: safe to call on every sync.
     *
     * @return {@code true} if a clone was performed; {@code false} if the
     *         repository already existed.
     */
    public boolean cloneIfMissing(Path localPath, String remoteUrl) {
        if (localPath == null) throw new IllegalArgumentException("localPath is required");
        if (remoteUrl == null || remoteUrl.isBlank()) {
            throw new IllegalArgumentException("remoteUrl is required for cloneIfMissing");
        }

        if (Files.isDirectory(localPath.resolve(".git"))) {
            return false;
        }

        try {
            Files.createDirectories(localPath);
        } catch (IOException ex) {
            throw new GitSyncException("cannot create local-path " + localPath + ": " + ex.getMessage(), ex);
        }

        if (hasNonGitContent(localPath)) {
            throw new GitSyncException(
                    "local-path " + localPath + " exists, has files, but is not a git repository. "
                            + "Either point local-path at a clean directory or remove the existing files.");
        }

        LOG.infof("Cloning %s into %s", remoteUrl, localPath);
        try (Git ignored = withCredentials(Git.cloneRepository()
                .setURI(remoteUrl)
                .setDirectory(localPath.toFile()))
                .call()) {
            return true;
        } catch (GitAPIException ex) {
            throw new GitSyncException("clone failed for " + remoteUrl + ": " + ex.getMessage(), ex);
        }
    }

    /**
     * Pulls the latest commits into {@code localPath}. Returns a
     * {@link PullResult} describing what moved; throws
     * {@link GitSyncException} on any git-level failure.
     */
    public PullResult pull(Path localPath) {
        if (localPath == null) throw new IllegalArgumentException("localPath is required");
        if (!Files.isDirectory(localPath.resolve(".git"))) {
            throw new GitSyncException("local-path " + localPath + " is not a git repository");
        }

        try (Git git = Git.open(localPath.toFile())) {
            Repository repo = git.getRepository();
            String branch = repo.getBranch();
            ObjectId before = repo.resolve("HEAD");

            // Try fast-forward-only first.
            org.eclipse.jgit.api.PullResult pull;
            try {
                pull = withCredentials(git.pull()
                        .setFastForward(MergeCommand.FastForwardMode.FF_ONLY))
                        .call();
            } catch (GitAPIException ffEx) {
                return forceResetToOrigin(git, repo, branch, before, ffEx.getMessage());
            }

            if (pull.getMergeResult() != null
                    && !pull.getMergeResult().getMergeStatus().isSuccessful()) {
                String why = pull.getMergeResult().getMergeStatus().name();
                return forceResetToOrigin(git, repo, branch, before, why);
            }

            ObjectId after = repo.resolve("HEAD");
            int changed = countChangedFiles(repo, before, after);
            return new PullResult(branch, sha(before), sha(after), changed, false);
        } catch (IOException ex) {
            throw new GitSyncException("failed to open repo at " + localPath + ": " + ex.getMessage(), ex);
        }
    }

    private PullResult forceResetToOrigin(Git git, Repository repo, String branch, ObjectId before, String reason) {
        LOG.warnf("Fast-forward pull failed (%s); falling back to hard reset on origin/%s", reason, branch);
        try {
            withCredentials(git.fetch()).call();
            ObjectId remoteHead = repo.resolve("refs/remotes/origin/" + branch);
            if (remoteHead == null) {
                throw new GitSyncException("remote-tracking ref refs/remotes/origin/" + branch + " is missing");
            }
            git.reset()
                    .setMode(ResetCommand.ResetType.HARD)
                    .setRef(remoteHead.name())
                    .call();
            ObjectId after = repo.resolve("HEAD");
            int changed = countChangedFiles(repo, before, after);
            return new PullResult(branch, sha(before), sha(after), changed, true);
        } catch (GitAPIException | IOException ex) {
            throw new GitSyncException("hard-reset fallback failed: " + ex.getMessage(), ex);
        }
    }

    /**
     * Reads {@code limit} commits from the {@code HEAD} log, skipping the first
     * {@code offset}, newest-first. When {@code includeFiles} is {@code true},
     * each entry is populated with the files it changed against its first
     * parent (root commit → empty list; merges are diffed against the first
     * parent only, mirroring {@code git log --first-parent}).
     *
     * <p>Returns an empty list when {@code localPath} does not contain a git
     * repository, so callers can treat "no vault yet" the same way {@code /sync}
     * does — without erroring.
     */
    public List<CommitEntry> readHistory(Path localPath, int offset, int limit, boolean includeFiles) {
        if (localPath == null) throw new IllegalArgumentException("localPath is required");
        if (offset < 0) throw new IllegalArgumentException("offset must be >= 0");
        if (limit <= 0) return List.of();
        if (!Files.isDirectory(localPath.resolve(".git"))) {
            return List.of();
        }

        try (Git git = Git.open(localPath.toFile())) {
            Iterable<RevCommit> log = git.log().setSkip(offset).setMaxCount(limit).call();
            List<CommitEntry> entries = new ArrayList<>();
            Repository repo = git.getRepository();
            try (DiffFormatter diff = new DiffFormatter(DisabledOutputStream.INSTANCE)) {
                diff.setRepository(repo);
                diff.setDetectRenames(true);
                for (RevCommit commit : log) {
                    entries.add(toEntry(repo, diff, commit, includeFiles));
                }
            }
            return entries;
        } catch (IOException | GitAPIException ex) {
            throw new GitSyncException("failed to read git log at " + localPath + ": " + ex.getMessage(), ex);
        }
    }

    private static CommitEntry toEntry(Repository repo, DiffFormatter diff, RevCommit commit, boolean includeFiles) throws IOException {
        String sha = commit.getId().name();
        String shortSha = sha.substring(0, 7);
        String message = commit.getFullMessage().stripTrailing();
        String author = commit.getAuthorIdent() != null ? commit.getAuthorIdent().getName() : "";
        Instant ts = commit.getAuthorIdent() != null
                ? commit.getAuthorIdent().getWhenAsInstant()
                : Instant.ofEpochSecond(commit.getCommitTime());
        List<FileChange> files = includeFiles ? changedFilesFirstParent(repo, diff, commit) : List.of();
        return new CommitEntry(sha, shortSha, message, author, ts, files);
    }

    private static List<FileChange> changedFilesFirstParent(Repository repo, DiffFormatter diff, RevCommit commit) throws IOException {
        try (RevWalk walk = new RevWalk(repo);
             org.eclipse.jgit.lib.ObjectReader reader = repo.newObjectReader()) {
            CanonicalTreeParser newTree = new CanonicalTreeParser();
            newTree.reset(reader, commit.getTree().getId());

            CanonicalTreeParser oldTree;
            if (commit.getParentCount() == 0) {
                // Root commit: diff against the empty tree → every file appears as ADDED.
                oldTree = new CanonicalTreeParser();
            } else {
                RevCommit parent = walk.parseCommit(commit.getParent(0).getId());
                oldTree = new CanonicalTreeParser();
                oldTree.reset(reader, parent.getTree().getId());
            }

            List<DiffEntry> diffs = diff.scan(oldTree, newTree);
            List<FileChange> changes = new ArrayList<>(diffs.size());
            for (DiffEntry de : diffs) {
                String path = de.getChangeType() == DiffEntry.ChangeType.DELETE ? de.getOldPath() : de.getNewPath();
                changes.add(new FileChange(path, mapChangeType(de.getChangeType())));
            }
            return Collections.unmodifiableList(changes);
        }
    }

    private static FileChange.ChangeType mapChangeType(DiffEntry.ChangeType jgit) {
        return switch (jgit) {
            case ADD -> FileChange.ChangeType.ADDED;
            case MODIFY -> FileChange.ChangeType.MODIFIED;
            case DELETE -> FileChange.ChangeType.DELETED;
            case RENAME -> FileChange.ChangeType.RENAMED;
            case COPY -> FileChange.ChangeType.COPIED;
        };
    }

    private static String sha(ObjectId id) {
        return id == null ? "" : id.name();
    }

    private static int countChangedFiles(Repository repo, ObjectId from, ObjectId to) {
        if (from == null || to == null || from.equals(to)) return 0;
        try (RevWalk walk = new RevWalk(repo);
             org.eclipse.jgit.lib.ObjectReader reader = repo.newObjectReader()) {
            RevCommit a = walk.parseCommit(from);
            RevCommit b = walk.parseCommit(to);
            CanonicalTreeParser aTree = new CanonicalTreeParser();
            aTree.reset(reader, a.getTree().getId());
            CanonicalTreeParser bTree = new CanonicalTreeParser();
            bTree.reset(reader, b.getTree().getId());
            try (Git git = new Git(repo)) {
                return git.diff()
                        .setOldTree(aTree)
                        .setNewTree(bTree)
                        .call()
                        .size();
            }
        } catch (IOException | GitAPIException ex) {
            LOG.debugf(ex, "could not count changed files between %s and %s", sha(from), sha(to));
            return -1;
        }
    }

    private static boolean hasNonGitContent(Path dir) {
        try (Stream<Path> entries = Files.list(dir)) {
            return entries.findAny().isPresent();
        } catch (IOException ex) {
            return false;
        }
    }

}
