package com.rag.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class QualityMetricsServiceTest {

    private QualityMetricsService metricsService;

    @BeforeEach
    void setUp() {
        metricsService = new QualityMetricsService(null);
    }

    @Test
    void testPrecisionAtK() {
        List<String> relevant = Arrays.asList("doc1", "doc2", "doc3");
        List<String> retrieved = Arrays.asList("doc1", "doc4", "doc2", "doc5");

        double p3 = metricsService.calculatePrecisionAtK(relevant, retrieved, 3);
        // At K=3: retrieved [doc1, doc4, doc2] -> relevant docs are doc1 and doc2 -> 2 relevant
        // Expected: 2/3 = 0.6667
        assertEquals(2.0 / 3.0, p3, 0.001);

        double p5 = metricsService.calculatePrecisionAtK(relevant, retrieved, 5);
        // At K=5: retrieved [doc1, doc4, doc2, doc5] -> relevant docs are doc1 and doc2 -> 2 relevant
        // Expected: 2/4 = 0.5 (since only 4 documents retrieved)
        assertEquals(2.0 / 4.0, p5, 0.001);
    }

    @Test
    void testRecallAtK() {
        List<String> relevant = Arrays.asList("doc1", "doc2", "doc3");
        List<String> retrieved = Arrays.asList("doc1", "doc4", "doc2");

        double r3 = metricsService.calculateRecallAtK(relevant, retrieved, 3);
        // At K=3: retrieved [doc1, doc4, doc2] -> relevant docs found: doc1, doc2 (2 out of 3)
        // Expected: 2/3 = 0.6667
        assertEquals(2.0 / 3.0, r3, 0.001);

        double r5 = metricsService.calculateRecallAtK(relevant, retrieved, 5);
        // At K=5: same as above since only 3 docs retrieved
        assertEquals(2.0 / 3.0, r5, 0.001);
    }

    @Test
    void testMRR() {
        List<List<String>> relevantDocs = Arrays.asList(
                Arrays.asList("doc1", "doc2"),
                Arrays.asList("doc3"),
                Arrays.asList("doc4")
        );

        List<List<String>> retrievedDocs = Arrays.asList(
                Arrays.asList("doc2", "doc1"),
                Arrays.asList("doc3", "doc5"),
                Arrays.asList("doc5", "doc6")
        );

        // Вычисляем каждое значение отдельно
        double rr1 = metricsService.calculateReciprocalRank(relevantDocs.get(0), retrievedDocs.get(0));
        double rr2 = metricsService.calculateReciprocalRank(relevantDocs.get(1), retrievedDocs.get(1));
        double rr3 = metricsService.calculateReciprocalRank(relevantDocs.get(2), retrievedDocs.get(2));

        System.out.println("RR1: " + rr1); // Должно быть 0.5
        System.out.println("RR2: " + rr2); // Должно быть 1.0
        System.out.println("RR3: " + rr3); // Должно быть 0.0

        double mrr = metricsService.calculateMRR(relevantDocs, retrievedDocs);
        System.out.println("Calculated MRR: " + mrr);

        double expected = (rr1 + rr2 + rr3) / 3.0;
        System.out.println("Expected MRR: " + expected);

        assertEquals(expected, mrr, 0.001);
    }

    @Test
    void testReciprocalRank() {
        List<String> relevant = Arrays.asList("doc1", "doc2");
        List<String> retrieved = Arrays.asList("doc3", "doc1", "doc2");

        double rr = metricsService.calculateReciprocalRank(relevant, retrieved);
        // doc1 at position 2 -> 1/2 = 0.5
        assertEquals(0.5, rr, 0.001);

        // Test when relevant is first
        List<String> retrieved2 = Arrays.asList("doc1", "doc3", "doc2");
        double rr2 = metricsService.calculateReciprocalRank(relevant, retrieved2);
        assertEquals(1.0, rr2, 0.001);

        // Test when no relevant
        List<String> retrieved3 = Arrays.asList("doc4", "doc5");
        double rr3 = metricsService.calculateReciprocalRank(relevant, retrieved3);
        assertEquals(0.0, rr3, 0.001);
    }

    @Test
    void testF1Score() {
        double f1 = metricsService.calculateF1Score(0.8, 0.6);
        // 2 * (0.8 * 0.6) / (0.8 + 0.6) = 2 * 0.48 / 1.4 = 0.96 / 1.4 = 0.6857
        assertEquals(0.6857, f1, 0.001);

        double f1Zero = metricsService.calculateF1Score(0, 0);
        assertEquals(0.0, f1Zero, 0.001);
    }

    @Test
    void testEdgeCases() {
        // Empty retrieved list
        List<String> relevant = Arrays.asList("doc1", "doc2");
        List<String> retrieved = Arrays.asList();

        double p = metricsService.calculatePrecisionAtK(relevant, retrieved, 5);
        assertEquals(0.0, p, 0.001);

        double r = metricsService.calculateRecallAtK(relevant, retrieved, 5);
        assertEquals(0.0, r, 0.001);

        // Empty relevant list
        List<String> retrieved2 = Arrays.asList("doc1", "doc2");
        double p2 = metricsService.calculatePrecisionAtK(List.of(), retrieved2, 2);
        assertEquals(0.0, p2, 0.001);

        double r2 = metricsService.calculateRecallAtK(List.of(), retrieved2, 2);
        assertEquals(0.0, r2, 0.001);
    }
}