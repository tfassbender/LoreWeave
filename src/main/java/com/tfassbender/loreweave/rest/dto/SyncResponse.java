package com.tfassbender.loreweave.rest.dto;

import java.time.Instant;

/** Response from {@code POST /sync}. */
public record SyncResponse(String status, int updatedFiles, Instant timestamp) {}
