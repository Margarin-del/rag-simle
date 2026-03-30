package com.rag.service;

import com.rag.model.SearchMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class QualityMetricsService {

    private final SearchService searchService;

    /**
     * Calculate Precision@K
     */
    public double calculatePrecisionAtK(List<String> relevant, List<String> retrieved, int k) {
        if (retrieved.isEmpty()) return 0.0;
        int kLimit = Math.min(k, retrieved.size());
        Set<String> retrievedSet = new HashSet<>(retrieved.subList(0, kLimit));
        Set<String> relevantSet = new HashSet<>(relevant);

        long relevantRetrieved = retrievedSet.stream()
                .filter(relevantSet::contains)
                .count();

        return (double) relevantRetrieved / kLimit;
    }

    /**
     * Calculate Recall@K
     */
    public double calculateRecallAtK(List<String> relevant, List<String> retrieved, int k) {
        if (relevant.isEmpty()) return 0.0;
        int kLimit = Math.min(k, retrieved.size());
        Set<String> retrievedSet = new HashSet<>(retrieved.subList(0, kLimit));
        Set<String> relevantSet = new HashSet<>(relevant);

        long relevantRetrieved = retrievedSet.stream()
                .filter(relevantSet::contains)
                .count();

        return (double) relevantRetrieved / relevantSet.size();
    }

    /**
     * Calculate F1 Score
     */
    public double calculateF1Score(double precision, double recall) {
        if (precision + recall == 0) return 0.0;
        return 2 * (precision * recall) / (precision + recall);
    }

    /**
     * Calculate Mean Reciprocal Rank (MRR)
     */
    public double calculateMRR(List<List<String>> relevantDocsList, List<List<String>> retrievedDocsList) {
        if (relevantDocsList.isEmpty() || retrievedDocsList.isEmpty()) return 0.0;

        double sumReciprocalRanks = 0.0;
        int queryCount = Math.min(relevantDocsList.size(), retrievedDocsList.size());

        for (int i = 0; i < queryCount; i++) {
            double reciprocalRank = calculateReciprocalRank(relevantDocsList.get(i), retrievedDocsList.get(i));
            sumReciprocalRanks += reciprocalRank;
        }

        return sumReciprocalRanks / queryCount;
    }

    /**
     * Calculate Reciprocal Rank for single query
     */
    public double calculateReciprocalRank(List<String> relevant, List<String> retrieved) {
        Set<String> relevantSet = new HashSet<>(relevant);

        for (int i = 0; i < retrieved.size(); i++) {
            if (relevantSet.contains(retrieved.get(i))) {
                return 1.0 / (i + 1);
            }
        }
        return 0.0;
    }

    /**
     * Evaluate search results against ground truth
     */
    public SearchMetrics evaluateSearch(String query,
                                        List<String> relevantDocumentIds,
                                        List<String> retrievedDocumentIds,
                                        String searchType,
                                        long executionTimeMs) {

        SearchMetrics metrics = new SearchMetrics();
        metrics.setQuery(query);
        metrics.setRelevantDocumentIds(relevantDocumentIds);
        metrics.setRetrievedDocumentIds(retrievedDocumentIds);
        metrics.setSearchType(searchType);
        metrics.setExecutionTimeMs(executionTimeMs);

        // Calculate metrics
        double precision = calculatePrecisionAtK(relevantDocumentIds, retrievedDocumentIds, retrievedDocumentIds.size());
        double recall = calculateRecallAtK(relevantDocumentIds, retrievedDocumentIds, retrievedDocumentIds.size());

        metrics.setPrecision(precision);
        metrics.setRecall(recall);
        metrics.setF1Score(calculateF1Score(precision, recall));
        metrics.setReciprocalRank(calculateReciprocalRank(relevantDocumentIds, retrievedDocumentIds));

        // Calculate metrics at different K values
        Map<String, Double> precisionAtK = new HashMap<>();
        Map<String, Double> recallAtK = new HashMap<>();

        for (int k : Arrays.asList(1, 3, 5, 10)) {
            precisionAtK.put("P@" + k, calculatePrecisionAtK(relevantDocumentIds, retrievedDocumentIds, k));
            recallAtK.put("R@" + k, calculateRecallAtK(relevantDocumentIds, retrievedDocumentIds, k));
        }

        metrics.setPrecisionAtK(precisionAtK);
        metrics.setRecallAtK(recallAtK);

        return metrics;
    }

    /**
     * Run batch evaluation on test queries
     */
    public Map<String, Object> runBatchEvaluation(Map<String, List<String>> testQueries,
                                                  Map<String, List<String>> groundTruth,
                                                  String searchType) {

        List<SearchMetrics> allMetrics = new ArrayList<>();
        double totalPrecision = 0.0;
        double totalRecall = 0.0;
        double totalF1 = 0.0;
        double totalMRR = 0.0;

        for (Map.Entry<String, List<String>> entry : testQueries.entrySet()) {
            String query = entry.getKey();
            List<String> expectedDocs = groundTruth.getOrDefault(query, Collections.emptyList());
            List<String> retrievedDocs = searchService.search(query, 10, searchType);

            long startTime = System.currentTimeMillis();
            // Simulate search to measure time
            long endTime = System.currentTimeMillis();

            SearchMetrics metrics = evaluateSearch(query, expectedDocs, retrievedDocs, searchType, endTime - startTime);
            allMetrics.add(metrics);

            totalPrecision += metrics.getPrecision();
            totalRecall += metrics.getRecall();
            totalF1 += metrics.getF1Score();
            totalMRR += metrics.getReciprocalRank();
        }

        int queryCount = allMetrics.size();
        Map<String, Object> summary = new HashMap<>();
        summary.put("totalQueries", queryCount);
        summary.put("averagePrecision", totalPrecision / queryCount);
        summary.put("averageRecall", totalRecall / queryCount);
        summary.put("averageF1", totalF1 / queryCount);
        summary.put("meanReciprocalRank", totalMRR / queryCount);
        summary.put("searchType", searchType);
        summary.put("details", allMetrics);

        return summary;
    }
}