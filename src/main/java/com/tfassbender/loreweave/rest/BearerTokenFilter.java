package com.tfassbender.loreweave.rest;

import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.PreMatching;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.ext.Provider;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.Optional;

/**
 * Requires {@code Authorization: Bearer <token>} on every request except
 * public paths. Public paths: {@code /health} and anything under {@code /q/}
 * (the Quarkus management namespace, including {@code /q/openapi} and
 * {@code /q/health}).
 *
 * <p>Uses {@link ConfigProperty} rather than the full {@code LoreWeaveConfig}
 * mapping because {@code @PreMatching} filters are instantiated early in the
 * Quarkus bootstrap, before {@code @ConfigMapping} synthetic beans are ready.
 *
 * <p>Fail-closed: if {@code loreweave.auth.token} is unset or blank, every
 * authed request is rejected. This prevents a misconfigured deployment from
 * silently serving the vault to the internet.
 */
@Provider
@PreMatching
@Priority(Priorities.AUTHENTICATION)
public class BearerTokenFilter implements ContainerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    @Inject
    @ConfigProperty(name = "loreweave.auth.token")
    Optional<String> configuredToken;

    @Override
    public void filter(ContainerRequestContext ctx) {
        String path = ctx.getUriInfo().getRequestUri().getPath();
        if (isPublic(path)) {
            return;
        }

        Optional<String> configured = configuredToken.filter(t -> !t.isBlank());
        if (configured.isEmpty()) {
            throw new UnauthorizedException("authentication is not configured on the server");
        }

        String header = ctx.getHeaderString(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            throw new UnauthorizedException("missing or malformed Authorization header");
        }

        String presented = header.substring(BEARER_PREFIX.length()).trim();
        if (!constantTimeEquals(presented, configured.get())) {
            throw new UnauthorizedException("invalid bearer token");
        }
    }

    private static boolean isPublic(String path) {
        if (path == null) return false;
        // Normalize a leading slash so both "/health" and "health" match.
        String p = path.startsWith("/") ? path.substring(1) : path;
        return p.equals("health")
                || p.startsWith("health/")
                || p.equals("q")
                || p.startsWith("q/");
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null || a.length() != b.length()) return false;
        int diff = 0;
        for (int i = 0; i < a.length(); i++) {
            diff |= a.charAt(i) ^ b.charAt(i);
        }
        return diff == 0;
    }
}
