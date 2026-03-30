package com.rag.entity;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class DocumentChunkEntity {
    private UUID id;
    private UUID documentId;
    private Integer chunkIndex;
    private String content;
    private float[] embedding;
    private LocalDateTime createdAt;
}