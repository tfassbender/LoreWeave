package com.tfassbender.loreweave.rest;

import com.tfassbender.loreweave.git.GitSyncException;
import com.tfassbender.loreweave.graph.SyncInProgressException;
import com.tfassbender.loreweave.rest.dto.ErrorResponse;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.jboss.logging.Logger;

import java.util.Map;

/**
 * Converts exceptions into the canonical {@code {"error": {code, message, details}}}
 * envelope. The mapping:
 *
 * <ul>
 *   <li>{@link NoteNotFoundException} → 404 / {@code NOTE_NOT_FOUND}</li>
 *   <li>{@link InvalidRequestException} → 400 / {@code INVALID_REQUEST}</li>
 *   <li>{@link UnauthorizedException} → 401 / {@code UNAUTHORIZED}</li>
 *   <li>{@link GitSyncException} → 500 / {@code SYNC_FAILED}</li>
 *   <li>{@link SyncInProgressException} → 409 / {@code SYNC_IN_PROGRESS}</li>
 *   <li>Anything else → 500 / {@code INTERNAL_ERROR}, logged at ERROR</li>
 * </ul>
 */
@Provider
public class ErrorMapper implements ExceptionMapper<Throwable> {

    private static final Logger LOG = Logger.getLogger(ErrorMapper.class);

    @Override
    public Response toResponse(Throwable ex) {
        if (ex instanceof NoteNotFoundException nnf) {
            return build(Response.Status.NOT_FOUND, "NOTE_NOT_FOUND", nnf.getMessage(), nnf.details());
        }
        if (ex instanceof InvalidRequestException ir) {
            return build(Response.Status.BAD_REQUEST, "INVALID_REQUEST", ir.getMessage(), ir.details());
        }
        if (ex instanceof UnauthorizedException ua) {
            return build(Response.Status.UNAUTHORIZED, "UNAUTHORIZED", ua.getMessage(), ua.details());
        }
        if (ex instanceof GitSyncException gse) {
            return build(Response.Status.INTERNAL_SERVER_ERROR, "SYNC_FAILED", gse.getMessage(), Map.of());
        }
        if (ex instanceof SyncInProgressException sip) {
            return build(Response.Status.CONFLICT, "SYNC_IN_PROGRESS", sip.getMessage(), Map.of());
        }
        if (ex instanceof jakarta.ws.rs.WebApplicationException wae && wae.getResponse() != null) {
            // Preserve other JAX-RS framework responses (e.g. 405s for wrong method)
            // but wrap their body so clients always see our error envelope.
            int status = wae.getResponse().getStatus();
            return build(Response.Status.fromStatusCode(status), "INVALID_REQUEST",
                    wae.getMessage() == null ? "bad request" : wae.getMessage(), Map.of());
        }

        LOG.error("Unhandled exception from REST layer", ex);
        return build(Response.Status.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "internal error", Map.of());
    }

    private static Response build(Response.Status status, String code, String message, Map<String, Object> details) {
        return Response.status(status)
                .type("application/json")
                .entity(ErrorResponse.of(code, message, details))
                .build();
    }
}
