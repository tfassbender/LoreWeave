package com.tfassbender.loreweave.rest;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.lessThanOrEqualTo;

@QuarkusTest
class SearchResourceTest {

    private static final String TOKEN = "Bearer test-token-123";

    @Test
    void searchesByKeyword() {
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
    void typeFilterNarrowsResults() {
        given()
            .header("Authorization", TOKEN)
            .queryParam("q", "union")
            .queryParam("type", "faction")
            .when().get("/search")
            .then()
                .statusCode(200)
                .body("results.type.unique()", is(java.util.List.of("faction")));
    }

    @Test
    void missingQReturns400() {
        given()
            .header("Authorization", TOKEN)
            .when().get("/search")
            .then()
                .statusCode(400)
                .body("error.code", is("INVALID_REQUEST"));
    }

    @Test
    void limitCapsAtTen() {
        given()
            .header("Authorization", TOKEN)
            .queryParam("q", "the")
            .queryParam("limit", "100")
            .when().get("/search")
            .then()
                .statusCode(200)
                .body("results.size()", lessThanOrEqualTo(10));
    }

    @Test
    void missingTokenReturns401() {
        given()
            .queryParam("q", "Kael")
            .when().get("/search")
            .then()
                .statusCode(401);
    }
}
