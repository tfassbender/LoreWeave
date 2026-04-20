package com.tfassbender.loreweave.graph;

import com.tfassbender.loreweave.domain.Note;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Weighted substring search over an {@link Index}. Scoring (case-insensitive
 * "contains" on each field, per-field contribution, not per occurrence):
 *
 * <ul>
 *   <li>title → 10</li>
 *   <li>alias → 8</li>
 *   <li>tag → 6</li>
 *   <li>summary → 4</li>
 *   <li>body → 1</li>
 * </ul>
 *
 * <p>The raw sum is normalized to a {@code [0.0, 1.0]} score by dividing by
 * the maximum possible sum ({@value #MAX_RAW_SCORE}). So a note that matches
 * on every field hits {@code 1.0}; a note that only matches the body hits
 * {@code 1/29 ≈ 0.034}. This matches the {@code 0.92}-style value in
 * {@code doc/open_api_spec.md} and gives AI consumers a stable, comparable
 * relevance signal regardless of which fields were populated.
 *
 * <p>Ties break by title ascending, then by note key for deterministic output.
 */
@ApplicationScoped
public class SearchService {

    private static final int W_TITLE = 10;
    private static final int W_ALIAS = 8;
    private static final int W_TAG = 6;
    private static final int W_SUMMARY = 4;
    private static final int W_CONTENT = 1;

    /** Maximum attainable raw score — sum of every field's weight. */
    public static final int MAX_RAW_SCORE = W_TITLE + W_ALIAS + W_TAG + W_SUMMARY + W_CONTENT;

    public List<Hit> search(Index index, String rawQuery, String typeFilter, int limit) {
        if (rawQuery == null || rawQuery.isBlank() || index == null) return List.of();
        String q = rawQuery.toLowerCase(Locale.ROOT);
        String typeLower = typeFilter == null || typeFilter.isBlank()
                ? null
                : typeFilter.toLowerCase(Locale.ROOT);

        List<Hit> hits = new ArrayList<>();
        for (IndexedNote in : index.notes()) {
            Note n = in.note();
            if (typeLower != null && !n.type().toLowerCase(Locale.ROOT).equals(typeLower)) continue;

            int score = 0;
            if (contains(n.title(), q)) score += W_TITLE;
            for (String alias : n.aliases()) {
                if (contains(alias, q)) { score += W_ALIAS; break; }
            }
            for (String tag : n.tags()) {
                if (contains(tag, q)) { score += W_TAG; break; }
            }
            if (contains(n.summary(), q)) score += W_SUMMARY;
            if (contains(n.body(), q)) score += W_CONTENT;

            if (score > 0) hits.add(new Hit(in, normalize(score)));
        }

        hits.sort(Comparator
                .comparingDouble(Hit::score).reversed()
                .thenComparing(h -> h.indexedNote.note().title(), Comparator.nullsLast(String::compareTo))
                .thenComparing(h -> h.indexedNote.note().key()));

        int cap = Math.max(0, Math.min(limit, 10));
        return hits.size() <= cap ? List.copyOf(hits) : List.copyOf(hits.subList(0, cap));
    }

    private static double normalize(int rawScore) {
        return Math.round((double) rawScore / MAX_RAW_SCORE * 1000.0) / 1000.0;
    }

    private static boolean contains(String haystack, String needleLower) {
        if (haystack == null || haystack.isEmpty()) return false;
        return haystack.toLowerCase(Locale.ROOT).contains(needleLower);
    }

    public record Hit(IndexedNote indexedNote, double score) {}
}
