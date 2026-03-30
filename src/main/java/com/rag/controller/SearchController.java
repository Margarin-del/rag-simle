package com.rag.controller;

import com.rag.metrics.MetricsService;
import com.rag.service.CacheService;
import com.rag.service.EmbeddingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@Slf4j
@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
@Tag(name = "Search", description = "Семантический и гибридный поиск документов")
public class SearchController {

    private final EmbeddingService embeddingService;
    private final JdbcTemplate jdbcTemplate;
    private final CacheService cacheService;
    private final MetricsService metricsService;

    @PostMapping("/semantic")
    @Operation(summary = "Семантический поиск", description = "Поиск документов по смыслу с возможностью фильтрации")
    public Map<String, Object> semanticSearch(@RequestBody SearchRequest request) {
        metricsService.incrementSearch();

        String query = request.getQuery();
        String orderId = request.getOrderId();
        String documentType = request.getDocumentType();
        Integer limit = request.getLimit() != null ? request.getLimit() : 10;

        // Создаём ключ для кэша
        String cacheKey = String.format("search:%s:%s:%s:%d", query,
                orderId != null ? orderId : "",
                documentType != null ? documentType : "",
                limit);

        // Проверяем кэш
        Map<String, Object> cachedResult = cacheService.getSearchResultFromCache(cacheKey);
        if (cachedResult != null) {
            log.info("✅ Cache HIT for search: {}", query);
            metricsService.incrementCacheHit();
            return cachedResult;
        }

        log.info("❌ Cache MISS for search: {}", query);
        metricsService.incrementCacheMiss();

        // Выполняем поиск
        float[] queryEmbedding = embeddingService.generateEmbedding(query);
        String vectorStr = embeddingToString(queryEmbedding);

        StringBuilder sql = new StringBuilder("""
            SELECT 
                dc.id as chunk_id,
                dc.document_id,
                dc.chunk_index,
                dc.content as chunk_content,
                d.filename,
                d.order_id,
                d.document_type,
                1 - (dc.embedding <=> CAST(? AS vector)) as similarity
            FROM document_chunks dc
            JOIN documents d ON dc.document_id = d.id
            WHERE dc.embedding IS NOT NULL
        """);

        List<Object> params = new ArrayList<>();
        params.add(vectorStr);

        if (orderId != null && !orderId.isEmpty()) {
            sql.append(" AND d.order_id = ?");
            params.add(orderId);
        }

        if (documentType != null && !documentType.isEmpty()) {
            sql.append(" AND d.document_type = ?");
            params.add(documentType);
        }

        sql.append(" ORDER BY dc.embedding <=> CAST(? AS vector) LIMIT ?");
        params.add(vectorStr);
        params.add(limit);

        List<Map<String, Object>> results = jdbcTemplate.query(sql.toString(),
                params.toArray(),
                (rs, rowNum) -> {
                    Map<String, Object> result = new HashMap<>();
                    result.put("chunkId", rs.getObject("chunk_id"));
                    result.put("documentId", rs.getObject("document_id"));
                    result.put("chunkIndex", rs.getInt("chunk_index"));
                    result.put("chunkContent", rs.getString("chunk_content"));
                    result.put("filename", rs.getString("filename"));
                    result.put("orderId", rs.getString("order_id"));
                    result.put("documentType", rs.getString("document_type"));
                    result.put("similarity", rs.getDouble("similarity"));
                    return result;
                });

        Map<UUID, Map<String, Object>> groupedResults = new LinkedHashMap<>();
        for (Map<String, Object> result : results) {
            UUID docId = (UUID) result.get("documentId");
            if (!groupedResults.containsKey(docId)) {
                Map<String, Object> docResult = new HashMap<>();
                docResult.put("documentId", docId);
                docResult.put("filename", result.get("filename"));
                docResult.put("orderId", result.get("orderId"));
                docResult.put("documentType", result.get("documentType"));
                docResult.put("bestSimilarity", result.get("similarity"));
                docResult.put("chunks", new ArrayList<Map<String, Object>>());
                groupedResults.put(docId, docResult);
            }

            Map<String, Object> docResult = groupedResults.get(docId);
            double bestSimilarity = (double) docResult.get("bestSimilarity");
            double currentSimilarity = (double) result.get("similarity");
            docResult.put("bestSimilarity", Math.max(bestSimilarity, currentSimilarity));

            List<Map<String, Object>> chunks = (List<Map<String, Object>>) docResult.get("chunks");
            Map<String, Object> chunkInfo = new HashMap<>();
            chunkInfo.put("chunkIndex", result.get("chunkIndex"));
            chunkInfo.put("content", result.get("chunkContent"));
            chunkInfo.put("similarity", result.get("similarity"));
            chunks.add(chunkInfo);
        }

        Map<String, Object> response = new HashMap<>();
        response.put("query", query);
        response.put("filters", Map.of(
                "orderId", orderId != null ? orderId : "",
                "documentType", documentType != null ? documentType : ""
        ));
        response.put("results", new ArrayList<>(groupedResults.values()));
        response.put("totalChunks", results.size());
        response.put("uniqueDocuments", groupedResults.size());

        // Сохраняем в кэш
        cacheService.cacheSearchResult(cacheKey, response);

        return response;
    }

