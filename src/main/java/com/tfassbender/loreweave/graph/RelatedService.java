package com.tfassbender.loreweave.graph;

import com.tfassbender.loreweave.domain.Backlink;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Breadth-first traversal over resolved forward and backward edges. Produces
 * at most {@code limit} neighbors, stopping once we'd exceed {@code depth}.
 *
 * <p>Unresolved edges are not traversed — they appear only in the validation
 * report, not in the graph surface.
 *
 * <p>Caps: depth 0..5, limit 0..20. Caller-supplied values are clamped
 * silently so the resource layer doesn't need to pre-validate.
 */
@ApplicationScoped
public class RelatedService {

    /** Maximum traversal depth enforced here regardless of request value. */
    public static final int MAX_DEPTH = 5;

    /** Maximum number of neighbors returned per request. */
    public static final int MAX_LIMIT = 20;

    public List<Neighbor> related(Index index, String seedKey, int depth, int limit) {
        if (index == null || seedKey == null) return List.of();
        int cap = Math.max(0, Math.min(limit, MAX_LIMIT));
        int maxDepth = Math.max(0, Math.min(depth, MAX_DEPTH));

        IndexedNote seed = index.get(seedKey).orElse(null);
        if (seed == null || cap == 0 || maxDepth == 0) return List.of();

        List<Neighbor> out = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        visited.add(seedKey);

        Deque<Step> queue = new ArrayDeque<>();
        queue.add(new Step(seed, 0));
        while (!queue.isEmpty() && out.size() < cap) {
            Step step = queue.poll();
            if (step.distance >= maxDepth) continue;

            // Forward edges.
            for (ResolvedLink rl : step.node.resolvedLinks()) {
                if (!rl.isResolved()) continue;
                String targetKey = rl.targetKey().orElseThrow();
                if (!visited.add(targetKey)) continue;
                index.get(targetKey).ifPresent(neighbor -> {
                    out.add(new Neighbor(neighbor, "link"));
                    queue.add(new Step(neighbor, step.distance + 1));
                });
                if (out.size() >= cap) return List.copyOf(out);
            }
            // Backward edges.
            for (Backlink b : step.node.backlinks()) {
                if (!visited.add(b.sourceKey())) continue;
                index.get(b.sourceKey()).ifPresent(neighbor -> {
                    out.add(new Neighbor(neighbor, "backlink"));
                    queue.add(new Step(neighbor, step.distance + 1));
                });
                if (out.size() >= cap) return List.copyOf(out);
            }
        }
        return List.copyOf(out);
    }

    public record Neighbor(IndexedNote node, String relation) {}

    private record Step(IndexedNote node, int distance) {}
}
