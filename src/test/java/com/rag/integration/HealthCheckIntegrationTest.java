package com.rag.integration;

import io.restassured.RestAssured;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

public class HealthCheckIntegrationTest extends SimpleIntegrationTest {

    @LocalServerPort
    private int port;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
        RestAssured.baseURI = "http://localhost";
    }

    @Test
    void healthCheck_ShouldReturnUp() {
        given()
                .when()
                .get("/health")
                .then()
                .statusCode(200)
                .body("status", equalTo("UP"))
                .body("service", equalTo("rag-simple"));
    }

    @Test
    void actuatorHealth_ShouldReturnUp() {
        given()
                .when()
                .get("/actuator/health")
                .then()
                .statusCode(200)
                .body("status", equalTo("UP"));
    }

    @Test
    void actuatorMetrics_ShouldReturnMetrics() {
        given()
                .when()
                .get("/actuator/metrics")
                .then()
                .statusCode(200)
                .body("names", hasItem("jvm.memory.used"));
    }
}