package com.rag.integration;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

public class DocumentControllerIntegrationTest extends SimpleIntegrationTest {

    @LocalServerPort
    private int port;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
        RestAssured.baseURI = "http://localhost";
    }

    @Test
    void createDocument_ShouldReturnPendingStatus() {
        String requestBody = """
            {
                "filename": "test.pdf",
                "orderId": "ORDER-INT-001",
                "documentType": "TEXT",
                "content": "This is a test document for integration testing."
            }
        """;

        given()
                .contentType(ContentType.JSON)
                .body(requestBody)
                .when()
                .post("/api/documents")
                .then()
                .statusCode(HttpStatus.OK.value())
                .body("status", equalTo("PENDING"))
                .body("id", notNullValue())
                .body("message", equalTo("Document queued for processing"));
    }

    @Test
    void getAllDocuments_ShouldReturnList() {
        given()
                .contentType(ContentType.JSON)
                .when()
                .get("/api/documents")
                .then()
                .statusCode(HttpStatus.OK.value())
                .body("$", notNullValue());
    }

    @Test
    void getDocumentById_ShouldReturnDocument() {
        // Сначала создаём документ
        String requestBody = """
            {
                "filename": "test-get.pdf",
                "orderId": "ORDER-INT-002",
                "documentType": "TEXT",
                "content": "Test content for retrieval"
            }
        """;

        String documentId = given()
                .contentType(ContentType.JSON)
                .body(requestBody)
                .when()
                .post("/api/documents")
                .then()
                .extract()
                .path("id");

        // Затем получаем по ID
        given()
                .contentType(ContentType.JSON)
                .when()
                .get("/api/documents/{id}", documentId)
                .then()
                .statusCode(HttpStatus.OK.value())
                .body("id", equalTo(documentId))
                .body("filename", equalTo("test-get.pdf"))
                .body("orderId", equalTo("ORDER-INT-002"));
    }
}