package com.tfassbender.loreweave.git;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.MergeCommand;
import org.eclipse.jgit.api.ResetCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
        try (Git ignored = Git.cloneRepository()
                .setURI(remoteUrl)
                .setDirectory(localPath.toFile())
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
                pull = git.pull()
                        .setFastForward(MergeCommand.FastForwardMode.FF_ONLY)
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
            git.fetch().call();
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
