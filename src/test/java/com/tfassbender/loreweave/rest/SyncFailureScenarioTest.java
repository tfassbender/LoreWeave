package com.tfassbender.loreweave.rest;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;

/**
 * Scenario: the configured git remote is unreachable. The app must still boot,
 * {@code /health} must report {@code last_sync.ok=false} with the error, and
 * {@code POST /sync} must return 500 / {@code SYNC_FAILED} rather than
 * succeeding or hanging.
 */
@QuarkusTest
@TestProfile(SyncFailureScenarioTest.BrokenRemoteProfile.class)
class SyncFailureScenarioTest {

    private static final String TOKEN = "Bearer test-token-123";

    @Test
    void healthReportsDegradedAndLastSyncError() {
        given()
            .when().get("/health")
            .then()
                .statusCode(200)
                .body("status", is("DEGRADED"))
                .body("last_sync.ok", is(false))
                .body("last_sync.error", notNullValue());
    }

    @Test
    void manualSyncReturnsSyncFailed() {
        given()
            .header("Authorization", TOKEN)
            .when().post("/sync")
            .then()
                .statusCode(500)
                .body("error.code", is("SYNC_FAILED"))
                .body("error.message", containsString("clone failed"));
    }

    /**
     * Points the vault at a bare-looking git URL that does not exist on disk, and
     * at a local-path that is also missing. The bootstrap sync will fail; the
     * server will still start.
     */
    public static class BrokenRemoteProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    "loreweave.vault.remote", "file:///no/such/place/does-not-exist.git",
                    "loreweave.vault.local-path", "build/tmp/sync-failure-test-vault"
            );
        }
    }
}
