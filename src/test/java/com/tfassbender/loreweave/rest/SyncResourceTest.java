package com.tfassbender.loreweave.rest;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;

@QuarkusTest
class SyncResourceTest {

    private static final String TOKEN = "Bearer test-token-123";

    @Test
    void manualSyncReturnsOk() {
        given()
            .header("Authorization", TOKEN)
            .when().post("/sync")
            .then()
                .statusCode(200)
                .body("status", is("ok"))
                .body("timestamp", notNullValue());
    }

    @Test
    void missingTokenReturns401() {
        given()
            .when().post("/sync")
            .then()
                .statusCode(401);
    }
}
