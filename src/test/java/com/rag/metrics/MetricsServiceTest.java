package com.rag.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MetricsServiceTest {

    private MeterRegistry meterRegistry;
    private MetricsService metricsService;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        metricsService = new MetricsService(meterRegistry);
    }

    @Test
    void testMetricsServiceCreation() {
        assertNotNull(metricsService);
    }

    @Test
    void testIncrementDocumentUpload() {
        metricsService.incrementDocumentUpload();
        double count = meterRegistry.find("rag.documents.uploaded").counter().count();
        assertEquals(1.0, count);
    }

    @Test
    void testIncrementDocumentProcessed() {
        metricsService.incrementDocumentProcessed();
        double count = meterRegistry.find("rag.documents.processed").counter().count();
        assertEquals(1.0, count);
    }

    @Test
    void testIncrementSearch() {
        metricsService.incrementSearch();
        double count = meterRegistry.find("rag.search.requests").counter().count();
        assertEquals(1.0, count);
    }

    @Test
    void testIncrementCacheHit() {
        metricsService.incrementCacheHit();
        double count = meterRegistry.find("rag.cache.hits").counter().count();
        assertEquals(1.0, count);
    }

    @Test
    void testIncrementCacheMiss() {
        metricsService.incrementCacheMiss();
        double count = meterRegistry.find("rag.cache.misses").counter().count();
        assertEquals(1.0, count);
    }
}