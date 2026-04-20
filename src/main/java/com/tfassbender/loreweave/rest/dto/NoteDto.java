package com.tfassbender.loreweave.rest.dto;

import java.util.List;
import java.util.Map;

/**
 * Full note response, including resolved forward links and backlinks. The
 * {@code aliases} field is intentionally omitted — aliases are not part of
 * the public API contract, only the internal schema.
 */
public record NoteDto(
        String path,
        String title,
        String type,
        String summary,
        List<String> tags,
        String content,
        Map<String, Object> metadata,
        List<LinkDto> links,
        List<BacklinkDto> backlinks,
        int schemaVersion) {}
