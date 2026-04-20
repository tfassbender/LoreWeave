package com.tfassbender.loreweave.graph;

import com.tfassbender.loreweave.domain.Link;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class LinkResolverTest {

    private LinkResolver buildResolver() {
        return new LinkResolver.Builder()
                .add("character_kael_varyn", Path.of("characters", "kael.md"))
                .add("faction_outer_union", Path.of("factions", "union.md"))
                .add("location_karsis_station", Path.of("locations", "karsis.md"))
                .build();
    }

    @Test
    void resolvesByBasenameCaseInsensitively() {
        LinkResolver r = buildResolver();
        assertThat(r.resolve(new Link("kael", null, null))).contains("character_kael_varyn");
        assertThat(r.resolve(new Link("KAEL", null, null))).contains("character_kael_varyn");
    }

    @Test
    void resolvesByFullPath() {
        LinkResolver r = buildResolver();
        assertThat(r.resolve(new Link("locations/karsis", null, null))).contains("location_karsis_station");
        assertThat(r.resolve(new Link("locations/karsis.md", null, null))).contains("location_karsis_station");
    }

    @Test
    void fullPathTakesPrecedenceOverBasename() {
        LinkResolver r = new LinkResolver.Builder()
                .add("a_root", Path.of("kael.md"))
                .add("a_nested", Path.of("characters", "kael.md"))
                .build();
        // Both have basename 'kael'; first-wins says a_root takes the basename slot.
        // But a full path to the nested file still resolves to the nested one.
        assertThat(r.resolve(new Link("characters/kael", null, null))).contains("a_nested");
        assertThat(r.resolve(new Link("kael", null, null))).contains("a_root");
    }

    @Test
    void aliasLikeTextDoesNotResolve() {
        // Aliases are not consulted. A link whose text is not a path or basename
        // must come back empty, even when it looks like an alternate display name.
        LinkResolver r = buildResolver();
        assertThat(r.resolve(new Link("The Scout", null, null))).isEmpty();
        assertThat(r.resolve(new Link("The Union", null, null))).isEmpty();
        assertThat(r.resolve(new Link("Rex Morrow", null, null))).isEmpty();
    }

    @Test
    void returnsEmptyWhenUnknown() {
        LinkResolver r = buildResolver();
        assertThat(r.resolve(new Link("Ghost", null, null))).isEmpty();
    }

    @Test
    void firstInsertedWinsOnBasenameCollision() {
        LinkResolver r = new LinkResolver.Builder()
                .add("a_first", Path.of("dir1", "note.md"))
                .add("b_second", Path.of("dir2", "note.md"))
                .build();
        assertThat(r.resolve(new Link("note", null, null))).contains("a_first");
    }

    @Test
    void missingRelativePathIsNoOp() {
        LinkResolver r = new LinkResolver.Builder()
                .add("no_path", null)
                .build();
        assertThat(r.resolve(new Link("no_path", null, null))).isEmpty();
    }
}
