package com.rag.consumer;

import com.rag.config.KafkaConfig;
import com.rag.metrics.MetricsService;
import com.rag.model.DocumentEvent;
import com.rag.service.DocumentProcessingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DocumentConsumer {

    private final DocumentProcessingService processingService;
    private final MetricsService metricsService;

    @KafkaListener(topics = KafkaConfig.DOCUMENT_UPLOADS_TOPIC,
            containerFactory = "kafkaListenerContainerFactory")
    public void consume(DocumentEvent event) {
        log.info("Received from Kafka: {}", event.getDocumentId());
        try {
            processingService.processDocument(event.getDocumentId(), event.getContent());
            metricsService.incrementDocumentProcessed();
        } catch (Exception e) {
            log.error("Failed to process document: {}", event.getDocumentId(), e);
            metricsService.incrementDocumentFailed();
        }
    }
}