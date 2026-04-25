package com.tfassbender.loreweave.rest;

import com.tfassbender.loreweave.domain.NoteKey;
import com.tfassbender.loreweave.graph.Index;
import com.tfassbender.loreweave.graph.IndexedNote;
import com.tfassbender.loreweave.graph.SyncService;
import com.tfassbender.loreweave.rest.dto.NoteResponse;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path("/note")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Note", description = "Fetch a single note with its links and backlinks.")
public class NoteResource {

    @Inject
    SyncService sync;

    @GET
    @Operation(operationId = "getNote", summary = "Get a note by path")
    public NoteResponse get(
            @Parameter(description = "Vault-relative path. Case-insensitive; `.md` suffix optional.",
                    example = "characters/kael", required = true)
            @QueryParam("path") String path) {
        if (path == null || path.isBlank()) {
            throw new InvalidRequestException("query parameter 'path' is required");
        }
        Index idx = sync.currentIndex();
        String key = NoteKey.of(path);
        IndexedNote in = idx.get(key).orElseThrow(() -> new NoteNotFoundException(path));
        return new NoteResponse(DtoMapper.toNoteDto(in, idx));
    }
}
