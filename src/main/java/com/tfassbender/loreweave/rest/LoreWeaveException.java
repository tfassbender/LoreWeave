package com.tfassbender.loreweave.rest;

import java.util.Map;

/**
 * Base class for domain-level exceptions that translate cleanly into the REST
 * error envelope. Subclasses carry just the data; {@link ErrorMapper} picks
 * the HTTP status and error code.
 */
public abstract class LoreWeaveException extends RuntimeException {

    private final Map<String, Object> details;

    protected LoreWeaveException(String message) {
        this(message, Map.of());
    }

    protected LoreWeaveException(String message, Map<String, Object> details) {
        super(message);
        this.details = details == null ? Map.of() : Map.copyOf(details);
    }

    public Map<String, Object> details() {
        return details;
    }
}
