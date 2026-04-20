package com.tfassbender.loreweave.rest;

import java.util.Map;

/** 400 — the request is missing a required field or carries an out-of-range value. */
public final class InvalidRequestException extends LoreWeaveException {
    public InvalidRequestException(String message) {
        super(message);
    }

    public InvalidRequestException(String message, Map<String, Object> details) {
        super(message, details);
    }
}