    @PostMapping("/hybrid")
    @Operation(summary = "Гибридный поиск", description = "Комбинация векторного и полнотекстового поиска с RRF ранжированием")
    public Map<String, Object> hybridSearch(@RequestBody HybridSearchRequest request) {
        metricsService.incrementSearch();

        String query = request.getQuery();
        String orderId = request.getOrderId();
        String documentType = request.getDocumentType();
        Integer limit = request.getLimit() != null ? request.getLimit() : 10;
        Double semanticWeight = request.getSemanticWeight() != null ? request.getSemanticWeight() : 0.7;
        Double textWeight = request.getTextWeight() != null ? request.getTextWeight() : 0.3;

        // Создаём ключ для кэша
        String cacheKey = String.format("hybrid:%s:%s:%s:%d:%.1f:%.1f", query,
                orderId != null ? orderId : "",
                documentType != null ? documentType : "",
                limit, semanticWeight, textWeight);

        // Проверяем кэш
        Map<String, Object> cachedResult = cacheService.getSearchResultFromCache(cacheKey);
        if (cachedResult != null) {
            log.info("✅ Cache HIT for hybrid search: {}", query);
            metricsService.incrementCacheHit();
            return cachedResult;
        }

        log.info("❌ Cache MISS for hybrid search: {}", query);
        metricsService.incrementCacheMiss();

        // Генерируем эмбеддинг для запроса
        float[] queryEmbedding = embeddingService.generateEmbedding(query);
        String vectorStr = embeddingToString(queryEmbedding);

        StringBuilder whereClause = new StringBuilder();
        List<Object> params = new ArrayList<>();

        params.add(vectorStr);
        params.add(vectorStr);
        params.add(query);
        params.add(query);
        params.add(query);

        if (orderId != null && !orderId.isEmpty()) {
            whereClause.append(" AND d.order_id = ?");
            params.add(orderId);
        }

        if (documentType != null && !documentType.isEmpty()) {
            whereClause.append(" AND d.document_type = ?");
            params.add(documentType);
        }

        String whereStr = whereClause.toString();

        String sql = """
            WITH semantic_results AS (
                SELECT 
                    dc.id as chunk_id,
                    dc.document_id,
                    dc.chunk_index,
                    dc.content,
                    d.filename,
                    d.order_id,
                    d.document_type,
                    1 - (dc.embedding <=> CAST(? AS vector)) as semantic_score,
                    ROW_NUMBER() OVER (ORDER BY dc.embedding <=> CAST(? AS vector)) as semantic_rank
                FROM document_chunks dc
                JOIN documents d ON dc.document_id = d.id
                WHERE dc.embedding IS NOT NULL
                %s
            ),
            text_results AS (
                SELECT 
                    dc.id as chunk_id,
                    dc.document_id,
                    dc.chunk_index,
                    dc.content,
                    d.filename,
                    d.order_id,
                    d.document_type,
                    ts_rank(to_tsvector('russian', dc.content), plainto_tsquery('russian', ?)) as text_score,
                    ROW_NUMBER() OVER (ORDER BY ts_rank(to_tsvector('russian', dc.content), plainto_tsquery('russian', ?)) DESC) as text_rank
                FROM document_chunks dc
                JOIN documents d ON dc.document_id = d.id
                WHERE to_tsvector('russian', dc.content) @@ plainto_tsquery('russian', ?)
                %s
            )
            SELECT 
                COALESCE(s.chunk_id, t.chunk_id) as chunk_id,
                COALESCE(s.document_id, t.document_id) as document_id,
                COALESCE(s.chunk_index, t.chunk_index) as chunk_index,
                COALESCE(s.content, t.content) as content,
                COALESCE(s.filename, t.filename) as filename,
                COALESCE(s.order_id, t.order_id) as order_id,
                COALESCE(s.document_type, t.document_type) as document_type,
                COALESCE(s.semantic_score, 0) as semantic_score,
                COALESCE(t.text_score, 0) as text_score,
                (
                    COALESCE(1.0 / (60 + s.semantic_rank), 0) * ? +
                    COALESCE(1.0 / (60 + t.text_rank), 0) * ?
                ) as rrf_score
            FROM semantic_results s
            FULL OUTER JOIN text_results t ON s.chunk_id = t.chunk_id
            ORDER BY rrf_score DESC
            LIMIT ?
        """;

        String finalSql = String.format(sql, whereStr, whereStr);

        params.add(semanticWeight);
        params.add(textWeight);
        params.add(limit);

        List<Map<String, Object>> results = jdbcTemplate.query(finalSql,
                params.toArray(),
                (rs, rowNum) -> {
                    Map<String, Object> result = new HashMap<>();
                    result.put("chunkId", rs.getObject("chunk_id"));
                    result.put("documentId", rs.getObject("document_id"));
                    result.put("chunkIndex", rs.getInt("chunk_index"));
                    result.put("content", rs.getString("content"));
                    result.put("filename", rs.getString("filename"));
                    result.put("orderId", rs.getString("order_id"));
                    result.put("documentType", rs.getString("document_type"));
                    result.put("semanticScore", rs.getDouble("semantic_score"));
                    result.put("textScore", rs.getDouble("text_score"));
                    result.put("rrfScore", rs.getDouble("rrf_score"));
                    return result;
                });

        Map<String, Object> response = new HashMap<>();
        response.put("query", query);
        response.put("type", "hybrid");
        response.put("weights", Map.of("semantic", semanticWeight, "text", textWeight));
        response.put("filters", Map.of(
                "orderId", orderId != null ? orderId : "",
                "documentType", documentType != null ? documentType : ""
        ));
        response.put("results", results);
        response.put("totalResults", results.size());

        // Сохраняем в кэш
        cacheService.cacheSearchResult(cacheKey, response);

        return response;
    }

