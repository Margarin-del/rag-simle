package com.rag.dto;

public record SemanticSearchRequest(
        String query,
        String orderId,
        String documentType,
        Integer limit
) {
}

