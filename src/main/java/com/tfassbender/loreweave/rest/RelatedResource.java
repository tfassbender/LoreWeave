package com.tfassbender.loreweave.rest;

import com.tfassbender.loreweave.domain.NoteKey;
import com.tfassbender.loreweave.graph.Index;
import com.tfassbender.loreweave.graph.RelatedService;
import com.tfassbender.loreweave.graph.SyncService;
import com.tfassbender.loreweave.rest.dto.RelatedResponse;
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

@Path("/related")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Related", description = "Graph neighbors via BFS over resolved forward and backward edges.")
public class RelatedResource {

    @Inject
    SyncService sync;

    @Inject
    RelatedService related;

    @GET
    @Operation(summary = "Get graph neighbors of a note")
    public RelatedResponse related(
            @Parameter(description = "Seed note path.", required = true)
            @QueryParam("path") String path,
            @Parameter(description = "Traversal depth. Default 2, max 5.")
            @QueryParam("depth") @DefaultValue("2") int depth,
            @Parameter(description = "Max neighbors returned. Default 10, max 20.")
            @QueryParam("limit") @DefaultValue("10") int limit) {
        if (path == null || path.isBlank()) {
            throw new InvalidRequestException("query parameter 'path' is required");
        }
        Index idx = sync.currentIndex();
        String key = NoteKey.of(path);
        if (idx.get(key).isEmpty()) {
            throw new NoteNotFoundException(path);
        }

        List<RelatedService.Neighbor> neighbors = related.related(idx, key, depth, limit);
        List<RelatedResponse.RelatedNode> mapped = neighbors.stream()
                .map(n -> new RelatedResponse.RelatedNode(
                        n.node().note().key(),
                        n.node().note().title(),
                        n.node().note().type(),
                        n.relation()))
                .toList();
        return new RelatedResponse(key, mapped);
    }
}
