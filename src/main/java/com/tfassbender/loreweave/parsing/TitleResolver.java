package com.tfassbender.loreweave.parsing;

/**
 * Resolves a display title using the precedence defined in
 * {@code doc/vault_schema.md}: frontmatter {@code title} → filename without
 * {@code .md} → ID-derived. The latter two also flag {@code missing_title}.
 */
public final class TitleResolver {

    public Resolved resolve(String frontmatterTitle, String filenameWithoutExt, String id) {
        if (frontmatterTitle != null && !frontmatterTitle.isBlank()) {
            return new Resolved(frontmatterTitle.strip(), false);
        }
        if (filenameWithoutExt != null && !filenameWithoutExt.isBlank()) {
            return new Resolved(filenameWithoutExt.strip(), true);
        }
        if (id != null && !id.isBlank()) {
            return new Resolved(deriveFromId(id), true);
        }
        return new Resolved("", true);
    }

    private static String deriveFromId(String id) {
        int underscore = id.indexOf('_');
        String tail = underscore < 0 ? id : id.substring(underscore + 1);
        String[] parts = tail.split("_");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) sb.append(' ');
            String p = parts[i];
            if (p.isEmpty()) continue;
            sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1));
        }
        return sb.toString();
    }

    public record Resolved(String title, boolean missingTitleWarning) {}
}
