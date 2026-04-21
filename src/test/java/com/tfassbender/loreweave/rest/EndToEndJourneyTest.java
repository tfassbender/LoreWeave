package com.tfassbender.loreweave.rest;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.Matchers.greaterThan;

/**
 * Full-boot integration walk that hits every public + authed endpoint in one
 * scenario, using the expanded {@code vault-valid} fixture. The methods are
 * ordered so the flow reads like a real client journey, but each is also
 * independently valid — Quarkus boots once for the whole class.
 */
@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class EndToEndJourneyTest {

    private static final String TOKEN = "Bearer test-token-123";

    @Test
    @Order(1)
    void healthIsUpAndReportsTenNotes() {
        given()
            .when().get("/health")
            .then()
                .statusCode(200)
                .body("status", is("UP"))
                .body("index_loaded", is(true))
                .body("notes_count", is(10))
                .body("last_sync.ok", is(true));
    }

    @Test
    @Order(2)
    void searchFindsKaelFromFreeText() {
        given()
            .header("Authorization", TOKEN)
            .queryParam("q", "Kael")
            .when().get("/search")
            .then()
                .statusCode(200)
                .body("results.path", hasItem(is("characters/kael")))
                .body("results[0].score", greaterThan(0f));
    }

    @Test
    @Order(3)
    void fetchFullNoteByPath() {
        given()
            .header("Authorization", TOKEN)
            .queryParam("path", "characters/kael")
            .when().get("/note")
            .then()
                .statusCode(200)
                .body("note.path", is("characters/kael"))
                .body("note.type", is("character"))
                .body("note.links.target_path", hasItem(is("locations/karsis")))
                .body("note.backlinks.source_path", hasItem(is("characters/rex")));
    }

    @Test
    @Order(4)
    void traverseGraphFromKael() {
        given()
            .header("Authorization", TOKEN)
            .queryParam("path", "characters/kael")
            .queryParam("depth", "2")
            .queryParam("limit", "20")
            .when().get("/related")
            .then()
                .statusCode(200)
                .body("node", is("characters/kael"))
                // Depth 2 from kael reaches tarek via rex → tarek (forward backlink chain).
                .body("related.path", hasItem(is("characters/tarek")));
    }

    @Test
    @Order(5)
    void manualSyncSucceeds() {
        given()
            .header("Authorization", TOKEN)
            .when().post("/sync")
            .then()
                .statusCode(200)
                .body("status", is("ok"))
                .body("timestamp", notNullValue());
    }

    @Test
    @Order(6)
    void healthRemainsUpAfterManualSync() {
        given()
            .when().get("/health")
            .then()
                .statusCode(200)
                .body("status", is("UP"))
                .body("notes_count", is(10));
    }
}
