package com.tfassbender.loreweave.rest;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;

/**
 * The test fixture under {@code src/test/resources/vault-valid} is not a git
 * repository, so the endpoint serves an empty page — perfect for verifying the
 * "missing repo → empty list" contract and the auth wiring without needing a
 * full git fixture in the resource layer. Per-commit behaviour is covered by
 * {@code GitVaultClientHistoryTest}; clamping by {@code HistoryServiceTest}.
 */
@QuarkusTest
class HistoryResourceTest {

    private static final String TOKEN = "Bearer test-token-123";

    @Test
    void requiresBearerToken() {
        given()
            .when().get("/history")
            .then()
                .statusCode(401);
    }

    @Test
    void emptyVaultReturnsEmptyPage() {
        given()
            .header("Authorization", TOKEN)
            .when().get("/history")
            .then()
                .statusCode(200)
                .body("offset", is(0))
                .body("page_size", is(10))
                .body("has_more", is(false))
                .body("commits.size()", is(0));
    }

    @Test
    void respectsExplicitPageSize() {
        given()
            .header("Authorization", TOKEN)
            .queryParam("page_size", 5)
            .when().get("/history")
            .then()
                .statusCode(200)
                .body("page_size", is(5));
    }

    @Test
    void clampsOversizePageSizeToMax() {
        given()
            .header("Authorization", TOKEN)
            .queryParam("page_size", 200)
            .when().get("/history")
            .then()
                .statusCode(200)
                .body("page_size", is(20));
    }

    @Test
    void rejectsNegativeOffset() {
        given()
            .header("Authorization", TOKEN)
            .queryParam("offset", -1)
            .when().get("/history")
            .then()
                .statusCode(400)
                .body("error.code", is("INVALID_REQUEST"));
    }

    @Test
    void rejectsZeroPageSize() {
        given()
            .header("Authorization", TOKEN)
            .queryParam("page_size", 0)
            .when().get("/history")
            .then()
                .statusCode(400)
                .body("error.code", is("INVALID_REQUEST"));
    }
}
