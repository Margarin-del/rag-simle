package com.rag.dto;

import java.util.List;
import java.util.UUID;

public record SemanticSearchResponse(
        String query,
        SearchFilters filters,
        List<SemanticDocumentResult> results,
        int totalChunks,
        int uniqueDocuments
) {
    public record SemanticDocumentResult(
            UUID documentId,
            String filename,
            String orderId,
            String documentType,
            double bestSimilarity,
            List<SemanticChunkResult> chunks
    ) {
    }

    public record SemanticChunkResult(
            int chunkIndex,
            String content,
            double similarity
    ) {
    }
}

