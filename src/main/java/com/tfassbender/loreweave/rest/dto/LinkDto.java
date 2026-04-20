package com.tfassbender.loreweave.rest.dto;

/**
 * A resolved forward link on a {@link NoteDto}.
 *
 * @param targetPath  the target note's path handle
 * @param title       the target's title (already resolved via the fallback chain)
 * @param type        the target's {@code type} frontmatter value
 * @param displayText the link's pipe-display text, or {@code null} if the link had none
 */
public record LinkDto(String targetPath, String title, String type, String displayText) {}
