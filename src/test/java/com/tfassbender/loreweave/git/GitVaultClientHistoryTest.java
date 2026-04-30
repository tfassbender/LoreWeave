package com.tfassbender.loreweave.git;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.PersonIdent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Covers {@link GitVaultClient#readHistory(Path, int, int, boolean)}. */
class GitVaultClientHistoryTest {

    private final GitVaultClient client = new GitVaultClient();
    private final PersonIdent author = new PersonIdent("Alice", "alice@example.com");

    @TempDir
    Path tmp;

    private Path repo;

    @BeforeEach
    void setUpRepo() throws Exception {
        repo = tmp.resolve("repo");
        Files.createDirectories(repo);
        try (Git git = Git.init().setDirectory(repo.toFile()).setInitialBranch("main").call()) {
            // Commit 1 (root): adds two files.
            write("characters/kael.md", "# Kael\n");
            write("locations/karsis.md", "# Karsis\n");
            git.add().addFilepattern(".").call();
            git.commit().setMessage("initial vault import").setAuthor(author).setCommitter(author).call();

            // Commit 2: modifies kael.
            write("characters/kael.md", "# Kael Varyn\n");
            git.add().addFilepattern(".").call();
            git.commit().setMessage("rename Kael").setAuthor(author).setCommitter(author).call();

            // Commit 3: deletes karsis, adds a new file.
            Files.delete(repo.resolve("locations/karsis.md"));
            write("factions/union.md", "# Union\n");
            git.add().addFilepattern(".").setUpdate(false).call();
            git.add().addFilepattern(".").setUpdate(true).call();
            git.commit().setMessage("rework locations").setAuthor(author).setCommitter(author).call();
        }
    }

    @Test
    void returnsCommitsNewestFirstWithChangedFiles() {
        List<CommitEntry> entries = client.readHistory(repo, 0, 10, true);

        assertThat(entries).hasSize(3);
        assertThat(entries.get(0).message()).isEqualTo("rework locations");
        assertThat(entries.get(1).message()).isEqualTo("rename Kael");
        assertThat(entries.get(2).message()).isEqualTo("initial vault import");
        assertThat(entries.get(0).author()).isEqualTo("Alice");
        assertThat(entries.get(0).sha()).hasSize(40);
        assertThat(entries.get(0).shortSha()).hasSize(7).isEqualTo(entries.get(0).sha().substring(0, 7));
        assertThat(entries.get(0).timestamp()).isNotNull();
    }

    @Test
    void rootCommitReportsAllFilesAsAdded() {
        List<CommitEntry> entries = client.readHistory(repo, 0, 10, true);
        CommitEntry root = entries.get(2);

        assertThat(root.changedFiles())
                .extracting(FileChange::path)
                .containsExactlyInAnyOrder("characters/kael.md", "locations/karsis.md");
        assertThat(root.changedFiles())
                .allMatch(f -> f.change() == FileChange.ChangeType.ADDED);
    }

    @Test
    void modifyAndDeleteAreCategorisedCorrectly() {
        List<CommitEntry> entries = client.readHistory(repo, 0, 10, true);

        CommitEntry modify = entries.get(1);
        assertThat(modify.changedFiles()).hasSize(1);
        assertThat(modify.changedFiles().get(0).path()).isEqualTo("characters/kael.md");
        assertThat(modify.changedFiles().get(0).change()).isEqualTo(FileChange.ChangeType.MODIFIED);

        CommitEntry rework = entries.get(0);
        assertThat(rework.changedFiles())
                .extracting(FileChange::path, FileChange::change)
                .containsExactlyInAnyOrder(
                        org.assertj.core.api.Assertions.tuple("locations/karsis.md", FileChange.ChangeType.DELETED),
                        org.assertj.core.api.Assertions.tuple("factions/union.md", FileChange.ChangeType.ADDED));
    }

    @Test
    void offsetAndLimitPaginate() {
        List<CommitEntry> first = client.readHistory(repo, 0, 2, false);
        List<CommitEntry> second = client.readHistory(repo, 2, 2, false);

        assertThat(first).hasSize(2);
        assertThat(first.get(0).message()).isEqualTo("rework locations");
        assertThat(first.get(1).message()).isEqualTo("rename Kael");
        assertThat(second).hasSize(1);
        assertThat(second.get(0).message()).isEqualTo("initial vault import");
    }

    @Test
    void includeFilesFalseOmitsChangedFiles() {
        List<CommitEntry> entries = client.readHistory(repo, 0, 10, false);

        assertThat(entries).allSatisfy(e -> assertThat(e.changedFiles()).isEmpty());
    }

    @Test
    void missingRepoReturnsEmptyList() {
        Path empty = tmp.resolve("no-repo");
        assertThat(client.readHistory(empty, 0, 10, true)).isEmpty();
    }

    @Test
    void offsetPastEndReturnsEmpty() {
        assertThat(client.readHistory(repo, 999, 10, false)).isEmpty();
    }

    private void write(String relative, String content) throws Exception {
        Path full = repo.resolve(relative);
        Files.createDirectories(full.getParent());
        Files.writeString(full, content, StandardCharsets.UTF_8);
    }
}
