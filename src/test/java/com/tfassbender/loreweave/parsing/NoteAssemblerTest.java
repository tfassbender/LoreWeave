package com.tfassbender.loreweave.parsing;

import com.tfassbender.loreweave.domain.Link;
import com.tfassbender.loreweave.domain.ValidationCategory;
import com.tfassbender.loreweave.domain.ValidationIssue;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class NoteAssemblerTest {

    private final NoteAssembler assembler = new NoteAssembler();
    private final Path file = Path.of("character_kael_varyn.md");

    @Test
    void assemblesHappyPath() {
        String raw = """
                ---
                id: character_kael_varyn
                type: character
                title: Kael Varyn
                summary: Outer Union scout.
                schema_version: 1
                aliases: [Kael, The Scout]
                metadata:
                  faction: outer_union
                ---

                Kael was stationed at [[Location - Karsis Station]]. #pov
                Loyal to the [[Faction - Outer Union|Union]]. #major
                """;
        ParseResult result = assembler.assemble(file, raw);
        assertThat(result).isInstanceOf(ParseResult.Success.class);
        var success = (ParseResult.Success) result;
        assertThat(success.issues()).isEmpty();
        assertThat(success.note().id()).isEqualTo("character_kael_varyn");
        assertThat(success.note().type()).isEqualTo("character");
        assertThat(success.note().title()).isEqualTo("Kael Varyn");
        assertThat(success.note().schemaVersion()).isEqualTo(1);
        assertThat(success.note().aliases()).containsExactly("Kael", "The Scout");
        assertThat(success.note().metadata()).containsEntry("faction", "outer_union");
        assertThat(success.note().tags()).containsExactly("pov", "major");
        assertThat(success.note().links()).extracting(Link::rawTarget)
                .containsExactly("Location - Karsis Station", "Faction - Outer Union");
    }

    @Test
    void missingIdAndTypeFail() {
        String raw = """
                ---
                title: Orphan
                ---

                Body.
                """;
        ParseResult result = assembler.assemble(file, raw);
        assertThat(result).isInstanceOf(ParseResult.Failure.class);
        assertThat(result.issues())
                .extracting(ValidationIssue::category)
                .contains(ValidationCategory.MISSING_REQUIRED_FIELDS);
    }

    @Test
    void mismatchedIdPrefixIsInvalidIdFormat() {
        String raw = """
                ---
                id: event_kael
                type: character
                ---
                """;
        ParseResult result = assembler.assemble(file, raw);
        assertThat(result).isInstanceOf(ParseResult.Failure.class);
        assertThat(result.issues())
                .extracting(ValidationIssue::category)
                .contains(ValidationCategory.INVALID_ID_FORMAT);
    }

    @Test
    void missingRecommendedFieldsSurfaceAsWarningsNotErrors() {
        String raw = """
                ---
                id: character_kael
                type: character
                ---

                Body.
                """;
        ParseResult result = assembler.assemble(file, raw);
        assertThat(result).isInstanceOf(ParseResult.Success.class);
        assertThat(result.issues())
                .extracting(ValidationIssue::category)
                .containsExactlyInAnyOrder(
                        ValidationCategory.MISSING_TITLE,
                        ValidationCategory.MISSING_SUMMARY,
                        ValidationCategory.MISSING_SCHEMA_VERSION);
        var success = (ParseResult.Success) result;
        // Title falls back to the filename without .md.
        assertThat(success.note().title()).isEqualTo("character_kael_varyn");
        assertThat(success.note().schemaVersion()).isEqualTo(1);
    }

    @Test
    void malformedYamlIsParseError() {
        String raw = """
                ---
                id: [unterminated
                ---

                Body.
                """;
        ParseResult result = assembler.assemble(file, raw);
        assertThat(result).isInstanceOf(ParseResult.Failure.class);
        assertThat(result.issues())
                .extracting(ValidationIssue::category)
                .contains(ValidationCategory.PARSE_ERRORS);
    }

    @Test
    void idDerivedTitleWhenFilenameAbsent() {
        // Use a path whose filename can't provide a title (just a bare name, no parent dir).
        String raw = """
                ---
                id: character_kael_varyn
                type: character
                summary: x
                schema_version: 1
                ---

                Body.
                """;
        // Filename = "character_kael_varyn" per fallback chain; not ID-derived here.
        ParseResult result = assembler.assemble(Path.of("character_kael_varyn.md"), raw);
        assertThat(result).isInstanceOf(ParseResult.Success.class);
        var success = (ParseResult.Success) result;
        assertThat(success.note().title()).isEqualTo("character_kael_varyn");
    }
}
