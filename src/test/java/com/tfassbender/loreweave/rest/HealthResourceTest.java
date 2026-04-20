package com.tfassbender.loreweave.rest;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;

@QuarkusTest
class HealthResourceTest {

    @Test
    void healthIsPublicAndReportsServedNotes() {
        given()
            .when().get("/health")
            .then()
                .statusCode(200)
                .body("status", is("UP"))
                .body("index_loaded", is(true))
                .body("notes_count", is(4))
                .body("last_sync.ok", is(true))
                .body("last_sync.timestamp", notNullValue())
                .body("validation.errors", notNullValue())
                .body("validation.warnings", notNullValue());
    }

    @Test
    void healthRequiresNoAuth() {
        // Explicitly send no header — still 200.
        given()
            .header("Authorization", "")
            .when().get("/health")
            .then()
                .statusCode(200)
                .body("notes_count", greaterThanOrEqualTo(0));
    }
}
