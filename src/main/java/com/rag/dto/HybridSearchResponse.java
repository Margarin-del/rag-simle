package com.rag.dto;

import java.util.List;
import java.util.UUID;

public record HybridSearchResponse(
        String query,
        String type,
        Weights weights,
        SearchFilters filters,
        List<HybridChunkResult> results,
        int totalResults
) {
    public record HybridChunkResult(
            UUID chunkId,
            UUID documentId,
            int chunkIndex,
            String content,
            String filename,
            String orderId,
            String documentType,
            double semanticScore,
            double textScore,
            double rrfScore
    ) {
    }
}

