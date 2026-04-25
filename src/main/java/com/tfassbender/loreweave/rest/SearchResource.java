package com.tfassbender.loreweave.rest;

import com.tfassbender.loreweave.graph.SearchService;
import com.tfassbender.loreweave.graph.SyncService;
import com.tfassbender.loreweave.rest.dto.NoteSummaryDto;
import com.tfassbender.loreweave.rest.dto.SearchResponse;
import jakarta.inject.Inject;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.List;

@Path("/search")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Search", description = "Weighted substring search across served notes.")
public class SearchResource {

    @Inject
    SyncService sync;

    @Inject
    SearchService search;

    @GET
    @Operation(operationId = "searchNotes",
            summary = "Search notes by keyword",
            description = "Case-insensitive substring scoring — title 10, alias 8, tag 6, "
                    + "summary 4, content 1. Results capped at 10.")
    public SearchResponse search(
            @Parameter(description = "Query text (required).", required = true)
            @QueryParam("q") String q,
            @Parameter(description = "Restrict to a single `type:` value.")
            @QueryParam("type") String type,
            @Parameter(description = "Max results (capped at 10).")
            @QueryParam("limit") @DefaultValue("10") int limit) {
        if (q == null || q.isBlank()) {
            throw new InvalidRequestException("query parameter 'q' is required");
        }
        var hits = search.search(sync.currentIndex(), q, type, limit);
        List<NoteSummaryDto> results = hits.stream()
                .map(h -> DtoMapper.toSummary(h.indexedNote(), h.score()))
                .toList();
        return new SearchResponse(results);
    }
}
