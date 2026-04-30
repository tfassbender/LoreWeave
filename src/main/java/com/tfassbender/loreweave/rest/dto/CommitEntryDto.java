package com.tfassbender.loreweave.rest.dto;

import java.time.Instant;
import java.util.List;

/** Single entry in a {@code GET /history} response. */
public record CommitEntryDto(
        String sha,
        String shortSha,
        String message,
        String author,
        Instant timestamp,
        List<FileChangeDto> changedFiles) {
}
