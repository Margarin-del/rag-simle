-- Minimal schema for the app runtime.
-- Uses pgvector for embeddings (dimension matches nomic-embed-text = 768).

CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS documents (
    id UUID PRIMARY KEY,
    filename TEXT,
    order_id TEXT,
    document_type TEXT,
    content TEXT,
    created_at TIMESTAMP,
    status TEXT
);

CREATE TABLE IF NOT EXISTS document_chunks (
    id UUID PRIMARY KEY,
    document_id UUID NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
    chunk_index INT NOT NULL,
    content TEXT,
    embedding vector(768),
    created_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_document_chunks_document_id
    ON document_chunks(document_id);

