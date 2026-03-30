package com.rag.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmbeddingService {

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final CacheService cacheService;

    private static final String OLLAMA_URL = "http://localhost:11434/api/embeddings";
    private static final String MODEL = "nomic-embed-text";

    public float[] generateEmbedding(String text) {
        // Проверяем кэш
        float[] cachedEmbedding = cacheService.getEmbeddingFromCache(text);
        if (cachedEmbedding != null) {
            log.info("✅ Cache HIT for embedding: {}", text);
            return cachedEmbedding;
        }

        log.info("❌ Cache MISS for embedding: {}", text);

        try {
            Map<String, String> requestBody = Map.of("model", MODEL, "prompt", text);
            String jsonBody = objectMapper.writeValueAsString(requestBody);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(OLLAMA_URL))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new RuntimeException("Ollama API error: " + response.statusCode());
            }

            var root = objectMapper.readTree(response.body());
            var embeddingNode = root.get("embedding");

            float[] embedding = new float[embeddingNode.size()];
            for (int i = 0; i < embeddingNode.size(); i++) {
                embedding[i] = (float) embeddingNode.get(i).asDouble();
            }

            log.info("Generated embedding size: {}", embedding.length);

            // Сохраняем в кэш
            cacheService.cacheEmbedding(text, embedding);

            return embedding;

        } catch (Exception e) {
            log.error("Failed to generate embedding: {}", e.getMessage());
            throw new RuntimeException("Embedding generation failed", e);
        }
    }
}