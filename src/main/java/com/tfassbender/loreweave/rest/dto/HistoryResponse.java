package com.tfassbender.loreweave.rest.dto;

import java.util.List;

/** Response body for {@code GET /history}. */
public record HistoryResponse(int offset, int pageSize, boolean hasMore, List<CommitEntryDto> commits) {}
