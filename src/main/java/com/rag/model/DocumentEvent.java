package com.rag.model;

import java.util.UUID;

public class DocumentEvent {
    private UUID documentId;
    private String filename;
    private String orderId;
    private String documentType;
    private String content;
    private String eventType;
    private Long timestamp;

    public DocumentEvent() {}

    public DocumentEvent(UUID documentId, String filename, String orderId,
                         String documentType, String content, String eventType, Long timestamp) {
        this.documentId = documentId;
        this.filename = filename;
        this.orderId = orderId;
        this.documentType = documentType;
        this.content = content;
        this.eventType = eventType;
        this.timestamp = timestamp;
    }

    // Геттеры и сеттеры
    public UUID getDocumentId() { return documentId; }
    public void setDocumentId(UUID documentId) { this.documentId = documentId; }

    public String getFilename() { return filename; }
    public void setFilename(String filename) { this.filename = filename; }

    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }

    public String getDocumentType() { return documentType; }
    public void setDocumentType(String documentType) { this.documentType = documentType; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }

    public Long getTimestamp() { return timestamp; }
    public void setTimestamp(Long timestamp) { this.timestamp = timestamp; }
}