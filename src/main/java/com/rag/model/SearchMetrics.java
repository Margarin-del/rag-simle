package com.rag.model;

import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class SearchMetrics {
    private String query;
    private List<String> relevantDocumentIds;
    private List<String> retrievedDocumentIds;
    private Double precision;
    private Double recall;
    private Double f1Score;
    private Double reciprocalRank;
    private Map<String, Double> precisionAtK;
    private Map<String, Double> recallAtK;
    private long executionTimeMs;
    private String searchType;
}