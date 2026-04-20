package com.tfassbender.loreweave.git;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.PersonIdent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GitVaultClientTest {

    private final GitVaultClient client = new GitVaultClient();
    private final PersonIdent author = new PersonIdent("t", "t@t");

    @TempDir
    Path tmp;

    private Path remoteDir;
    private String remoteUri;

    @BeforeEach
    void setUpRemote() throws Exception {
        remoteDir = tmp.resolve("remote");
        Files.createDirectories(remoteDir);
        try (Git git = Git.init().setDirectory(remoteDir.toFile()).setInitialBranch("main").call()) {
            writeFile(remoteDir, "characters/kael.md", "---\ntype: character\ntitle: Kael\n---\n");
            git.add().addFilepattern(".").call();
            git.commit().setMessage("initial").setAuthor(author).setCommitter(author).call();
        }
        remoteUri = remoteDir.toUri().toString();
    }

    @Test
    void cloneIfMissingPopulatesLocalPath() {
        Path local = tmp.resolve("local");
        boolean cloned = client.cloneIfMissing(local, remoteUri);
        assertThat(cloned).isTrue();
        assertThat(Files.isDirectory(local.resolve(".git"))).isTrue();
        assertThat(Files.exists(local.resolve("characters/kael.md"))).isTrue();
    }

    @Test
    void cloneIfMissingIsNoOpOnExistingRepo() {
        Path local = tmp.resolve("local");
        client.cloneIfMissing(local, remoteUri);
        assertThat(client.cloneIfMissing(local, remoteUri)).isFalse();
    }

    @Test
    void cloneRejectsPopulatedNonGitDirectory() throws Exception {
        Path local = tmp.resolve("local");
        Files.createDirectories(local);
        Files.writeString(local.resolve("stray.txt"), "oops");
        assertThatThrownBy(() -> client.cloneIfMissing(local, remoteUri))
                .isInstanceOf(GitSyncException.class)
                .hasMessageContaining("not a git repository");
    }

    @Test
    void pullFastForwardsNewCommits() throws Exception {
        Path local = tmp.resolve("local");
        client.cloneIfMissing(local, remoteUri);

        // Add a second commit in the remote.
        try (Git git = Git.open(remoteDir.toFile())) {
            writeFile(remoteDir, "locations/karsis.md", "---\ntype: location\ntitle: Karsis\n---\n");
            git.add().addFilepattern(".").call();
            git.commit().setMessage("add karsis").setAuthor(author).setCommitter(author).call();
        }

        PullResult result = client.pull(local);
        assertThat(result.upToDate()).isFalse();
        assertThat(result.forceReset()).isFalse();
        assertThat(Files.exists(local.resolve("locations/karsis.md"))).isTrue();
    }

    @Test
    void pullOnUpToDateRepoReturnsSameRef() {
        Path local = tmp.resolve("local");
        client.cloneIfMissing(local, remoteUri);

        PullResult result = client.pull(local);
        assertThat(result.upToDate()).isTrue();
        assertThat(result.changedFiles()).isZero();
    }

    @Test
    void pullOnNonRepoDirectoryThrows() throws Exception {
        Path local = tmp.resolve("empty");
        Files.createDirectories(local);
        assertThatThrownBy(() -> client.pull(local))
                .isInstanceOf(GitSyncException.class)
                .hasMessageContaining("not a git repository");
    }

    @Test
    void cloneFromInvalidRemoteThrowsTypedException() {
        Path local = tmp.resolve("local");
        assertThatThrownBy(() -> client.cloneIfMissing(local, "file:///definitely/does/not/exist"))
                .isInstanceOf(GitSyncException.class);
    }

    private static void writeFile(Path root, String relative, String content) throws Exception {
        Path full = root.resolve(relative);
        Files.createDirectories(full.getParent());
        Files.writeString(full, content, StandardCharsets.UTF_8);
    }
}
