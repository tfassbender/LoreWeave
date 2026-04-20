package com.tfassbender.loreweave.domain;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * A successfully parsed note. Forward links are unresolved at this stage — they
 * are resolved to target note IDs during index build. Backlinks are not stored
 * on {@link Note}; they are computed and exposed by the {@code Index}.
 */
public record Note(
        String id,
        String type,
        String title,
        String summary,
        int schemaVersion,
        List<String> aliases,
        Map<String, Object> metadata,
        String body,
        List<Link> links,
        List<String> tags,
        Path sourcePath) {

    public Note {
        aliases = aliases == null ? List.of() : List.copyOf(aliases);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        links = links == null ? List.of() : List.copyOf(links);
        tags = tags == null ? List.of() : List.copyOf(tags);
    }
}
