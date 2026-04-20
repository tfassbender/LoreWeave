package com.tfassbender.loreweave.graph;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SearchServiceTest {

    private final SearchService service = new SearchService();
    private final IndexBuilder builder = new IndexBuilder();
    private final Index index = builder.build(Path.of("src/test/resources/vault-valid").toAbsolutePath());

    @Test
    void titleMatchBeatsContentMatch() {
        // Karsis is a title; 'frontier' appears only in summaries/body.
        List<SearchService.Hit> hits = service.search(index, "Karsis", null, 10);
        assertThat(hits).isNotEmpty();
        assertThat(hits.get(0).indexedNote().note().title()).contains("Karsis");
    }

    @Test
    void typeFilterExcludesNonMatches() {
        List<SearchService.Hit> hits = service.search(index, "union", "character", 10);
        assertThat(hits).allMatch(h -> h.indexedNote().note().type().equals("character"));
    }

    @Test
    void emptyQueryYieldsNothing() {
        assertThat(service.search(index, "", null, 10)).isEmpty();
        assertThat(service.search(index, null, null, 10)).isEmpty();
    }

    @Test
    void limitCapsAtTenAndRespectsSmallerValues() {
        List<SearchService.Hit> three = service.search(index, "the", null, 3);
        assertThat(three.size()).isLessThanOrEqualTo(3);
        List<SearchService.Hit> capped = service.search(index, "the", null, 100);
        assertThat(capped.size()).isLessThanOrEqualTo(10);
    }

    @Test
    void caseInsensitiveMatching() {
        List<SearchService.Hit> lower = service.search(index, "kael", null, 10);
        List<SearchService.Hit> upper = service.search(index, "KAEL", null, 10);
        assertThat(lower).hasSameSizeAs(upper);
    }

    @Test
    void scoreReflectsWeightedFields() {
        // 'union' hits a title (faction/union → "The Outer Union") AND summaries/body.
        // Some other notes only have it in body/summary.
        List<SearchService.Hit> hits = service.search(index, "union", null, 10);
        assertThat(hits).isNotEmpty();
        // Hits are ordered by score desc; the one whose title contains the term should win.
        assertThat(hits.get(0).indexedNote().note().title()).containsIgnoringCase("Union");
    }
}
