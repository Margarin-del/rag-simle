package com.rag.dto;

import java.util.List;

public record FiltersResponse(
        List<String> orderIds,
        List<String> documentTypes
) {
}

