package com.tfassbender.loreweave.parsing;

import java.util.regex.Pattern;

/**
 * Enforces the ID format: {@code ^[a-z][a-z0-9]*_[a-z0-9_]+$}, and requires the
 * prefix before the first underscore to equal the {@code type:} value.
 */
public final class IdValidator {

    private static final Pattern ID = Pattern.compile("^[a-z][a-z0-9]*_[a-z0-9_]+$");

    public Result validate(String id, String type) {
        if (id == null || id.isBlank()) {
            return new Result(false, "id is blank");
        }
        if (!ID.matcher(id).matches()) {
            return new Result(false, "id '" + id + "' does not match " + ID.pattern());
        }
        if (type == null || type.isBlank()) {
            return new Result(false, "type is blank, cannot verify id prefix");
        }
        int underscore = id.indexOf('_');
        String prefix = id.substring(0, underscore);
        if (!prefix.equals(type)) {
            return new Result(false,
                    "id prefix '" + prefix + "' does not match type '" + type + "'");
        }
        return new Result(true, null);
    }

    public record Result(boolean valid, String message) {}
}
