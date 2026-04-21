package com.tfassbender.loreweave.rest;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.is;

/**
 * Scenario: the vault contains files that exercise every validation category
 * (parse_errors, missing_required_fields, unresolved_links, and each of the
 * three warnings). {@code /health} must surface the categories with counts and
 * sample paths, and the server must keep serving warning-only notes.
 */
@QuarkusTest
@TestProfile(ValidationErrorScenarioTest.InvalidVaultProfile.class)
class ValidationErrorScenarioTest {

    @Test
    void healthSurfacesEveryErrorCategory() {
        given()
            .when().get("/health")
            .then()
                .statusCode(200)
                // Error categories in vault-invalid:
                // - parse_errors (bad_yaml.md)
                // - missing_required_fields (missing_type.md)
                // - unresolved_links (unresolved.md → [[DoesNotExist]])
                .body("validation.errors.parse_errors.count", greaterThanOrEqualTo(1))
                .body("validation.errors.missing_required_fields.count", greaterThanOrEqualTo(1))
                .body("validation.errors.unresolved_links.count", greaterThanOrEqualTo(1));
    }

    @Test
    void healthSurfacesEveryWarningCategory() {
        given()
            .when().get("/health")
            .then()
                .statusCode(200)
                .body("validation.warnings.missing_title.count", greaterThanOrEqualTo(1))
                .body("validation.warnings.missing_summary.count", greaterThanOrEqualTo(1))
                .body("validation.warnings.missing_schema_version.count", greaterThanOrEqualTo(1));
    }

    @Test
    void warningOnlyNotesAreStillServed() {
        given()
            .when().get("/health")
            .then()
                .statusCode(200)
                // vault-invalid contains missing_type, bad_yaml (both excluded),
                // and no_title / no_summary / no_schema / unresolved (warning-only, served).
                // So the served count is the 4 warning-only notes.
                .body("notes_count", is(4));
    }

    public static class InvalidVaultProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    "loreweave.vault.remote", "",
                    "loreweave.vault.local-path", "src/test/resources/vault-invalid"
            );
        }
    }
}
