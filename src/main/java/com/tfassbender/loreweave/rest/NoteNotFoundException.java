package com.tfassbender.loreweave.rest;

import java.util.Map;

/** 404 — the requested note is not in the served index. */
public final class NoteNotFoundException extends LoreWeaveException {
    public NoteNotFoundException(String path) {
        super("No note found for path '" + path + "'", Map.of("path", path));
    }
}
