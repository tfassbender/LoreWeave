package com.tfassbender.loreweave.rest;

/** 401 — missing or bad bearer token. */
public final class UnauthorizedException extends LoreWeaveException {
    public UnauthorizedException(String message) {
        super(message);
    }
}
