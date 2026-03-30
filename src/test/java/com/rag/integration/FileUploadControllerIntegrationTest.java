package com.rag.integration;

import io.restassured.RestAssured;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.io.File;
import java.io.FileWriter;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

public class FileUploadControllerIntegrationTest extends SimpleIntegrationTest {

    @LocalServerPort
    private int port;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
        RestAssured.baseURI = "http://localhost";
    }

    @Test
    void uploadTextFile_ShouldReturnSuccess() throws Exception {
        // Создаём временный файл
        File tempFile = File.createTempFile("test", ".txt");
        try (FileWriter writer = new FileWriter(tempFile)) {
            writer.write("This is a test text file for upload integration test.");
        }

        given()
                .multiPart("file", tempFile)
                .multiPart("orderId", "ORDER-UPLOAD-001")
                .multiPart("documentType", "TEXT")
                .when()
                .post("/api/upload")
                .then()
                .statusCode(200)
                .body("filename", equalTo(tempFile.getName()))
                .body("orderId", equalTo("ORDER-UPLOAD-001"))
                .body("documentType", equalTo("TEXT"))
                .body("status", equalTo("PENDING"))
                .body("id", notNullValue());

        tempFile.delete();
    }
}