package com.rag.service;

import com.rag.dto.FiltersResponse;
import com.rag.dto.HybridSearchRequest;
import com.rag.dto.HybridSearchResponse;
import com.rag.dto.SearchFilters;
import com.rag.dto.SemanticSearchRequest;
import com.rag.dto.SemanticSearchResponse;
import com.rag.dto.Weights;
import com.rag.metrics.MetricsService;
import com.rag.util.VectorUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SearchService {

    private final EmbeddingService embeddingService;
    private final JdbcTemplate jdbcTemplate;
    private final CacheService cacheService;
    private final MetricsService metricsService;

    public SemanticSearchResponse semanticSearch(SemanticSearchRequest request) {
        String query = request.query();
        String orderId = request.orderId();
        String documentType = request.documentType();
        int limit = request.limit() != null ? request.limit() : 10;

        String cacheKey = String.format("search:%s:%s:%s:%d",
                query,
                orderId != null ? orderId : "",
                documentType != null ? documentType : "",
                limit
        );

        SemanticSearchResponse cached = cacheService.getSearchResultFromCache(cacheKey, SemanticSearchResponse.class);
        if (cached != null) {
            metricsService.incrementCacheHit();
            return cached;
        }

        metricsService.incrementCacheMiss();

        float[] queryEmbedding = embeddingService.generateEmbedding(query);
        String vectorStr = VectorUtils.embeddingToVectorLiteral(queryEmbedding);

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

        List<Map<String, Object>> rows = jdbcTemplate.query(sql.toString(), params.toArray(), (rs, rowNum) -> Map.of(
                "chunk_id", rs.getObject("chunk_id"),
                "document_id", rs.getObject("document_id"),
                "chunk_index", rs.getInt("chunk_index"),
                "chunk_content", rs.getString("chunk_content"),
                "filename", rs.getString("filename"),
                "order_id", rs.getString("order_id"),
                "document_type", rs.getString("document_type"),
                "similarity", rs.getDouble("similarity")
        ));

        Map<UUID, SemanticDocAcc> acc = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            UUID docId = (UUID) row.get("document_id");
            SemanticDocAcc a = acc.computeIfAbsent(docId, id -> {
                SemanticDocAcc init = new SemanticDocAcc();
                init.documentId = id;
                init.filename = (String) row.get("filename");
                init.orderId = (String) row.get("order_id");
                init.documentType = (String) row.get("document_type");
                init.bestSimilarity = (double) row.get("similarity");
                return init;
            });

            double similarity = (double) row.get("similarity");
            a.bestSimilarity = Math.max(a.bestSimilarity, similarity);
            int chunkIndex = (int) row.get("chunk_index");
            String chunkContent = (String) row.get("chunk_content");
            a.chunks.add(new SemanticSearchResponse.SemanticChunkResult(chunkIndex, chunkContent, similarity));
        }

        List<SemanticSearchResponse.SemanticDocumentResult> results = acc.values().stream()
                .map(a -> new SemanticSearchResponse.SemanticDocumentResult(
                        a.documentId,
                        a.filename,
                        a.orderId,
                        a.documentType,
                        a.bestSimilarity,
                        a.chunks
                ))
                .collect(Collectors.toList());

        SearchFilters filters = new SearchFilters(
                orderId != null ? orderId : "",
                documentType != null ? documentType : ""
        );

        SemanticSearchResponse response = new SemanticSearchResponse(
                query,
                filters,
                results,
                rows.size(),
                results.size()
        );

        cacheService.cacheSearchResult(cacheKey, response);
        return response;
    }

    public HybridSearchResponse hybridSearch(HybridSearchRequest request) {
        String query = request.query();
        String orderId = request.orderId();
        String documentType = request.documentType();
        int limit = request.limit() != null ? request.limit() : 10;
        double semanticWeight = request.semanticWeight() != null ? request.semanticWeight() : 0.7d;
        double textWeight = request.textWeight() != null ? request.textWeight() : 0.3d;

        String cacheKey = String.format("hybrid:%s:%s:%s:%d:%.1f:%.1f",
                query,
                orderId != null ? orderId : "",
                documentType != null ? documentType : "",
                limit,
                semanticWeight,
                textWeight
        );

        HybridSearchResponse cached = cacheService.getSearchResultFromCache(cacheKey, HybridSearchResponse.class);
        if (cached != null) {
            metricsService.incrementCacheHit();
            return cached;
        }

        metricsService.incrementCacheMiss();

        float[] queryEmbedding = embeddingService.generateEmbedding(query);
        String vectorStr = VectorUtils.embeddingToVectorLiteral(queryEmbedding);

        DocumentFilterClause filtersClause = buildDocumentFilterClause(orderId, documentType);

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

        String finalSql = String.format(sql, filtersClause.sql, filtersClause.sql);

        // ВАЖНО: filtersClause.sql вставляется в 2 разных CTE, значит filterParams нужно передать дважды.
        List<Object> params = new ArrayList<>();
        params.add(vectorStr); // semantic_score
        params.add(vectorStr); // semantic_rank
        params.addAll(filtersClause.params); // semantic where

        params.add(query); // text_score tsquery
        params.add(query); // text_rank tsquery
        params.add(query); // text_results WHERE tsquery
        params.addAll(filtersClause.params); // text where

        params.add(semanticWeight);
        params.add(textWeight);
        params.add(limit);

        List<HybridSearchResponse.HybridChunkResult> results = jdbcTemplate.query(finalSql, params.toArray(), (rs, rowNum) ->
                new HybridSearchResponse.HybridChunkResult(
                        (UUID) rs.getObject("chunk_id"),
                        (UUID) rs.getObject("document_id"),
                        rs.getInt("chunk_index"),
                        rs.getString("content"),
                        rs.getString("filename"),
                        rs.getString("order_id"),
                        rs.getString("document_type"),
                        rs.getDouble("semantic_score"),
                        rs.getDouble("text_score"),
                        rs.getDouble("rrf_score")
                ));

        SearchFilters filters = new SearchFilters(
                orderId != null ? orderId : "",
                documentType != null ? documentType : ""
        );

        HybridSearchResponse response = new HybridSearchResponse(
                query,
                "hybrid",
                new Weights(semanticWeight, textWeight),
                filters,
                results,
                results.size()
        );

        cacheService.cacheSearchResult(cacheKey, response);
        return response;
    }

    public FiltersResponse getFilters() {
        String orderIdsSql = "SELECT DISTINCT order_id FROM documents WHERE order_id IS NOT NULL AND order_id != ''";
        String docTypesSql = "SELECT DISTINCT document_type FROM documents WHERE document_type IS NOT NULL AND document_type != ''";

        List<String> orderIds = jdbcTemplate.query(orderIdsSql, (rs, rowNum) -> rs.getString("order_id"));
        List<String> documentTypes = jdbcTemplate.query(docTypesSql, (rs, rowNum) -> rs.getString("document_type"));

        return new FiltersResponse(orderIds, documentTypes);
    }

    /**
     * Used by quality evaluation to get list of document IDs.
     * For hybrid search we return one entry per chunk (keeps old behavior).
     */
    public List<String> search(String query, int limit, String searchType) {
        try {
            if ("semantic".equalsIgnoreCase(searchType)) {
                SemanticSearchResponse response = semanticSearch(new SemanticSearchRequest(query, null, null, limit));
                return response.results().stream()
                        .map(r -> r.documentId().toString())
                        .collect(Collectors.toList());
            }

            HybridSearchResponse response = hybridSearch(new HybridSearchRequest(query, null, null, limit, null, null));
            return response.results().stream()
                    .map(r -> r.documentId().toString())
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("Search failed for query: {}", query, e);
            return List.of();
        }
    }

    private static class SemanticDocAcc {
        private UUID documentId;
        private String filename;
        private String orderId;
        private String documentType;
        private double bestSimilarity;
        private List<SemanticSearchResponse.SemanticChunkResult> chunks = new ArrayList<>();
    }

    private DocumentFilterClause buildDocumentFilterClause(String orderId, String documentType) {
        StringBuilder sb = new StringBuilder();
        List<Object> params = new ArrayList<>();

        if (orderId != null && !orderId.isEmpty()) {
            sb.append(" AND d.order_id = ?");
            params.add(orderId);
        }
        if (documentType != null && !documentType.isEmpty()) {
            sb.append(" AND d.document_type = ?");
            params.add(documentType);
        }

        return new DocumentFilterClause(sb.toString(), params);
    }

    private record DocumentFilterClause(String sql, List<Object> params) {
    }
}
