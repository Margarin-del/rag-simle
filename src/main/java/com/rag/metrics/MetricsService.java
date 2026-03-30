package com.rag.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;

@Service
public class MetricsService {

    private final Counter documentUploadCounter;
    private final Counter documentProcessedCounter;
    private final Counter documentFailedCounter;
    private final Counter searchCounter;
    private final Counter cacheHitCounter;
    private final Counter cacheMissCounter;

    public MetricsService(MeterRegistry meterRegistry) {
        this.documentUploadCounter = Counter.builder("rag.documents.uploaded")
                .description("Total number of documents uploaded")
                .register(meterRegistry);

        this.documentProcessedCounter = Counter.builder("rag.documents.processed")
                .description("Total number of documents processed")
                .register(meterRegistry);

        this.documentFailedCounter = Counter.builder("rag.documents.failed")
                .description("Total number of documents failed")
                .register(meterRegistry);

        this.searchCounter = Counter.builder("rag.search.requests")
                .description("Total number of search requests")
                .register(meterRegistry);

        this.cacheHitCounter = Counter.builder("rag.cache.hits")
                .description("Number of cache hits")
                .register(meterRegistry);

        this.cacheMissCounter = Counter.builder("rag.cache.misses")
                .description("Number of cache misses")
                .register(meterRegistry);
    }

    public void incrementDocumentUpload() {
        documentUploadCounter.increment();
    }

    public void incrementDocumentProcessed() {
        documentProcessedCounter.increment();
    }

    public void incrementDocumentFailed() {
        documentFailedCounter.increment();
    }

    public void incrementSearch() {
        searchCounter.increment();
    }

    public void incrementCacheHit() {
        cacheHitCounter.increment();
    }

    public void incrementCacheMiss() {
        cacheMissCounter.increment();
    }
}