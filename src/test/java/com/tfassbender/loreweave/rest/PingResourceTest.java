package com.tfassbender.loreweave.rest;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;

@QuarkusTest
class PingResourceTest {

    @Test
    void pingReturnsOk() {
        given()
            .when().get("/ping")
            .then()
                .statusCode(200)
                .body("status", is("ok"));
    }
}
