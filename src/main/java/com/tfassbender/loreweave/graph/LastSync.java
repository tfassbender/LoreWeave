package com.tfassbender.loreweave.graph;

import java.time.Instant;

/**
 * Outcome of the most recent sync attempt. Exposed via {@code /health} so
 * operators and AI agents can tell whether the served index is fresh.
 *
 * @param ok             {@code true} iff the last sync completed without a git-level failure
 * @param timestamp      when the sync attempt finished
 * @param updatedFiles   number of files changed compared to the previous head, or {@code 0} if nothing moved
 * @param errorMessage   {@code null} on success; short human-readable message on failure
 */
public record LastSync(boolean ok, Instant timestamp, int updatedFiles, String errorMessage) {

    public static LastSync success(Instant timestamp, int updatedFiles) {
        return new LastSync(true, timestamp, updatedFiles, null);
    }

    public static LastSync failure(Instant timestamp, String errorMessage) {
        return new LastSync(false, timestamp, 0,
                errorMessage == null ? "unknown error" : errorMessage);
    }

    public static LastSync never() {
        return new LastSync(false, Instant.EPOCH, 0, "sync has not run yet");
    }
}
