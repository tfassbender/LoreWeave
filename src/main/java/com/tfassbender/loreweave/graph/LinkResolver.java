package com.tfassbender.loreweave.graph;

import com.tfassbender.loreweave.domain.Link;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Resolves a {@link Link}'s raw target to a note ID using the order defined in
 * {@code doc/vault_schema.md}: full path → basename (both comparisons
 * case-insensitive, {@code .md} suffix ignored).
 *
 * <p>Aliases declared in frontmatter are kept as note metadata but are
 * <b>not</b> consulted during link resolution. If a link text is not a path or
 * a basename, it is unresolved.
 *
 * <p>When a lookup key maps to multiple notes (two files share a basename),
 * the first-inserted entry wins. Callers feed the resolver a deterministic
 * note order (sorted by source path), so the tie-break is reproducible across
 * runs.
 */
public final class LinkResolver {

    private final Map<String, String> byFullPath; // lowercased path → id
    private final Map<String, String> byBasename; // lowercased basename → id

    private LinkResolver(Map<String, String> byFullPath, Map<String, String> byBasename) {
        this.byFullPath = byFullPath;
        this.byBasename = byBasename;
    }

    public Optional<String> resolve(Link link) {
        if (link == null) return Optional.empty();
        String raw = link.rawTarget();
        if (raw == null || raw.isBlank()) return Optional.empty();
        String normalized = normalizePath(raw);

        String id = byFullPath.get(normalized);
        if (id != null) return Optional.of(id);

        String key = stripMdSuffix(raw).toLowerCase(Locale.ROOT).trim();
        id = byBasename.get(key);
        if (id != null) return Optional.of(id);

        return Optional.empty();
    }

    /** Builder that captures the index tables while notes are being added in order. */
    public static final class Builder {
        // Preserve insertion order so "first wins" tie-breaking is deterministic.
        private final Map<String, String> byFullPath = new LinkedHashMap<>();
        private final Map<String, String> byBasename = new LinkedHashMap<>();

        /**
         * Registers a served note's path-based lookup entries.
         *
         * @param id           the note's ID
         * @param relativePath vault-relative path of the source file (required for path/basename resolution)
         */
        public Builder add(String id, Path relativePath) {
            if (id == null || id.isBlank() || relativePath == null) return this;

            String normFull = normalizePath(relativePath.toString());
            byFullPath.putIfAbsent(normFull, id);

            String basename = relativePath.getFileName() == null ? "" : relativePath.getFileName().toString();
            String basenameKey = stripMdSuffix(basename).toLowerCase(Locale.ROOT).trim();
            if (!basenameKey.isEmpty()) byBasename.putIfAbsent(basenameKey, id);

            return this;
        }

        public LinkResolver build() {
            return new LinkResolver(Map.copyOf(byFullPath), Map.copyOf(byBasename));
        }
    }

    private static String normalizePath(String raw) {
        String s = raw.replace('\\', '/').trim();
        s = stripMdSuffix(s);
        return s.toLowerCase(Locale.ROOT);
    }

    private static String stripMdSuffix(String s) {
        if (s == null) return "";
        if (s.toLowerCase(Locale.ROOT).endsWith(".md")) {
            return s.substring(0, s.length() - 3);
        }
        return s;
    }

    // For debugging/tests.
    Map<String, String> byFullPath() { return new TreeMap<>(byFullPath); }
    Map<String, String> byBasename() { return new TreeMap<>(byBasename); }
}
