package com.tfassbender.loreweave.graph;

import com.tfassbender.loreweave.domain.Backlink;
import com.tfassbender.loreweave.domain.ValidationCategory;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class IndexBuilderTest {

    private final IndexBuilder builder = new IndexBuilder();

    @Test
    void buildsValidFixtureVault() {
        Index index = builder.build(Path.of("src/test/resources/vault-valid").toAbsolutePath());

        assertThat(index.size()).isEqualTo(4);
        assertThat(index.notesById().keySet()).containsExactlyInAnyOrder(
                "character_kael_varyn",
                "character_rex_morrow",
                "location_karsis_station",
                "faction_outer_union");

        IndexedNote kael = index.get("character_kael_varyn").orElseThrow();
        assertThat(kael.resolvedLinks())
                .allMatch(ResolvedLink::isResolved);
        assertThat(kael.resolvedLinks())
                .extracting(rl -> rl.targetId().orElse(""))
                .containsExactly("location_karsis_station", "faction_outer_union", "character_rex_morrow");

        // Backlinks: rex links to kael, so kael has a backlink from rex.
        assertThat(kael.backlinks())
                .extracting(Backlink::sourceNoteId)
                .containsExactly("character_rex_morrow");

        IndexedNote union = index.get("faction_outer_union").orElseThrow();
        assertThat(union.backlinks())
                .extracting(Backlink::sourceNoteId)
                .containsExactlyInAnyOrder("character_kael_varyn", "character_rex_morrow");

        // No errors, no warnings on the valid fixture.
        assertThat(index.report().totalErrors()).isZero();
        assertThat(index.report().totalWarnings()).isZero();
    }

    @Test
    void invalidFixtureProducesEveryValidationCategory() {
        Index index = builder.build(Path.of("src/test/resources/vault-invalid").toAbsolutePath());

        // Error categories — every one should fire at least once.
        assertThat(index.report().stats(ValidationCategory.PARSE_ERRORS).count()).isGreaterThanOrEqualTo(1);
        assertThat(index.report().stats(ValidationCategory.MISSING_REQUIRED_FIELDS).count()).isGreaterThanOrEqualTo(1);
        assertThat(index.report().stats(ValidationCategory.INVALID_ID_FORMAT).count()).isGreaterThanOrEqualTo(1);
        // Both dup_a.md and dup_b.md produce a duplicate_ids issue.
        assertThat(index.report().stats(ValidationCategory.DUPLICATE_IDS).count()).isEqualTo(2);
        assertThat(index.report().stats(ValidationCategory.UNRESOLVED_LINKS).count()).isGreaterThanOrEqualTo(1);

        // Warning categories — each of the 'no_*' fixtures fires its category.
        assertThat(index.report().stats(ValidationCategory.MISSING_TITLE).count()).isGreaterThanOrEqualTo(1);
        assertThat(index.report().stats(ValidationCategory.MISSING_SUMMARY).count()).isGreaterThanOrEqualTo(1);
        assertThat(index.report().stats(ValidationCategory.MISSING_SCHEMA_VERSION).count()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void invalidFixtureExcludesErroredAndDuplicateNotesFromServedIndex() {
        Index index = builder.build(Path.of("src/test/resources/vault-invalid").toAbsolutePath());

        Set<String> servedIds = index.notesById().keySet();

        // Error notes must not appear in the served index.
        assertThat(servedIds).doesNotContain("character_dup"); // both duplicates are excluded
        // Warning-only notes remain.
        assertThat(servedIds).contains(
                "character_titleless",
                "character_summaryless",
                "character_schemaless",
                "character_linker"); // the unresolved-link note itself is served
    }

    @Test
    void sampleCapsAtFiveButCountKeepsGoing() {
        ValidationReport.Builder rb = new ValidationReport.Builder();
        for (int i = 0; i < 7; i++) {
            rb.add(com.tfassbender.loreweave.domain.ValidationIssue.error(
                    ValidationCategory.PARSE_ERRORS,
                    Path.of("note-" + i + ".md"),
                    "bad"));
        }
        ValidationReport report = rb.build();
        assertThat(report.stats(ValidationCategory.PARSE_ERRORS).count()).isEqualTo(7);
        assertThat(report.stats(ValidationCategory.PARSE_ERRORS).samples()).hasSize(ValidationReport.MAX_SAMPLES);
    }
}
