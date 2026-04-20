package com.tfassbender.loreweave.domain;

/**
 * Categories surfaced by the {@code /health} endpoint. The first five are errors
 * (the note is excluded from the served index); the last three are warnings (the
 * note is served but incomplete).
 */
public enum ValidationCategory {
    // Errors
    PARSE_ERRORS,
    MISSING_REQUIRED_FIELDS,
    INVALID_ID_FORMAT,
    DUPLICATE_IDS,
    UNRESOLVED_LINKS,

    // Warnings
    MISSING_TITLE,
    MISSING_SUMMARY,
    MISSING_SCHEMA_VERSION;

    public boolean isError() {
        return ordinal() <= UNRESOLVED_LINKS.ordinal();
    }
}
