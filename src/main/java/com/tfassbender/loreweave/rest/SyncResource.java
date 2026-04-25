package com.tfassbender.loreweave.rest;

import com.tfassbender.loreweave.graph.SyncService;
import com.tfassbender.loreweave.rest.dto.SyncResponse;
import jakarta.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path("/sync")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Sync", description = "Force a git pull + index rebuild.")
public class SyncResource {

    @Inject
    SyncService sync;

    @POST
    @Operation(operationId = "triggerSync",
            summary = "Pull the vault and rebuild the index",
            description = "Returns 500 / SYNC_FAILED if git fails, 409 / SYNC_IN_PROGRESS "
                    + "if another sync is already running.")
    public SyncResponse sync() {
        SyncService.SyncOutcome outcome = sync.syncNow();
        return new SyncResponse("ok", outcome.lastSync().updatedFiles(), outcome.lastSync().timestamp());
    }
}
