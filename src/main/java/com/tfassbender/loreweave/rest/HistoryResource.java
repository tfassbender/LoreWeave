package com.tfassbender.loreweave.rest;

import com.tfassbender.loreweave.git.CommitEntry;
import com.tfassbender.loreweave.git.FileChange;
import com.tfassbender.loreweave.git.HistoryPage;
import com.tfassbender.loreweave.git.HistoryService;
import com.tfassbender.loreweave.rest.dto.CommitEntryDto;
import com.tfassbender.loreweave.rest.dto.FileChangeDto;
import com.tfassbender.loreweave.rest.dto.HistoryResponse;
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
import java.util.Map;

@Path("/history")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "History", description = "Page through the vault's git log, newest-first.")
public class HistoryResource {

    @Inject
    HistoryService history;

    @GET
    @Operation(operationId = "getHistory",
            summary = "Page through the vault's git log",
            description = "Returns commits in newest-first order. Use `offset` to skip past "
                    + "already-seen commits and `page_size` to control batch size (clamped to "
                    + "the server's configured maximum). `has_more` indicates whether at least "
                    + "one further commit exists past the returned page. Set `include_files=false` "
                    + "to skip the per-commit diff for cheaper message-only calls.")
    public HistoryResponse history(
            @Parameter(description = "Zero-based index of the first commit to return (newest = 0).")
            @QueryParam("offset") @DefaultValue("0") int offset,
            @Parameter(description = "Page size; clamped to the server's configured max.")
            @QueryParam("page_size") Integer pageSize,
            @Parameter(description = "Include the per-commit changed_files list (default true).")
            @QueryParam("include_files") @DefaultValue("true") boolean includeFiles) {
        if (offset < 0) {
            throw new InvalidRequestException("offset must be >= 0", Map.of("offset", offset));
        }
        int requested = pageSize == null ? history.defaultPageSize() : pageSize;
        if (requested < 1) {
            throw new InvalidRequestException("page_size must be >= 1", Map.of("page_size", requested));
        }

        HistoryPage page = history.page(offset, requested, includeFiles);
        List<CommitEntryDto> dtos = page.commits().stream().map(HistoryResource::toDto).toList();
        return new HistoryResponse(page.offset(), page.pageSize(), page.hasMore(), dtos);
    }

    private static CommitEntryDto toDto(CommitEntry c) {
        List<FileChangeDto> files = c.changedFiles().stream()
                .map(f -> new FileChangeDto(f.path(), changeName(f.change())))
                .toList();
        return new CommitEntryDto(c.sha(), c.shortSha(), c.message(), c.author(), c.timestamp(), files);
    }

    private static String changeName(FileChange.ChangeType change) {
        return change.name().toLowerCase();
    }
}
