package com.tfassbender.loreweave.rest.dto;

import java.util.Map;

/** Canonical error envelope: {@code {"error": {"code", "message", "details"}}}. */
public record ErrorResponse(Body error) {

    public static ErrorResponse of(String code, String message, Map<String, Object> details) {
        return new ErrorResponse(new Body(code, message, details == null ? Map.of() : details));
    }

    public record Body(String code, String message, Map<String, Object> details) {}
}
