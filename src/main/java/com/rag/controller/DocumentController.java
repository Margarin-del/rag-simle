package com.rag.controller;

import com.rag.entity.DocumentEntity;
import com.rag.model.DocumentEvent;
import com.rag.repository.DocumentRepository;
import com.rag.service.KafkaProducerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
@Tag(name = "Documents", description = "Управление документами")
public class DocumentController {

    private final DocumentRepository documentRepository;
    private final KafkaProducerService kafkaProducerService;
    private final JdbcTemplate jdbcTemplate;

    @PostMapping
    @Operation(summary = "Создать документ", description = "Создает новый документ и отправляет в очередь на обработку")
    public Map<String, Object> create(@RequestBody DocumentEntity document) {
        try {
            UUID id = UUID.randomUUID();
            document.setId(id);
            document.setStatus("PENDING");
            documentRepository.save(document);

            DocumentEvent event = new DocumentEvent();
            event.setDocumentId(id);
            event.setFilename(document.getFilename());
            event.setOrderId(document.getOrderId());
            event.setDocumentType(document.getDocumentType());
            event.setContent(document.getContent());
            event.setEventType("UPLOADED");
            event.setTimestamp(System.currentTimeMillis());

            kafkaProducerService.sendDocumentUpload(event);

            log.info("Document queued: {}", id);
            return Map.of("id", id, "status", "PENDING", "message", "Document queued for processing");

        } catch (Exception e) {
            log.error("Failed to create document", e);
            return Map.of("status", "FAILED", "error", e.getMessage());
        }
    }

    @GetMapping
    @Operation(summary = "Получить все документы", description = "Возвращает список всех документов")
    public List<DocumentEntity> getAll() {
        return documentRepository.findAll();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Получить документ по ID", description = "Возвращает документ по его идентификатору")
    public DocumentEntity getById(@PathVariable UUID id) {
        return documentRepository.findById(id).orElse(null);
    }

    @GetMapping("/{id}/chunks")
    @Operation(summary = "Получить чанки документа", description = "Возвращает все чанки указанного документа")
    public List<Map<String, Object>> getChunks(@PathVariable UUID id) {
        String sql = """
            SELECT chunk_index, content, created_at 
            FROM document_chunks 
            WHERE document_id = ? 
            ORDER BY chunk_index
        """;

        return jdbcTemplate.query(sql, (rs, rowNum) -> Map.of(
                "chunkIndex", rs.getInt("chunk_index"),
                "content", rs.getString("content"),
                "createdAt", rs.getTimestamp("created_at")
        ), id);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Удалить документ", description = "Удаляет документ и все его чанки")
    public void delete(@PathVariable UUID id) {
        documentRepository.deleteById(id);
        jdbcTemplate.update("DELETE FROM document_chunks WHERE document_id = ?", id);
    }
}