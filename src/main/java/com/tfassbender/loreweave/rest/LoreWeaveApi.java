package com.tfassbender.loreweave.rest;

import org.eclipse.microprofile.openapi.annotations.OpenAPIDefinition;
import org.eclipse.microprofile.openapi.annotations.enums.SecuritySchemeType;
import org.eclipse.microprofile.openapi.annotations.info.Info;
import org.eclipse.microprofile.openapi.annotations.security.SecurityScheme;

/**
 * Application-level OpenAPI metadata. No {@code extends Application} — Quarkus
 * auto-discovers JAX-RS resources, and adding an {@code Application} subclass
 * interferes with the synthetic bean discovery used for {@code @ConfigMapping}.
 * Annotations alone are enough for {@code quarkus-smallrye-openapi}.
 */
@OpenAPIDefinition(info = @Info(
        title = "LoreWeave API",
        version = "0.1.0",
        description = "Query interface over a Git-backed Obsidian vault. "
                + "All endpoints except /health require a bearer token."))
@SecurityScheme(securitySchemeName = "bearerAuth",
        type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "opaque")
public final class LoreWeaveApi {
    private LoreWeaveApi() {}
}
