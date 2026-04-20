package com.tfassbender.loreweave.rest;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.lessThanOrEqualTo;

@QuarkusTest
class RelatedResourceTest {

    private static final String TOKEN = "Bearer test-token-123";

    @Test
    void returnsForwardAndBackwardNeighbors() {
        given()
            .header("Authorization", TOKEN)
            .queryParam("path", "characters/kael")
            .when().get("/related")
            .then()
                .statusCode(200)
                .body("node", is("characters/kael"))
                .body("related.path", hasItem(is("locations/karsis")))
                .body("related.path", hasItem(is("factions/union")))
                .body("related.path", hasItem(is("characters/rex")));
    }

    @Test
    void depthCapsAtFive() {
        given()
            .header("Authorization", TOKEN)
            .queryParam("path", "characters/kael")
            .queryParam("depth", "99")
            .when().get("/related")
            .then()
                .statusCode(200)
                .body("related.size()", lessThanOrEqualTo(20));
    }

    @Test
    void unknownSeedReturns404() {
        given()
            .header("Authorization", TOKEN)
            .queryParam("path", "not/here")
            .when().get("/related")
            .then()
                .statusCode(404)
                .body("error.code", is("NOTE_NOT_FOUND"));
    }

    @Test
    void missingTokenReturns401() {
        given()
            .queryParam("path", "characters/kael")
            .when().get("/related")
            .then()
                .statusCode(401);
    }
}
