package com.tfassbender.loreweave.rest.dto;

/**
 * A resolved backlink on a {@link NoteDto}.
 *
 * @param sourcePath  the path handle of the note that links to us
 * @param title       source note's title
 * @param type        source note's {@code type}
 * @param displayText the link's pipe-display text at the source, or {@code null}
 */
public record BacklinkDto(String sourcePath, String title, String type, String displayText) {}
