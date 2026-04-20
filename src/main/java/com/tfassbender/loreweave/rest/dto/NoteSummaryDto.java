package com.tfassbender.loreweave.rest.dto;

import java.util.List;

/**
 * Compact form of a note for use in {@code /search} hits. Content and links
 * are intentionally excluded to keep responses small for AI consumers; fetch
 * full content via {@code GET /note?path=…}.
 */
public record NoteSummaryDto(
        String path,
        String title,
        String type,
        String summary,
        List<String> tags,
        double score) {}
