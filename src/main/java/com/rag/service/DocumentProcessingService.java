package com.rag.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentProcessingService {

    private final EmbeddingService embeddingService;
    private final JdbcTemplate jdbcTemplate;

    private static final int CHUNK_SIZE = 500;
    private static final int OVERLAP = 50;

    public void processDocument(UUID documentId, String content) {
        log.info("Processing document: {}", documentId);
        log.info("Content length: {}", content == null ? 0 : content.length());

        if (content == null || content.trim().isEmpty()) {
            updateStatus(documentId, "FAILED");
            return;
        }

        try {
            // Разбиваем на чанки
            List<String> chunks = splitIntoChunks(content);
            log.info("Split into {} chunks", chunks.size());

            // Сохраняем каждый чанк
            for (int i = 0; i < chunks.size(); i++) {
                log.info("Processing chunk {}/{}", i + 1, chunks.size());

                float[] embedding = embeddingService.generateEmbedding(chunks.get(i));
                String vectorStr = embeddingToString(embedding);

                UUID chunkId = UUID.randomUUID();
                String sql = """
                    INSERT INTO document_chunks (id, document_id, chunk_index, content, embedding, created_at)
                    VALUES (?, ?, ?, ?, CAST(? AS vector), ?)
                """;

                jdbcTemplate.update(sql,
                        chunkId,
                        documentId,
                        i,
                        chunks.get(i),
                        vectorStr,
                        LocalDateTime.now()
                );
            }

            updateStatus(documentId, "PROCESSED");
            log.info("Document {} processed successfully with {} chunks", documentId, chunks.size());

        } catch (Exception e) {
            log.error("Failed to process document: {}", documentId, e);
            updateStatus(documentId, "FAILED");
        }
    }

    private List<String> splitIntoChunks(String text) {
        List<String> chunks = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return chunks;
        }

        int textLength = text.length();
        int start = 0;

        while (start < textLength) {
            // Конец чанка
            int end = Math.min(start + CHUNK_SIZE, textLength);

            // Если не конец текста, ищем хорошее место для разрыва
            if (end < textLength) {
                int breakPoint = end;
                int searchStart = Math.max(start, end - 100);
                for (int i = end - 1; i >= searchStart; i--) {
                    char c = text.charAt(i);
                    if (c == '.' || c == '\n' || c == ' ') {
                        breakPoint = i + 1;
                        break;
                    }
                }
                end = breakPoint;
                if (end <= start) {
                    end = Math.min(start + CHUNK_SIZE, textLength);
                }
            }

            // Добавляем чанк
            String chunk = text.substring(start, end);
            if (!chunk.trim().isEmpty()) {
                chunks.add(chunk.trim());
            }

            // Сдвигаем начало для следующего чанка с перекрытием
            if (end >= textLength) {
                break;
            }
            start = end - OVERLAP;
            if (start < 0) start = 0;
            if (start >= end) start = end;
        }

        return chunks;
    }

    private void updateStatus(UUID documentId, String status) {
        String sql = "UPDATE documents SET status = ? WHERE id = ?";
        jdbcTemplate.update(sql, status, documentId);
    }

    private String embeddingToString(float[] embedding) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < embedding.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(embedding[i]);
        }
        sb.append("]");
        return sb.toString();
    }
}