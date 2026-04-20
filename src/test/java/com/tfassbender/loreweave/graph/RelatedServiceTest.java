package com.tfassbender.loreweave.graph;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RelatedServiceTest {

    private final RelatedService service = new RelatedService();
    private final Index index = new IndexBuilder()
            .build(Path.of("src/test/resources/vault-valid").toAbsolutePath());

    @Test
    void depthOneReturnsImmediateForwardAndBackwardNeighbors() {
        // kael links to karsis, union, rex. Also rex links back to kael → backlink.
        List<RelatedService.Neighbor> n = service.related(index, "characters/kael", 1, 10);
        assertThat(n).extracting(x -> x.node().note().key())
                .containsExactlyInAnyOrder(
                        "locations/karsis", "factions/union", "characters/rex");
    }

    @Test
    void depthTwoIncludesNeighborsOfNeighbors() {
        // From union, depth-1 sees kael + rex (backlinks). Depth-2 also sees
        // whatever kael and rex link to.
        List<RelatedService.Neighbor> n = service.related(index, "factions/union", 2, 20);
        assertThat(n).extracting(x -> x.node().note().key())
                .contains("characters/kael", "characters/rex", "locations/karsis");
    }

    @Test
    void unknownSeedYieldsEmpty() {
        assertThat(service.related(index, "not/here", 1, 10)).isEmpty();
    }

    @Test
    void limitCapsAtTwenty() {
        List<RelatedService.Neighbor> n = service.related(index, "characters/kael", 2, 100);
        assertThat(n.size()).isLessThanOrEqualTo(20);
    }

    @Test
    void depthZeroYieldsEmpty() {
        assertThat(service.related(index, "characters/kael", 0, 10)).isEmpty();
    }
}
