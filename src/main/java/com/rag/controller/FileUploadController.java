package com.rag.controller;

import com.rag.entity.DocumentEntity;
import com.rag.metrics.MetricsService;
import com.rag.model.DocumentEvent;
import com.rag.repository.DocumentRepository;
import com.rag.service.KafkaProducerService;
import com.rag.service.TextExtractionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/upload")
@RequiredArgsConstructor
@Tag(name = "File Upload", description = "Загрузка файлов (PDF, DOCX, TXT)")
public class FileUploadController {

    private final TextExtractionService textExtractionService;
    private final DocumentRepository documentRepository;
    private final KafkaProducerService kafkaProducerService;
    private final MetricsService metricsService;

    @PostMapping
    @Operation(summary = "Загрузить файл", description = "Загружает PDF, DOCX или TXT файл для обработки")
    public ResponseEntity<Map<String, Object>> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "orderId", required = false) String orderId,
            @RequestParam(value = "documentType", required = false) String documentType) {

        try {
            if (file.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "File is empty"));
            }

            log.info("Uploading file: {}, size: {} bytes", file.getOriginalFilename(), file.getSize());

            String extractedText = textExtractionService.extractText(file);

            if (extractedText.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "No text content found in file"));
            }

            UUID id = UUID.randomUUID();
            DocumentEntity document = new DocumentEntity();
            document.setId(id);
            document.setFilename(file.getOriginalFilename());
            document.setOrderId(orderId != null ? orderId : "UNKNOWN");
            document.setDocumentType(documentType != null ? documentType : "GENERAL");
            document.setContent(extractedText);
            document.setStatus("PENDING");
            documentRepository.save(document);

            // Инкремент метрики загрузки документов
            metricsService.incrementDocumentUpload();

            DocumentEvent event = new DocumentEvent();
            event.setDocumentId(id);
            event.setFilename(file.getOriginalFilename());
            event.setOrderId(document.getOrderId());
            event.setDocumentType(document.getDocumentType());
            event.setContent(extractedText);
            event.setEventType("UPLOADED");
            event.setTimestamp(System.currentTimeMillis());

            kafkaProducerService.sendDocumentUpload(event);

            Map<String, Object> response = new HashMap<>();
            response.put("id", id);
            response.put("filename", file.getOriginalFilename());
            response.put("orderId", document.getOrderId());
            response.put("documentType", document.getDocumentType());
            response.put("contentLength", extractedText.length());
            response.put("status", "PENDING");
            response.put("message", "Document queued for processing");

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Failed to upload file", e);
            metricsService.incrementDocumentFailed();
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to process file: " + e.getMessage()));
        }
    }
}