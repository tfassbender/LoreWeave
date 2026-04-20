package com.tfassbender.loreweave.rest.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Health response exposed at {@code GET /health}. Public (no auth). Combines
 * liveness, the current index size, the per-category validation breakdown,
 * and the most recent sync outcome.
 */
public record HealthResponse(
        String status,
        boolean indexLoaded,
        int notesCount,
        ValidationBlock validation,
        LastSyncBlock lastSync) {

    public record ValidationBlock(
            Map<String, CategoryStats> errors,
            Map<String, CategoryStats> warnings) {}

    public record CategoryStats(int count, List<String> samples) {}

    public record LastSyncBlock(
            boolean ok,
            Instant timestamp,
            int updatedFiles,
            String error) {}
}
