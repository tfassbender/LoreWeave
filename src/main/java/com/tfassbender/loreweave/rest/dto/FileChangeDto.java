package com.tfassbender.loreweave.rest.dto;

/** A single file change in a commit. */
public record FileChangeDto(String path, String change) {}
