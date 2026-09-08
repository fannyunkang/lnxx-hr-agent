package com.lnxx.hr.ingestion;

import java.time.LocalDateTime;

public record IngestionJob(String jobId, String documentId, int version, Status status,
                           int chunkCount, String errorCode, String errorMessage,
                           LocalDateTime createdAt, LocalDateTime updatedAt) {
    public enum Status { PENDING, PARSING, EMBEDDING, INDEXED, FAILED, DELETED }
}