    @GetMapping("/filters")
    @Operation(summary = "Получить доступные фильтры", description = "Возвращает все доступные orderId и documentType для фильтрации")
    public Map<String, Object> getFilters() {
        String orderIdsSql = "SELECT DISTINCT order_id FROM documents WHERE order_id IS NOT NULL AND order_id != ''";
        String docTypesSql = "SELECT DISTINCT document_type FROM documents WHERE document_type IS NOT NULL AND document_type != ''";

        List<String> orderIds = jdbcTemplate.query(orderIdsSql, (rs, rowNum) -> rs.getString("order_id"));
        List<String> documentTypes = jdbcTemplate.query(docTypesSql, (rs, rowNum) -> rs.getString("document_type"));

        return Map.of(
                "orderIds", orderIds,
                "documentTypes", documentTypes
        );
    }

    private String embeddingToString(float[] embedding) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < embedding.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(embedding[i]);
        }
        sb.append("]");
        return sb.toString();
    }

    public static class SearchRequest {
        private String query;
        private String orderId;
        private String documentType;
        private Integer limit;

        public String getQuery() { return query; }
        public void setQuery(String query) { this.query = query; }

        public String getOrderId() { return orderId; }
        public void setOrderId(String orderId) { this.orderId = orderId; }

        public String getDocumentType() { return documentType; }
        public void setDocumentType(String documentType) { this.documentType = documentType; }

        public Integer getLimit() { return limit; }
        public void setLimit(Integer limit) { this.limit = limit; }
    }

    public static class HybridSearchRequest {
        private String query;
        private String orderId;
        private String documentType;
        private Integer limit;
        private Double semanticWeight;
        private Double textWeight;

        public String getQuery() { return query; }
        public void setQuery(String query) { this.query = query; }

        public String getOrderId() { return orderId; }
        public void setOrderId(String orderId) { this.orderId = orderId; }

        public String getDocumentType() { return documentType; }
        public void setDocumentType(String documentType) { this.documentType = documentType; }

        public Integer getLimit() { return limit; }
        public void setLimit(Integer limit) { this.limit = limit; }

        public Double getSemanticWeight() { return semanticWeight; }
        public void setSemanticWeight(Double semanticWeight) { this.semanticWeight = semanticWeight; }

        public Double getTextWeight() { return textWeight; }
        public void setTextWeight(Double textWeight) { this.textWeight = textWeight; }
    }
}