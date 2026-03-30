package com.rag.controller;

import com.rag.service.QualityMetricsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/metrics")
@RequiredArgsConstructor
@Tag(name = "Quality Metrics", description = "Метрики качества поиска")
public class QualityMetricsController {

    private final QualityMetricsService metricsService;

    @PostMapping("/evaluate")
    @Operation(summary = "Оценить качество поиска",
            description = "Вычисляет Precision, Recall, MRR для заданных запросов")
    public Map<String, Object> evaluateSearch(
            @RequestBody EvaluationRequest request) {

        return metricsService.runBatchEvaluation(
                request.getTestQueries(),
                request.getGroundTruth(),
                request.getSearchType()
        );
    }

    @GetMapping("/test-data")
    @Operation(summary = "Получить тестовые данные",
            description = "Возвращает пример тестовых запросов с релевантными документами")
    public Map<String, List<String>> getTestData() {
        return Map.of(
                "Что такое RAG?", List.of("doc1", "doc2"),
                "Как работает семантический поиск?", List.of("doc3", "doc4"),
                "Что такое эмбеддинги?", List.of("doc5")
        );
    }

    public static class EvaluationRequest {
        // key: строка запроса, value: список (не используется напрямую, но держим структуру для расширения)
        private Map<String, List<String>> testQueries;
        private Map<String, List<String>> groundTruth;
        private String searchType; // "semantic" or "hybrid"

        public Map<String, List<String>> getTestQueries() { return testQueries; }
        public void setTestQueries(Map<String, List<String>> testQueries) { this.testQueries = testQueries; }

        public Map<String, List<String>> getGroundTruth() { return groundTruth; }
        public void setGroundTruth(Map<String, List<String>> groundTruth) { this.groundTruth = groundTruth; }

        public String getSearchType() { return searchType; }
        public void setSearchType(String searchType) { this.searchType = searchType; }
    }
}