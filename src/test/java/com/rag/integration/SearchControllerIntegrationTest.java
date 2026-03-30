package com.rag.integration;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

public class SearchControllerIntegrationTest extends SimpleIntegrationTest {

    @LocalServerPort
    private int port;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
        RestAssured.baseURI = "http://localhost";
    }

    @Test
    void semanticSearch_ShouldReturnResults() {
        // Сначала создаём документ для поиска
        String docRequest = """
            {
                "filename": "search-test.pdf",
                "orderId": "ORDER-SEARCH-001",
                "documentType": "TEXT",
                "content": "Artificial Intelligence and Machine Learning are key technologies. RAG systems combine retrieval with generation."
            }
        """;

        given()
                .contentType(ContentType.JSON)
                .body(docRequest)
                .when()
                .post("/api/documents")
                .then()
                .statusCode(200);

        // Немного ждём для асинхронной обработки
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Выполняем поиск
        String searchRequest = """
            {
                "query": "RAG system",
                "limit": 10
            }
        """;

        given()
                .contentType(ContentType.JSON)
                .body(searchRequest)
                .when()
                .post("/api/search/semantic")
                .then()
                .statusCode(200)
                .body("query", equalTo("RAG system"))
                .body("results", notNullValue())
                .body("totalChunks", greaterThanOrEqualTo(0));
    }

    @Test
    void hybridSearch_ShouldReturnResults() {
        String searchRequest = """
            {
                "query": "machine learning",
                "limit": 10,
                "semanticWeight": 0.6,
                "textWeight": 0.4
            }
        """;

        given()
                .contentType(ContentType.JSON)
                .body(searchRequest)
                .when()
                .post("/api/search/hybrid")
                .then()
                .statusCode(200)
                .body("query", equalTo("machine learning"))
                .body("type", equalTo("hybrid"))
                .body("weights", notNullValue());
    }

    @Test
    void searchWithFilters_ShouldReturnFilteredResults() {
        String searchRequest = """
            {
                "query": "test",
                "orderId": "ORDER-SEARCH-001",
                "documentType": "TEXT",
                "limit": 10
            }
        """;

        given()
                .contentType(ContentType.JSON)
                .body(searchRequest)
                .when()
                .post("/api/search/semantic")
                .then()
                .statusCode(200)
                .body("filters.orderId", equalTo("ORDER-SEARCH-001"))
                .body("filters.documentType", equalTo("TEXT"));
    }

    @Test
    void getFilters_ShouldReturnAvailableFilters() {
        given()
                .contentType(ContentType.JSON)
                .when()
                .get("/api/search/filters")
                .then()
                .statusCode(200)
                .body("orderIds", notNullValue())
                .body("documentTypes", notNullValue());
    }
}