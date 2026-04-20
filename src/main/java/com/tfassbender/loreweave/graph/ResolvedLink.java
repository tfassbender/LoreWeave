package com.tfassbender.loreweave.graph;

import com.tfassbender.loreweave.domain.Link;

import java.util.Optional;

/**
 * A {@link Link} paired with the outcome of resolving it against the served
 * index. {@code targetId} is present iff resolution succeeded.
 */
public record ResolvedLink(Link link, Optional<String> targetId) {

    public ResolvedLink {
        if (link == null) throw new IllegalArgumentException("link is required");
        if (targetId == null) targetId = Optional.empty();
    }

    public boolean isResolved() {
        return targetId.isPresent();
    }
}
