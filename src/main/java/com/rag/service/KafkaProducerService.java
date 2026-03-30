package com.rag.service;

import com.rag.config.KafkaConfig;
import com.rag.model.DocumentEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class KafkaProducerService {

    private final KafkaTemplate<String, DocumentEvent> kafkaTemplate;

    public void sendDocumentUpload(DocumentEvent event) {
        log.info("Sending to Kafka: {}", event.getDocumentId());
        kafkaTemplate.send(KafkaConfig.DOCUMENT_UPLOADS_TOPIC,
                event.getDocumentId().toString(),
                event);
    }
}