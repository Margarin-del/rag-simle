package com.rag.dto;

public record HybridSearchRequest(
        String query,
        String orderId,
        String documentType,
        Integer limit,
        Double semanticWeight,
        Double textWeight
) {
}

