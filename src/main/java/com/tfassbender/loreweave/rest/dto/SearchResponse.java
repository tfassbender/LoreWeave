package com.tfassbender.loreweave.rest.dto;

import java.util.List;

/** Search response: {@code {"results": [...]}}. */
public record SearchResponse(List<NoteSummaryDto> results) {}
