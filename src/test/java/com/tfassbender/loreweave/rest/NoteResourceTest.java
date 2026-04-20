package com.tfassbender.loreweave.rest;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.is;

@QuarkusTest
class NoteResourceTest {

    private static final String TOKEN = "Bearer test-token-123";

    @Test
    void getsResolvedNoteWithLinksAndBacklinks() {
        given()
            .header("Authorization", TOKEN)
            .queryParam("path", "characters/kael")
            .when().get("/note")
            .then()
                .statusCode(200)
                .body("note.path", is("characters/kael"))
                .body("note.title", is("Kael Varyn"))
                .body("note.type", is("character"))
                .body("note.schema_version", is(1))
                .body("note.tags", hasItem("pov"))
                .body("note.links.target_path",
                        hasItem(equalTo("locations/karsis")))
                .body("note.links.target_path",
                        hasItem(equalTo("factions/union")))
                .body("note.backlinks.source_path",
                        hasItem(equalTo("characters/rex")));
    }

    @Test
    void getAcceptsCaseInsensitiveAndMdSuffixedPath() {
        given()
            .header("Authorization", TOKEN)
            .queryParam("path", "Characters/Kael.MD")
            .when().get("/note")
            .then()
                .statusCode(200)
                .body("note.path", is("characters/kael"));
    }

    @Test
    void missingNoteReturns404NoteNotFound() {
        given()
            .header("Authorization", TOKEN)
            .queryParam("path", "characters/ghost")
            .when().get("/note")
            .then()
                .statusCode(404)
                .body("error.code", is("NOTE_NOT_FOUND"))
                .body("error.details.path", is("characters/ghost"));
    }

    @Test
    void missingPathParamReturns400InvalidRequest() {
        given()
            .header("Authorization", TOKEN)
            .when().get("/note")
            .then()
                .statusCode(400)
                .body("error.code", is("INVALID_REQUEST"));
    }

    @Test
    void missingTokenReturns401() {
        given()
            .queryParam("path", "characters/kael")
            .when().get("/note")
            .then()
                .statusCode(401)
                .body("error.code", is("UNAUTHORIZED"));
    }

    @Test
    void wrongTokenReturns401() {
        given()
            .header("Authorization", "Bearer not-the-token")
            .queryParam("path", "characters/kael")
            .when().get("/note")
            .then()
                .statusCode(401)
                .body("error.code", is("UNAUTHORIZED"));
    }
}
