package com.rag.controller;

import com.rag.dto.FiltersResponse;
import com.rag.dto.HybridSearchRequest;
import com.rag.dto.HybridSearchResponse;
import com.rag.dto.SemanticSearchRequest;
import com.rag.dto.SemanticSearchResponse;
import com.rag.metrics.MetricsService;
import com.rag.service.SearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
@Tag(name = "Search", description = "Семантический и гибридный поиск документов")
public class SearchController {

    private final MetricsService metricsService;
    private final SearchService searchService;

    @PostMapping("/semantic")
    @Operation(summary = "Семантический поиск", description = "Поиск документов по смыслу с возможностью фильтрации")
    public SemanticSearchResponse semanticSearch(@RequestBody SemanticSearchRequest request) {
        metricsService.incrementSearch();
        return searchService.semanticSearch(request);
    }

    @PostMapping("/hybrid")
    @Operation(summary = "Гибридный поиск", description = "Комбинация векторного и полнотекстового поиска с RRF ранжированием")
    public HybridSearchResponse hybridSearch(@RequestBody HybridSearchRequest request) {
        metricsService.incrementSearch();
        return searchService.hybridSearch(request);
    }

    @GetMapping("/filters")
    @Operation(summary = "Получить доступные фильтры", description = "Возвращает все доступные orderId и documentType для фильтрации")
    public FiltersResponse getFilters() {
        return searchService.getFilters();
    }
}
