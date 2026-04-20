package com.tfassbender.loreweave.rest;

import com.tfassbender.loreweave.domain.ValidationCategory;
import com.tfassbender.loreweave.graph.Index;
import com.tfassbender.loreweave.graph.LastSync;
import com.tfassbender.loreweave.graph.SyncService;
import com.tfassbender.loreweave.graph.ValidationReport;
import com.tfassbender.loreweave.rest.dto.HealthResponse;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.LinkedHashMap;
import java.util.Map;

@Path("/health")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Health", description = "Liveness, index stats, and last-sync info (public — no auth).")
public class HealthResource {

    @Inject
    SyncService sync;

    @GET
    @Operation(summary = "Server and index health",
            description = "Returns liveness, number of served notes, per-category validation "
                    + "counts (with up to 5 sample paths each), and the last-sync outcome.")
    public HealthResponse health() {
        Index idx = sync.currentIndex();
        LastSync last = sync.lastSync();

        Map<String, HealthResponse.CategoryStats> errors = new LinkedHashMap<>();
        Map<String, HealthResponse.CategoryStats> warnings = new LinkedHashMap<>();
        for (Map.Entry<ValidationCategory, ValidationReport.CategoryStats> e
                : idx.report().byCategory().entrySet()) {
            HealthResponse.CategoryStats stats = new HealthResponse.CategoryStats(
                    e.getValue().count(),
                    e.getValue().samples().stream()
                            .map(p -> p.toString().replace('\\', '/'))
                            .toList());
            String key = e.getKey().name().toLowerCase();
            if (e.getKey().isError()) errors.put(key, stats);
            else warnings.put(key, stats);
        }

        boolean healthy = last.ok() && idx.report().totalErrors() == 0;
        return new HealthResponse(
                healthy ? "UP" : "DEGRADED",
                idx.size() > 0 || last.ok(),
                idx.size(),
                new HealthResponse.ValidationBlock(errors, warnings),
                new HealthResponse.LastSyncBlock(
                        last.ok(), last.timestamp(), last.updatedFiles(), last.errorMessage()));
    }
}
