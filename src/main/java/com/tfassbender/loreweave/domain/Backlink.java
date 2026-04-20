package com.tfassbender.loreweave.domain;

/**
 * An incoming link to a note, computed on index build from the forward links of
 * every other note. Phase 2 defines the type; phase 3 populates it.
 *
 * @param sourceNoteId the ID of the note that links to us
 * @param displayText  the pipe-display text used at the link site, or {@code null}
 */
public record Backlink(String sourceNoteId, String displayText) {

    public Backlink {
        if (sourceNoteId == null || sourceNoteId.isBlank()) {
            throw new IllegalArgumentException("sourceNoteId must be non-blank");
        }
    }
}
