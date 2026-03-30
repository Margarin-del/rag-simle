package com.rag.service;

import com.rag.controller.SearchController;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SearchService {

    private final SearchController searchController;
    private final ObjectMapper objectMapper;

    public List<String> search(String query, int limit, String searchType) {
        try {
            Map<String, Object> response;

            if ("semantic".equalsIgnoreCase(searchType)) {
                SearchController.SearchRequest request = new SearchController.SearchRequest();
                request.setQuery(query);
                request.setLimit(limit);
                response = searchController.semanticSearch(request);
            } else {
                SearchController.HybridSearchRequest request = new SearchController.HybridSearchRequest();
                request.setQuery(query);
                request.setLimit(limit);
                response = searchController.hybridSearch(request);
            }

            // Extract document IDs from response
            List<Map<String, Object>> results = (List<Map<String, Object>>) response.get("results");
            if (results == null || results.isEmpty()) {
                return List.of();
            }

            return results.stream()
                    .map(r -> r.get("documentId").toString())
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("Search failed for query: {}", query, e);
            return List.of();
        }
    }
}